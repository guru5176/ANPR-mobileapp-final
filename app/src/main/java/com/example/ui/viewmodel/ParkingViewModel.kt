package com.example.ui.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.CreditEntity
import com.example.data.local.PassEntity
import com.example.data.local.PassType
import com.example.data.local.PaymentStatus
import com.example.data.local.PlateInspectionResult
import com.example.data.local.RateConfig
import com.example.data.local.ShiftAuditSummary
import com.example.data.local.SyncQueueEntity
import com.example.data.local.SyncStatus
import com.example.data.local.VehicleLogEntity
import com.example.data.repository.BillingRepository
import com.example.ui.i18n.AppLanguage
import com.example.util.NetworkSyncMonitor
import com.example.vision.AnprDetectionResult
import com.example.vision.AnprFrameAnalyzer
import com.example.vision.PhotoWatermarkUtil
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ThinkingLevel
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.ai.type.thinkingConfig
import com.google.firebase.firestore.FirebaseFirestoreException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

enum class AppRole {
    WORKER,
    OWNER
}

enum class ScanMode {
    ENTRY,
    EXIT
}

sealed interface AiTaskState {
    data object Idle : AiTaskState
    data object Loading : AiTaskState
    data class Streaming(val partialText: String) : AiTaskState
    data class Success(val resultText: String) : AiTaskState
    data class Error(val message: String) : AiTaskState
}

data class VisionPlateAnalysis(
    val plateNumber: String,
    val vehicleDescription: String,
    val plateCondition: String,
    val rtoRegion: String
)

class ParkingViewModel(
    private val repository: BillingRepository,
    private val currentUserId: String,
    applicationContext: Context? = null
) : ViewModel() {

    private companion object {
        const val TAG = "ParkingViewModel"
        const val STOP_TIMEOUT_MS = 5000L
        const val AUTO_RETRY_INTERVAL_MS = 15_000L
        const val PREFS_NAME = "motovault_prefs"
        const val KEY_LANGUAGE = "ui_language"
    }

    private val appContext: Context? = applicationContext?.applicationContext

    // --- 1. Role, Language (English / தமிழ்) & PIN Access Control ---
    private val _appLanguage = MutableStateFlow(loadSavedLanguage())
    val appLanguage: StateFlow<AppLanguage> = _appLanguage.asStateFlow()

    private val _currentRole = MutableStateFlow(AppRole.WORKER)
    val currentRole: StateFlow<AppRole> = _currentRole.asStateFlow()

    private val _workerName = MutableStateFlow("Shift Worker")
    val workerName: StateFlow<String> = _workerName.asStateFlow()

    private fun loadSavedLanguage(): AppLanguage {
        val code = appContext
            ?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.code)
        return if (code == AppLanguage.TAMIL.code) AppLanguage.TAMIL else AppLanguage.ENGLISH
    }

    // --- 2. Live Scanner, Mode (Entry/Exit), Torch & Luminosity State ---
    private val _scanMode = MutableStateFlow(ScanMode.ENTRY)
    val scanMode: StateFlow<ScanMode> = _scanMode.asStateFlow()

    private val _plateInput = MutableStateFlow("")
    val plateInput: StateFlow<String> = _plateInput.asStateFlow()

    private val _plateInspection = MutableStateFlow(
        PlateInspectionResult(plateNumber = "", isValidFormat = false)
    )
    val plateInspection: StateFlow<PlateInspectionResult> = _plateInspection.asStateFlow()

    private val _currentLuminosity = MutableStateFlow(120.0)
    val currentLuminosity: StateFlow<Double> = _currentLuminosity.asStateFlow()

    private val _isTorchOn = MutableStateFlow(false)
    val isTorchOn: StateFlow<Boolean> = _isTorchOn.asStateFlow()

    private val _autoTorchEnabled = MutableStateFlow(true)
    val autoTorchEnabled: StateFlow<Boolean> = _autoTorchEnabled.asStateFlow()

    private val _lastCapturedBitmap = MutableStateFlow<Bitmap?>(null)
    val lastCapturedBitmap: StateFlow<Bitmap?> = _lastCapturedBitmap.asStateFlow()

    private val _lastDetectionResult = MutableStateFlow<AnprDetectionResult?>(null)
    val lastDetectionResult: StateFlow<AnprDetectionResult?> = _lastDetectionResult.asStateFlow()

    private val _statusBannerMessage = MutableStateFlow<String?>(null)
    val statusBannerMessage: StateFlow<String?> = _statusBannerMessage.asStateFlow()

    private val _activeExitDialogLog = MutableStateFlow<VehicleLogEntity?>(null)
    val activeExitDialogLog: StateFlow<VehicleLogEntity?> = _activeExitDialogLog.asStateFlow()

    // --- 3. Offline Sync Queue & Connectivity State ---
    private val _isOnline = MutableStateFlow(
        appContext?.let { NetworkSyncMonitor.isCurrentlyConnected(it) } ?: true
    )
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _isSyncingQueue = MutableStateFlow(false)
    val isSyncingQueue: StateFlow<Boolean> = _isSyncingQueue.asStateFlow()

    val pendingSyncQueue: StateFlow<List<SyncQueueEntity>> = repository.observePendingSyncQueue()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val pendingSyncCount: StateFlow<Int> = repository.observePendingSyncCount()
        .catch { emit(0) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    // --- 4. Reactive Database & Cloud Streams ---
    val vehicleLogs: StateFlow<List<VehicleLogEntity>> = repository.observeVehicleLogs()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing vehicle logs", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val passes: StateFlow<List<PassEntity>> = repository.observePasses()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing passes", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val credits: StateFlow<List<CreditEntity>> = repository.observeCredits()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Error observing credits", firestoreError ?: error)
            emit(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    val rateConfig: StateFlow<RateConfig> = repository.observeRateConfig()
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            emit(RateConfig(userId = currentUserId))
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            RateConfig(userId = currentUserId)
        )

    val shiftAuditSummary: StateFlow<ShiftAuditSummary> = repository.observeShiftAuditSummary()
        .catch { emit(ShiftAuditSummary()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), ShiftAuditSummary())

    // --- 5. Firebase AI Logic (Gemini Models) ---

    // A. Multimodal Image Understanding Model (gemini-4-argon) with Structured JSON Schema
    private val visionPlateSchema = Schema.obj(
        properties = mapOf(
            "plateNumber" to Schema.string(description = "Normalized Indian vehicle license plate number without spaces, e.g. TN39AB1234"),
            "vehicleDescription" to Schema.string(description = "Two-wheeler make, type, and color visible in the image"),
            "plateCondition" to Schema.string(description = "Condition of the plate, e.g. Standard HSRP, Rusted, Hand-painted, Angled"),
            "rtoRegion" to Schema.string(description = "Identified Indian State and RTO district name")
        )
    )

    private val geminiVisionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = visionPlateSchema
            }
        )
    }

    // B. Low-Latency Fast Helper Model (gemini-3.1-flash-lite)
    private val geminiFastLiteModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.1-flash-lite"
        )
    }

    // C. High-Thinking Complex Audit Model (gemini-4-argon with ThinkingLevel.HIGH, no maxOutputTokens)
    private val geminiHighThinkingAuditModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-4-argon",
            generationConfig = generationConfig {
                thinkingConfig = thinkingConfig {
                    thinkingLevel = ThinkingLevel.HIGH
                }
            }
        )
    }

    // D. General Task Model (gemini-3.8-flash) for Dispute Receipt Summaries
    private val geminiGeneralFlashModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash"
        )
    }

    private val _visionAnalysisState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val visionAnalysisState: StateFlow<AiTaskState> = _visionAnalysisState.asStateFlow()

    private val _lastVisionPlateAnalysis = MutableStateFlow<VisionPlateAnalysis?>(null)
    val lastVisionPlateAnalysis: StateFlow<VisionPlateAnalysis?> = _lastVisionPlateAnalysis.asStateFlow()

    private val _fastRtoHintState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val fastRtoHintState: StateFlow<AiTaskState> = _fastRtoHintState.asStateFlow()

    private val _ownerDeepAuditState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val ownerDeepAuditState: StateFlow<AiTaskState> = _ownerDeepAuditState.asStateFlow()

    private val _disputeSummaryState = MutableStateFlow<AiTaskState>(AiTaskState.Idle)
    val disputeSummaryState: StateFlow<AiTaskState> = _disputeSummaryState.asStateFlow()

    init {
        repository.startRealtimeSync(viewModelScope) { syncErr ->
            Log.w(TAG, "Realtime sync warning: $syncErr")
        }

        // Observe cellular / Wi-Fi network connectivity and automatically flush the Room sync_queue when online
        val ctx = appContext
        if (ctx != null) {
            viewModelScope.launch {
                var wasOnline = _isOnline.value
                NetworkSyncMonitor.observeNetworkConnectivity(ctx)
                    .catch { emit(true) }
                    .collect { connected ->
                        _isOnline.value = connected
                        if (connected && (!wasOnline || pendingSyncCount.value > 0)) {
                            syncOfflineQueueNow(silent = true)
                        }
                        wasOnline = connected
                    }
            }

            // Periodic background retry while online if any items remain in the sync queue
            viewModelScope.launch {
                while (true) {
                    delay(AUTO_RETRY_INTERVAL_MS)
                    if (_isOnline.value && pendingSyncCount.value > 0 && !_isSyncingQueue.value) {
                        syncOfflineQueueNow(silent = true)
                    }
                }
            }
        }
    }

    /**
     * Flushes all pending Room `sync_queue` records to Firebase Firestore.
     */
    fun syncOfflineQueueNow(silent: Boolean = false) {
        if (_isSyncingQueue.value) return
        viewModelScope.launch {
            _isSyncingQueue.value = true
            try {
                val result = repository.flushSyncQueue()
                if (result.syncedCount > 0) {
                    _statusBannerMessage.value =
                        "Cloud Sync Complete: ${result.syncedCount} queued record(s) uploaded to Firestore."
                } else if (!silent && result.remainingCount > 0) {
                    _statusBannerMessage.value =
                        "Offline Queue: ${result.remainingCount} record(s) waiting for stable network connection."
                } else if (!silent && result.remainingCount == 0) {
                    _statusBannerMessage.value = "All vehicle logs are synced with Firestore."
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!silent) {
                    _statusBannerMessage.value = "Sync retry deferred: ${e.localizedMessage ?: "No connection"}"
                }
            } finally {
                _isSyncingQueue.value = false
            }
        }
    }

    // --- Role, Language & PIN Actions ---

    fun toggleLanguage() {
        val next = if (_appLanguage.value == AppLanguage.ENGLISH) {
            AppLanguage.TAMIL
        } else {
            AppLanguage.ENGLISH
        }
        setLanguage(next)
    }

    fun setLanguage(language: AppLanguage) {
        _appLanguage.value = language
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putString(KEY_LANGUAGE, language.code)
            ?.apply()
        _statusBannerMessage.value = if (language == AppLanguage.TAMIL) {
            "தமிழ் மொழி தேர்ந்தெடுக்கப்பட்டது (Tamil UI Enabled)"
        } else {
            "Switched to English UI"
        }
    }

    fun unlockOwnerRoleWithPin(enteredPin: String): Boolean {
        val expectedPin = rateConfig.value.ownerPin.ifBlank { "1234" }
        return if (enteredPin.trim() == expectedPin) {
            _currentRole.value = AppRole.OWNER
            _statusBannerMessage.value = if (_appLanguage.value == AppLanguage.TAMIL) {
                "உரிமையாளர் (Owner) பகுதி திறக்கப்பட்டது."
            } else {
                "Owner Audit Mode unlocked."
            }
            true
        } else {
            false
        }
    }

    fun switchToWorkerRole() {
        _currentRole.value = AppRole.WORKER
        _statusBannerMessage.value = if (_appLanguage.value == AppLanguage.TAMIL) {
            "பணியாளர் (Worker) முறைக்கு மாற்றப்பட்டது."
        } else {
            "Switched to Worker Mode."
        }
    }

    fun updateWorkerName(name: String) {
        _workerName.value = name.ifBlank { "Shift Worker" }
    }

    fun clearBannerMessage() {
        _statusBannerMessage.value = null
    }

    // --- Scanner, Mode Toggle, Luminosity & Torch Actions ---

    fun setScanMode(mode: ScanMode) {
        _scanMode.value = mode
        refreshPlateInspection(_plateInput.value)
    }

    fun toggleScanMode() {
        _scanMode.value = if (_scanMode.value == ScanMode.ENTRY) ScanMode.EXIT else ScanMode.ENTRY
        refreshPlateInspection(_plateInput.value)
    }

    fun onPlateInputChanged(rawInput: String) {
        val cleaned = rawInput.uppercase().replace(Regex("[^A-Z0-9]"), "").take(12)
        _plateInput.value = cleaned
        refreshPlateInspection(cleaned)
    }

    fun appendRtoPrefix(prefix: String) {
        val current = _plateInput.value
        val updated = if (current.length >= 4 && current.startsWith(prefix.take(2))) {
            prefix + current.drop(4)
        } else {
            prefix
        }
        onPlateInputChanged(updated)
    }

    fun onLiveAnprPlateDetected(result: AnprDetectionResult) {
        _lastDetectionResult.value = result
        if (result.frameSnapshot != null) {
            _lastCapturedBitmap.value = result.frameSnapshot
        }
        if (_plateInput.value != result.normalizedPlate) {
            _plateInput.value = result.normalizedPlate
            refreshPlateInspection(result.normalizedPlate)
        }
    }

    fun onLuminosityUpdated(averageLuma: Double, shouldAutoEnableTorch: Boolean) {
        _currentLuminosity.value = averageLuma
        if (_autoTorchEnabled.value && shouldAutoEnableTorch && !_isTorchOn.value) {
            _isTorchOn.value = true
        }
    }

    fun toggleTorchManual() {
        _isTorchOn.value = !_isTorchOn.value
    }

    fun setAutoTorchEnabled(enabled: Boolean) {
        _autoTorchEnabled.value = enabled
    }

    private fun refreshPlateInspection(plate: String) {
        viewModelScope.launch {
            val isExitMode = _scanMode.value == ScanMode.EXIT
            val inspection = repository.inspectPlate(
                rawPlate = plate,
                searchCloudIfMissing = isExitMode && _isOnline.value
            )
            _plateInspection.value = inspection
        }
    }

    // --- Mode-Switched Entry & Exit Processing Logic ---

    /**
     * Unified scanner processor controlled by the 'Entry / Exit' toggle state:
     * - ENTRY mode: Creates a new vehicle entry record with mandatory timestamped photo proof.
     * - EXIT mode: Searches for an active INSIDE record for the scanned plate to calculate the fee.
     */
    fun processPlateForCurrentMode(context: Context) {
        when (_scanMode.value) {
            ScanMode.ENTRY -> logEntryWithMandatoryPhoto(context)
            ScanMode.EXIT -> searchActiveRecordAndCalculateFee(_plateInput.value)
        }
    }

    /**
     * EXIT mode processor: Searches local Room + cloud Firestore for an active record
     * matching the scanned plate, calculates the 24h rolling fee, and opens the exit billing sheet.
     */
    fun searchActiveRecordAndCalculateFee(rawPlate: String = _plateInput.value) {
        val normalized = BillingRepository.normalizePlateNumber(rawPlate)
        if (normalized.length < 4) {
            _statusBannerMessage.value = "Scan or enter a valid license plate to search active records."
            return
        }

        viewModelScope.launch {
            val inspection = repository.inspectPlate(
                rawPlate = normalized,
                searchCloudIfMissing = _isOnline.value
            )
            _plateInspection.value = inspection

            val activeRecord = inspection.activeInsideLog
            val bill = inspection.exitBillPreview
            if (activeRecord != null && bill != null) {
                _activeExitDialogLog.value = activeRecord
                _statusBannerMessage.value =
                    "Active record found for $normalized • Fee calculated: ₹${bill.totalAmountDue.toInt()} (${bill.daysCharged}d)"
            } else {
                _statusBannerMessage.value =
                    "No active 'Inside' record found for $normalized. Switch to Entry mode to create a new record."
            }
        }
    }

    fun logEntryWithMandatoryPhoto(
        context: Context,
        capturedFrame: Bitmap? = _lastCapturedBitmap.value,
        customNotes: String? = null
    ) {
        val rawPlate = _plateInput.value
        val normalized = BillingRepository.normalizePlateNumber(rawPlate)
        if (normalized.length < 4) {
            _statusBannerMessage.value = "Enter or scan a valid vehicle license plate first."
            return
        }

        viewModelScope.launch {
            val currentInspection = repository.inspectPlate(normalized)
            if (currentInspection.activeInsideLog != null) {
                _activeExitDialogLog.value = currentInspection.activeInsideLog
                _statusBannerMessage.value = "$normalized is already INSIDE! Opening Exit Billing Sheet."
                return@launch
            }

            val now = System.currentTimeMillis()
            val watermarkedPath = PhotoWatermarkUtil.saveWatermarkedSnapshot(
                context = context,
                sourceBitmap = capturedFrame,
                plateNumber = normalized,
                timestampMillis = now,
                workerName = _workerName.value
            )

            val noteText = customNotes ?: _lastVisionPlateAnalysis.value
                ?.takeIf { it.plateNumber == normalized }
                ?.let { "${it.vehicleDescription} (${it.plateCondition})" }

            val result = repository.logVehicleEntry(
                plateNumber = normalized,
                photoPath = watermarkedPath,
                workerName = _workerName.value,
                notes = noteText,
                entryTimestamp = now
            )

            result.onSuccess { created ->
                _statusBannerMessage.value = if (created.syncStatus == SyncStatus.SYNCED) {
                    "ENTRY LOGGED & SYNCED: ${created.plateNumber} with timestamped photo proof."
                } else {
                    "ENTRY SAVED OFFLINE: ${created.plateNumber} queued in Room for automatic cloud upload."
                }
                refreshPlateInspection(normalized)
            }.onFailure { err ->
                _statusBannerMessage.value = "Entry failed: ${err.localizedMessage}"
            }
        }
    }

    fun openExitSheetForVehicle(log: VehicleLogEntity) {
        _activeExitDialogLog.value = log
        _plateInput.value = log.plateNumber
        refreshPlateInspection(log.plateNumber)
    }

    fun dismissExitSheet() {
        _activeExitDialogLog.value = null
    }

    fun completeVehicleExit(
        logId: String,
        paymentChoice: String,
        notes: String? = null
    ) {
        viewModelScope.launch {
            val result = repository.processVehicleExit(
                logId = logId,
                paymentChoice = paymentChoice,
                exitTimestamp = System.currentTimeMillis(),
                notes = notes
            )
            result.onSuccess { exited ->
                _activeExitDialogLog.value = null
                val modeLabel = when (exited.paymentStatus) {
                    PaymentStatus.PAID_CASH -> "PAID CASH (₹${exited.amountCollected.toInt()} added to Cash Drawer)"
                    PaymentStatus.PAID_UPI -> "PAID UPI (₹${exited.amountCollected.toInt()} logged digitally)"
                    PaymentStatus.UNPAID_CREDIT -> "PAY TOMORROW CREDIT (₹${exited.totalAmountDue.toInt()} linked to ${exited.plateNumber})"
                    PaymentStatus.PASS_COVERED -> "ACTIVE PASS COVERED (₹0 Daily Fee)"
                    else -> exited.paymentStatus
                }
                val syncSuffix = if (exited.syncStatus == SyncStatus.SYNCED) {
                    "Synced"
                } else {
                    "Queued in Offline Sync"
                }
                _statusBannerMessage.value = "EXIT COMPLETED ($syncSuffix) • ${exited.plateNumber}: $modeLabel"
                refreshPlateInspection(exited.plateNumber)
            }.onFailure { err ->
                _statusBannerMessage.value = "Exit failed: ${err.localizedMessage}"
            }
        }
    }

    // --- Pass & Credit Management Actions ---

    fun issueOrRenewPass(
        plateNumber: String,
        passType: String,
        paymentMode: String
    ) {
        viewModelScope.launch {
            val result = repository.registerOrRenewPass(
                plateNumber = plateNumber,
                passType = passType,
                paymentMode = paymentMode
            )
            result.onSuccess { pass ->
                val tierLabel = if (pass.passType == PassType.MONTHLY_PASS) "Monthly Pass (30d)" else "Weekly Pass (7d)"
                _statusBannerMessage.value = "$tierLabel activated for ${pass.plateNumber} (${pass.paymentMode})."
                refreshPlateInspection(pass.plateNumber)
            }.onFailure { err ->
                _statusBannerMessage.value = "Pass registration failed: ${err.localizedMessage}"
            }
        }
    }

    fun settlePendingCredit(
        credit: CreditEntity,
        paymentMode: String
    ) {
        viewModelScope.launch {
            val result = repository.upsertCreditBalance(
                plateNumber = credit.plateNumber,
                pendingAmount = 0.0,
                notes = "Settled ₹${credit.pendingAmount.toInt()} via $paymentMode",
                isSettled = true
            )
            result.onSuccess {
                _statusBannerMessage.value = "Credit of ₹${credit.pendingAmount.toInt()} for ${credit.plateNumber} settled via $paymentMode."
                refreshPlateInspection(_plateInput.value)
            }.onFailure { err ->
                _statusBannerMessage.value = "Failed to settle credit: ${err.localizedMessage}"
            }
        }
    }

    fun saveOwnerRateConfig(
        dailyRate: Double,
        weeklyPassRate: Double,
        monthlyPassRate: Double,
        ownerPin: String,
        lowLightThreshold: Double
    ) {
        if (_currentRole.value != AppRole.OWNER) {
            _statusBannerMessage.value = "Only Owner role can modify tariff rates."
            return
        }
        viewModelScope.launch {
            val result = repository.updateRateConfig(
                dailyRate = dailyRate,
                weeklyPassRate = weeklyPassRate,
                monthlyPassRate = monthlyPassRate,
                ownerPin = ownerPin,
                lowLightThreshold = lowLightThreshold
            )
            result.onSuccess {
                _statusBannerMessage.value = "Tariff rates & Low-light threshold updated."
                refreshPlateInspection(_plateInput.value)
            }.onFailure { err ->
                _statusBannerMessage.value = "Failed to save rates: ${err.localizedMessage}"
            }
        }
    }

    // --- 6. Gemini AI Features ---

    /**
     * Feature 1: Analyze uploaded/captured vehicle & license plate photo using `gemini-4-argon`.
     */
    fun analyzePlateImageWithGemini(bitmap: Bitmap) {
        _lastCapturedBitmap.value = bitmap
        viewModelScope.launch {
            _visionAnalysisState.value = AiTaskState.Loading
            try {
                val response = withTimeout(60_000L) {
                    geminiVisionModel.generateContent(
                        content {
                            image(bitmap)
                            text(
                                "Analyze this two-wheeler image from an Indian parking stand. " +
                                    "Extract the normalized Indian registration plate number (e.g. TN39AB1234), " +
                                    "describe the two-wheeler model/color, note the plate condition (e.g. rusted, ghost plate, two-line, HSRP), " +
                                    "and identify the RTO state/district."
                            )
                        }
                    )
                }
                val jsonText = response.text ?: throw IllegalStateException("Empty response from gemini-4-argon")
                val json = JSONObject(jsonText)
                val extractedPlate = BillingRepository.normalizePlateNumber(json.optString("plateNumber", ""))
                val vehicleDesc = json.optString("vehicleDescription", "Two-wheeler")
                val condition = json.optString("plateCondition", "Standard")
                val rto = json.optString("rtoRegion", AnprFrameAnalyzer.lookupRtoDistrict(extractedPlate))

                val analysis = VisionPlateAnalysis(
                    plateNumber = extractedPlate,
                    vehicleDescription = vehicleDesc,
                    plateCondition = condition,
                    rtoRegion = rto
                )
                _lastVisionPlateAnalysis.value = analysis
                if (extractedPlate.isNotBlank()) {
                    onPlateInputChanged(extractedPlate)
                }
                _visionAnalysisState.value = AiTaskState.Success(
                    "Plate: ${extractedPlate.ifBlank { "Unreadable" }} • $vehicleDesc ($condition • $rto)"
                )
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Gemini Vision analysis timed out", e)
                _visionAnalysisState.value = AiTaskState.Error("Image analysis timed out after 60s. Please retry.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Gemini Vision analysis failed", e)
                _visionAnalysisState.value = AiTaskState.Error(e.localizedMessage ?: "Gemini image analysis failed.")
            }
        }
    }

    /**
     * Feature 2: Ultra-low-latency RTO & Plate Intelligence using `gemini-3.1-flash-lite`.
     */
    fun runFastPlateLookup(plateNumber: String = _plateInput.value) {
        val targetPlate = plateNumber.ifBlank { "TN39" }
        val isTamil = _appLanguage.value == AppLanguage.TAMIL
        viewModelScope.launch {
            _fastRtoHintState.value = AiTaskState.Loading
            try {
                val langInstruction = if (isTamil) {
                    "Respond in simple Tamil (தமிழ்) suitable for a parking worker."
                } else {
                    "Respond in concise English."
                }
                val response = withTimeout(30_000L) {
                    geminiFastLiteModel.generateContent(
                        "Provide a concise 1-sentence Indian RTO lookup and format verification for license plate '$targetPlate' " +
                            "(include district/city name, state, and whether it matches standard Indian HSRP format). $langInstruction"
                    )
                }
                val text = response.text?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    _fastRtoHintState.value = AiTaskState.Success(text)
                } else {
                    _fastRtoHintState.value = AiTaskState.Error("Empty response from fast model.")
                }
            } catch (e: TimeoutCancellationException) {
                _fastRtoHintState.value = AiTaskState.Error("Fast lookup timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Fast RTO lookup failed", e)
                _fastRtoHintState.value = AiTaskState.Error(e.localizedMessage ?: "Fast lookup failed.")
            }
        }
    }

    /**
     * Feature 3: Deep Anti-Theft & Cash Skimming Audit using `gemini-4-argon` with `ThinkingLevel.HIGH`.
     * Do NOT set `maxOutputTokens`.
     */
    fun runDeepOwnerAudit() {
        viewModelScope.launch {
            _ownerDeepAuditState.value = AiTaskState.Loading
            try {
                val summary = shiftAuditSummary.value
                val recentLogs = vehicleLogs.value.take(25)
                val openCredits = credits.value.filter { !it.isSettled && it.pendingAmount > 0 }
                val rates = rateConfig.value

                val logLines = recentLogs.joinToString("\n") { log ->
                    "- Plate=${log.plateNumber}, Status=${log.status}, Payment=${log.paymentStatus}, " +
                        "Days=${log.daysCharged}, Due=₹${log.totalAmountDue.toInt()}, Collected=₹${log.amountCollected.toInt()}, Worker=${log.workerName}"
                }
                val creditLines = openCredits.joinToString("\n") { c ->
                    "- Plate=${c.plateNumber}, Pending=₹${c.pendingAmount.toInt()}, Note=${c.notes}"
                }

                val prompt = """
                    Perform a rigorous anti-theft commercial parking audit for the Owner of MotoVault ANPR:
                    
                    [LIVE SHIFT METRICS]
                    - Bikes Currently Inside: ${summary.totalBikesInside}
                    - Exits Today: ${summary.totalExitsToday}
                    - Expected Physical Cash in Drawer: ₹${summary.expectedCashInDrawer.toInt()} (Exits: ₹${summary.cashFromExitsToday.toInt()}, Passes: ₹${summary.cashFromPassesToday.toInt()})
                    - Digital UPI Collected Today: ₹${summary.totalUpiToday.toInt()}
                    - Total Pending 'Pay Tomorrow' Credits: ₹${summary.totalPendingCreditAmount.toInt()} across ${summary.activeCreditAccountsCount} vehicles
                    - Configured Rates: Daily=₹${rates.dailyRate.toInt()}, Weekly=₹${rates.weeklyPassRate.toInt()}, Monthly=₹${rates.monthlyPassRate.toInt()}
                    
                    [RECENT VEHICLE LOGS]
                    ${logLines.ifBlank { "No vehicle logs recorded yet." }}
                    
                    [OPEN 'PAY TOMORROW' CREDITS]
                    ${creditLines.ifBlank { "No unpaid credits." }}
                    
                    Provide:
                    1. Cash Drawer Reconciliation & Skimming Risk Assessment (Cash vs UPI vs Unpaid Credit ratios).
                    2. Multi-Day Stay & Overdue Credit Recovery Priorities.
                    3. 3 Actionable Recommendations to maximize stand revenue and prevent leakage today.
                """.trimIndent()

                val accumulated = StringBuilder()
                withTimeout(60_000L) {
                    geminiHighThinkingAuditModel.generateContentStream(prompt).collect { chunk ->
                        accumulated.append(chunk.text.orEmpty())
                        _ownerDeepAuditState.value = AiTaskState.Streaming(accumulated.toString())
                    }
                }
                val finalOutput = accumulated.toString().trim()
                if (finalOutput.isNotEmpty()) {
                    _ownerDeepAuditState.value = AiTaskState.Success(finalOutput)
                } else {
                    _ownerDeepAuditState.value = AiTaskState.Error("Audit model returned an empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                Log.e(TAG, "Deep owner audit timed out", e)
                _ownerDeepAuditState.value = AiTaskState.Error("Deep audit timed out after 60 seconds.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Deep owner audit failed", e)
                _ownerDeepAuditState.value = AiTaskState.Error(e.localizedMessage ?: "Audit failed.")
            }
        }
    }

    /**
     * Feature 4: Customer Dispute & Receipt Breakdown using `gemini-3.8-flash`.
     */
    fun generateDisputeExplanation(log: VehicleLogEntity) {
        viewModelScope.launch {
            _disputeSummaryState.value = AiTaskState.Loading
            try {
                val fmt = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                val entryStr = fmt.format(Date(log.entryTimestamp))
                val exitStr = fmt.format(Date(log.exitTimestamp ?: System.currentTimeMillis()))
                val bill = BillingRepository.calculateExitBill(
                    activeLog = log,
                    exitTimestamp = log.exitTimestamp ?: System.currentTimeMillis(),
                    rateConfig = rateConfig.value,
                    activePass = passes.value.firstOrNull { it.plateNumber == log.plateNumber },
                    unpaidCredit = credits.value.firstOrNull { it.plateNumber == log.plateNumber }
                )

                val isTamil = _appLanguage.value == AppLanguage.TAMIL
                val langInstruction = if (isTamil) {
                    "Write the explanation in clear, polite Tamil (தமிழ்) so the worker and customer can easily read it."
                } else {
                    "Keep it concise (3-4 bullet points) suitable for showing directly to the customer."
                }
                val prompt = """
                    Generate a clear, polite, non-negotiable parking fee explanation for a customer at an Indian two-wheeler stand:
                    - Vehicle Plate: ${log.plateNumber}
                    - Entry Time (Photo Watermarked): $entryStr
                    - Current / Exit Time: $exitStr
                    - Rolling 24-Hour Blocks Charged: ${bill.daysCharged} day(s) @ ₹${bill.dailyRateUsed.toInt()}/day = ₹${bill.baseParkingFee.toInt()}
                    - Previous Pending Credit (Udhaar): ₹${bill.previousPendingCredit.toInt()}
                    - Active Pass Covered: ${bill.isCoveredByPass}
                    - Total Non-Negotiable Amount Due: ₹${bill.totalAmountDue.toInt()}
                    $langInstruction
                """.trimIndent()

                val response = withTimeout(45_000L) {
                    geminiGeneralFlashModel.generateContent(prompt)
                }
                val text = response.text?.trim().orEmpty()
                if (text.isNotEmpty()) {
                    _disputeSummaryState.value = AiTaskState.Success(text)
                } else {
                    _disputeSummaryState.value = AiTaskState.Error("Empty response.")
                }
            } catch (e: TimeoutCancellationException) {
                _disputeSummaryState.value = AiTaskState.Error("Receipt explanation timed out.")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Dispute explanation failed", e)
                _disputeSummaryState.value = AiTaskState.Error(e.localizedMessage ?: "Failed to generate explanation.")
            }
        }
    }
}
