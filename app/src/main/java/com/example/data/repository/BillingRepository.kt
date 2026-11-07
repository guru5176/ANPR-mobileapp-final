package com.example.data.repository

import android.content.Context
import com.example.R
import com.example.data.local.CreditEntity
import com.example.data.local.ExitBillCalculation
import com.example.data.local.INDIAN_PLATE_REGEX
import com.example.data.local.ParkingDao
import com.example.data.local.ParkingDatabase
import com.example.data.local.PassEntity
import com.example.data.local.PassType
import com.example.data.local.PaymentStatus
import com.example.data.local.PlateInspectionResult
import com.example.data.local.RateConfig
import com.example.data.local.ShiftAuditSummary
import com.example.data.local.SyncEntityType
import com.example.data.local.SyncFlushResult
import com.example.data.local.SyncOperationType
import com.example.data.local.SyncQueueEntity
import com.example.data.local.SyncStatus
import com.example.data.local.VehicleLogEntity
import com.example.data.local.VehicleStatus
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.snapshots
import java.util.Calendar
import java.util.UUID
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.ceil
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

/**
 * BillingRepository:
 * Core engine for 24-hour rolling multi-day fee calculation, Weekly/Monthly Pass validation,
 * "Pay Tomorrow" (Udhaar) debt accumulation, Expected Cash-in-Drawer anti-theft auditing,
 * and offline-first Room Sync Queue + Firebase Firestore synchronization.
 */
class BillingRepository(
    private val firestore: FirebaseFirestore,
    private val parkingDao: ParkingDao? = null
) {
    constructor(context: Context) : this(
        firestore = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        parkingDao = ParkingDatabase.getInstance(context.applicationContext).parkingDao()
    )

    private val auth = Firebase.auth
    private val syncMutex = Mutex()

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    companion object {
        const val MILLIS_PER_24_HOURS = 86_400_000L
        const val WEEKLY_DURATION_MS = 7 * MILLIS_PER_24_HOURS
        const val MONTHLY_DURATION_MS = 30 * MILLIS_PER_24_HOURS
        private const val FAST_CLOUD_WRITE_TIMEOUT_MS = 4_000L
        private const val QUEUE_FLUSH_ITEM_TIMEOUT_MS = 8_000L

        /**
         * Normalizes raw OCR or manual input into uppercase alphanumeric format
         * and corrects common OCR confusions in Indian RTO positions.
         */
        fun normalizePlateNumber(raw: String): String {
            val cleaned = raw.uppercase().replace(Regex("[^A-Z0-9]"), "")
            if (cleaned.length < 6) return cleaned
            val chars = cleaned.toCharArray()
            // First 2 characters in Indian plates are always state letters (e.g. TN, KA, MH, DL)
            for (i in 0..1) {
                chars[i] = when (chars[i]) {
                    '0' -> 'O'
                    '1' -> 'I'
                    '8' -> 'B'
                    '5' -> 'S'
                    else -> chars[i]
                }
            }
            // Characters 2..3 are RTO district digits (e.g. 39, 42, 01)
            for (i in 2..minOf(3, chars.lastIndex)) {
                if (i == 2 || (i == 3 && chars.size >= 8)) {
                    chars[i] = when (chars[i]) {
                        'O', 'Q' -> '0'
                        'I', 'L' -> '1'
                        'Z' -> '2'
                        'S' -> '5'
                        'B' -> '8'
                        else -> chars[i]
                    }
                }
            }
            // Last 4 characters are numeric registration digits (if preceded by letters)
            val tailStart = max(4, chars.size - 4)
            for (i in tailStart until chars.size) {
                chars[i] = when (chars[i]) {
                    'O', 'Q' -> '0'
                    'I', 'L' -> '1'
                    'Z' -> '2'
                    'S' -> '5'
                    'B' -> '8'
                    else -> chars[i]
                }
            }
            return String(chars)
        }

        fun isValidIndianPlate(plate: String): Boolean {
            return INDIAN_PLATE_REGEX.matches(plate.trim().uppercase())
        }

        /**
         * Exact 24-hour rolling block fee calculation:
         * - Minimum 1 day (24h block) charged on any stay.
         * - Every started 24-hour block increments daysCharged.
         * - If covered by an active Weekly/Monthly Pass, baseParkingFee is ₹0.0.
         * - Any unpaid "Pay Tomorrow" credit is automatically added to totalAmountDue.
         */
        fun calculateExitBill(
            activeLog: VehicleLogEntity,
            exitTimestamp: Long,
            rateConfig: RateConfig,
            activePass: PassEntity?,
            unpaidCredit: CreditEntity?
        ): ExitBillCalculation {
            val elapsedMillis = max(0L, exitTimestamp - activeLog.entryTimestamp)
            val daysCharged = max(1, ceil(elapsedMillis.toDouble() / MILLIS_PER_24_HOURS.toDouble()).toInt())

            val isPassValid = activePass != null && activePass.isActive(exitTimestamp)
            val passDaysLeft = activePass?.daysRemaining(exitTimestamp) ?: 0

            val baseParkingFee = if (isPassValid) {
                0.0
            } else {
                daysCharged * rateConfig.dailyRate
            }

            val pendingCredit = if (unpaidCredit != null && !unpaidCredit.isSettled && unpaidCredit.pendingAmount > 0.0) {
                unpaidCredit.pendingAmount
            } else {
                0.0
            }

            val totalDue = baseParkingFee + pendingCredit

            return ExitBillCalculation(
                logId = activeLog.id,
                plateNumber = activeLog.plateNumber,
                entryTimestamp = activeLog.entryTimestamp,
                exitTimestamp = exitTimestamp,
                photoPath = activeLog.photoPath,
                elapsedMillis = elapsedMillis,
                daysCharged = daysCharged,
                dailyRateUsed = rateConfig.dailyRate,
                isCoveredByPass = isPassValid,
                passDaysRemaining = passDaysLeft,
                baseParkingFee = baseParkingFee,
                previousPendingCredit = pendingCredit,
                previousCreditDate = unpaidCredit?.takeIf { pendingCredit > 0 }?.lastUnpaidTimestamp,
                totalAmountDue = totalDue
            )
        }

        /**
         * Computes the live Owner Shift Audit metrics strictly separating physical Cash from UPI
         * and aggregating active "Pay Tomorrow" credits.
         */
        fun computeShiftAuditSummary(
            logs: List<VehicleLogEntity>,
            passes: List<PassEntity>,
            credits: List<CreditEntity>,
            nowMillis: Long = System.currentTimeMillis()
        ): ShiftAuditSummary {
            val startOfToday = Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis

            val insideCount = logs.count { it.status == VehicleStatus.INSIDE }
            val exitedTodayLogs = logs.filter {
                it.status == VehicleStatus.EXITED && (it.exitTimestamp ?: 0L) >= startOfToday
            }

            val cashExitsToday = exitedTodayLogs
                .filter { it.paymentStatus == PaymentStatus.PAID_CASH }
                .sumOf { it.amountCollected }

            val upiExitsToday = exitedTodayLogs
                .filter { it.paymentStatus == PaymentStatus.PAID_UPI }
                .sumOf { it.amountCollected }

            val passesSoldToday = passes.filter { it.startDate >= startOfToday }
            val cashPassesToday = passesSoldToday
                .filter { it.paymentMode == PaymentStatus.PAID_CASH }
                .sumOf { it.amountPaid }

            val upiPassesToday = passesSoldToday
                .filter { it.paymentMode == PaymentStatus.PAID_UPI }
                .sumOf { it.amountPaid }

            val activeCredits = credits.filter { !it.isSettled && it.pendingAmount > 0.0 }
            val totalPendingCredits = activeCredits.sumOf { it.pendingAmount }
            val activePassesCount = passes.count { it.isActive(nowMillis) }

            return ShiftAuditSummary(
                totalBikesInside = insideCount,
                totalExitsToday = exitedTodayLogs.size,
                cashFromExitsToday = cashExitsToday,
                cashFromPassesToday = cashPassesToday,
                expectedCashInDrawer = cashExitsToday + cashPassesToday,
                upiFromExitsToday = upiExitsToday,
                upiFromPassesToday = upiPassesToday,
                totalUpiToday = upiExitsToday + upiPassesToday,
                totalPendingCreditAmount = totalPendingCredits,
                activeCreditAccountsCount = activeCredits.size,
                activePassesCount = activePassesCount
            )
        }
    }

    // --- Firestore Real-Time Flows (Used for Cloud Sync & Rule Verification) ---

    fun observeCloudVehicleLogs(): Flow<List<VehicleLogEntity>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/vehicle_logs"
        emitAll(
            firestore.collection("users").document(uid).collection("vehicle_logs")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> doc.toVehicleLogEntity() }
                        .sortedByDescending { it.entryTimestamp }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudPasses(): Flow<List<PassEntity>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/passes"
        emitAll(
            firestore.collection("users").document(uid).collection("passes")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> doc.toPassEntity() }
                        .sortedByDescending { it.expiryDate }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudCredits(): Flow<List<CreditEntity>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/credits"
        emitAll(
            firestore.collection("users").document(uid).collection("credits")
                .snapshots()
                .map { snapshot ->
                    snapshot.documents.mapNotNull { doc -> doc.toCreditEntity() }
                        .sortedByDescending { it.lastUnpaidTimestamp }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeCloudRateConfig(): Flow<RateConfig> = flow {
        val uid = requireUserId()
        val path = "users/$uid/config/rates"
        emitAll(
            firestore.collection("users").document(uid).collection("config").document("rates")
                .snapshots()
                .map { doc -> doc.toRateConfig(uid) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Starts real-time cloud-to-local Room synchronization when authenticated,
     * while preserving any un-synced local offline queue modifications in Room.
     */
    fun startRealtimeSync(scope: CoroutineScope, onSyncError: (String) -> Unit = {}) {
        val dao = parkingDao ?: return
        scope.launch {
            flushSyncQueue()
        }
        scope.launch {
            observeCloudVehicleLogs()
                .catch { e -> onSyncError(e.localizedMessage ?: "Cloud log sync error") }
                .collect { cloudLogs ->
                    val pendingLogIds = dao.getPendingEntityIdsByType(SyncEntityType.VEHICLE_LOG).toSet()
                    val safeToMerge = cloudLogs.filter { it.id !in pendingLogIds }
                    if (safeToMerge.isNotEmpty()) {
                        dao.upsertVehicleLogs(safeToMerge)
                    }
                }
        }
        scope.launch {
            observeCloudPasses()
                .catch { e -> onSyncError(e.localizedMessage ?: "Cloud pass sync error") }
                .collect { cloudPasses ->
                    val pendingPlates = dao.getPendingEntityIdsByType(SyncEntityType.PASS).toSet()
                    val safeToMerge = cloudPasses.filter { it.plateNumber !in pendingPlates }
                    if (safeToMerge.isNotEmpty()) {
                        dao.upsertPasses(safeToMerge)
                    }
                }
        }
        scope.launch {
            observeCloudCredits()
                .catch { e -> onSyncError(e.localizedMessage ?: "Cloud credit sync error") }
                .collect { cloudCredits ->
                    val pendingPlates = dao.getPendingEntityIdsByType(SyncEntityType.CREDIT).toSet()
                    val safeToMerge = cloudCredits.filter { it.plateNumber !in pendingPlates }
                    if (safeToMerge.isNotEmpty()) {
                        dao.upsertCredits(safeToMerge)
                    }
                }
        }
        scope.launch {
            observeCloudRateConfig()
                .catch { /* Keep local rate config if cloud doc not created yet */ }
                .collect { cloudConfig ->
                    val pendingConfigIds = dao.getPendingEntityIdsByType(SyncEntityType.RATE_CONFIG).toSet()
                    if ("rates" !in pendingConfigIds) {
                        dao.upsertRateConfig(cloudConfig)
                    }
                }
        }
    }

    // --- Offline-First Combined Flows & Sync Queue Observers ---

    fun observeVehicleLogs(): Flow<List<VehicleLogEntity>> {
        return parkingDao?.observeAllVehicleLogs() ?: observeCloudVehicleLogs()
    }

    fun observePasses(): Flow<List<PassEntity>> {
        return parkingDao?.observeAllPasses() ?: observeCloudPasses()
    }

    fun observeCredits(): Flow<List<CreditEntity>> {
        return parkingDao?.observeAllCredits() ?: observeCloudCredits()
    }

    fun observeRateConfig(): Flow<RateConfig> {
        val localFlow = parkingDao?.observeRateConfig()?.map { it ?: RateConfig() }
        return localFlow ?: observeCloudRateConfig()
    }

    fun observePendingSyncQueue(): Flow<List<SyncQueueEntity>> {
        return parkingDao?.observePendingSyncQueue() ?: flowOf(emptyList())
    }

    fun observePendingSyncCount(): Flow<Int> {
        return parkingDao?.observePendingSyncCount() ?: flowOf(0)
    }

    fun observeShiftAuditSummary(): Flow<ShiftAuditSummary> {
        return combine(
            observeVehicleLogs(),
            observePasses(),
            observeCredits()
        ) { logs, passes, credits ->
            computeShiftAuditSummary(logs, passes, credits)
        }
    }

    // --- Instant Plate Inspection & Active Exit Record Search ---

    suspend fun inspectPlate(
        rawPlate: String,
        nowMillis: Long = System.currentTimeMillis(),
        searchCloudIfMissing: Boolean = false
    ): PlateInspectionResult {
        val normalized = normalizePlateNumber(rawPlate)
        val valid = isValidIndianPlate(normalized)
        if (normalized.length < 4) {
            return PlateInspectionResult(plateNumber = normalized, isValidFormat = false)
        }

        val rateConfig = parkingDao?.getRateConfig() ?: RateConfig()
        var activeLog = parkingDao?.getActiveInsideLogForPlate(normalized)

        // When in Exit mode or explicitly searching for an active record, also check Firestore if not found locally
        if (activeLog == null && searchCloudIfMissing) {
            val uid = auth.currentUser?.uid
            if (uid != null) {
                try {
                    val querySnap = withTimeout(FAST_CLOUD_WRITE_TIMEOUT_MS) {
                        firestore.collection("users")
                            .document(uid)
                            .collection("vehicle_logs")
                            .whereEqualTo("plateNumber", normalized)
                            .whereEqualTo("status", VehicleStatus.INSIDE)
                            .get()
                            .await()
                    }
                    val cloudActive = querySnap.documents
                        .mapNotNull { it.toVehicleLogEntity() }
                        .maxByOrNull { it.entryTimestamp }
                    if (cloudActive != null) {
                        parkingDao?.upsertVehicleLog(cloudActive)
                        activeLog = cloudActive
                    }
                } catch (e: CancellationException) {
                    if (e !is TimeoutCancellationException) throw e
                } catch (_: Exception) {
                    // Offline fallback: rely on Room cache
                }
            }
        }

        val pass = parkingDao?.getPassForPlate(normalized)
        val credit = parkingDao?.getCreditForPlate(normalized)?.takeIf { !it.isSettled && it.pendingAmount > 0.0 }

        val passActive = pass?.isActive(nowMillis) == true
        val passDaysLeft = pass?.daysRemaining(nowMillis) ?: 0

        val exitPreview = activeLog?.let {
            calculateExitBill(
                activeLog = it,
                exitTimestamp = nowMillis,
                rateConfig = rateConfig,
                activePass = pass,
                unpaidCredit = credit
            )
        }

        return PlateInspectionResult(
            plateNumber = normalized,
            isValidFormat = valid,
            activeInsideLog = activeLog,
            passEntity = pass,
            isPassActive = passActive,
            passDaysLeft = passDaysLeft,
            unpaidCredit = credit,
            exitBillPreview = exitPreview
        )
    }

    // --- Write Operations (Offline-First Room Sync Queue + Firestore Upload) ---

    private fun isFatalSecurityError(error: Throwable): Boolean {
        val firestoreException = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull() ?: return false
        return firestoreException.code == FirebaseFirestoreException.Code.PERMISSION_DENIED ||
            firestoreException.code == FirebaseFirestoreException.Code.UNAUTHENTICATED
    }

    suspend fun logVehicleEntry(
        plateNumber: String,
        photoPath: String,
        workerName: String,
        notes: String? = null,
        entryTimestamp: Long = System.currentTimeMillis()
    ): Result<VehicleLogEntity> = runCatching {
        val uid = requireUserId()
        val normalizedPlate = normalizePlateNumber(plateNumber)
        require(normalizedPlate.length >= 4) { "Plate number must be at least 4 characters" }

        val rateConfig = parkingDao?.getRateConfig() ?: RateConfig(userId = uid)
        val credit = parkingDao?.getCreditForPlate(normalizedPlate)?.takeIf { !it.isSettled && it.pendingAmount > 0 }
        val pass = parkingDao?.getPassForPlate(normalizedPlate)
        val isPassValid = pass?.isActive(entryTimestamp) == true

        val baseFee = if (isPassValid) 0.0 else rateConfig.dailyRate
        val previousDebt = credit?.pendingAmount ?: 0.0

        val logId = "log_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val dao = parkingDao
        val entity = VehicleLogEntity(
            id = logId,
            userId = uid,
            plateNumber = normalizedPlate,
            entryTimestamp = entryTimestamp,
            exitTimestamp = null,
            photoPath = photoPath.ifBlank { "/storage/emulated/0/entry_default.jpg" },
            status = VehicleStatus.INSIDE,
            paymentStatus = if (isPassValid && previousDebt == 0.0) PaymentStatus.PASS_COVERED else PaymentStatus.PENDING,
            daysCharged = 1,
            baseFee = baseFee,
            previousCreditAdded = previousDebt,
            totalAmountDue = baseFee + previousDebt,
            amountCollected = 0.0,
            workerName = workerName.ifBlank { "Worker" },
            notes = notes,
            syncStatus = if (dao != null) SyncStatus.PENDING_SYNC else SyncStatus.SYNCED
        )

        val docRef = firestore.collection("users").document(uid).collection("vehicle_logs").document(logId)

        if (dao == null) {
            try {
                docRef.set(entity.toFirestoreMap(isCreate = true)).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
            return@runCatching entity
        }

        // 1. Persist immediately to Room & enqueue in SyncQueueEntity for offline durability
        dao.upsertVehicleLog(entity)
        dao.enqueueSyncItem(
            SyncQueueEntity(
                userId = uid,
                entityType = SyncEntityType.VEHICLE_LOG,
                entityId = logId,
                operationType = SyncOperationType.CREATE_VEHICLE_LOG,
                enqueuedAt = entryTimestamp
            )
        )

        // 2. Attempt fast cloud upload; if offline or cellular connection times out, keep queued in Room
        try {
            withTimeout(FAST_CLOUD_WRITE_TIMEOUT_MS) {
                docRef.set(entity.toFirestoreMap(isCreate = true)).await()
            }
            dao.deleteSyncItemForEntity(SyncEntityType.VEHICLE_LOG, logId)
            dao.updateVehicleLogSyncStatus(logId, SyncStatus.SYNCED)
            entity.copy(syncStatus = SyncStatus.SYNCED)
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
            entity
        } catch (e: Exception) {
            if (isFatalSecurityError(e)) {
                handleFirestoreError(e, OperationType.CREATE, docRef.path)
                throw e
            }
            // Offline or poor cellular connectivity: keep in Room sync_queue for automatic background upload
            entity
        }
    }

    suspend fun processVehicleExit(
        logId: String,
        paymentChoice: String,
        exitTimestamp: Long = System.currentTimeMillis(),
        notes: String? = null
    ): Result<VehicleLogEntity> = runCatching {
        val uid = requireUserId()
        val dao = parkingDao
        val existingLog = dao?.getVehicleLogById(logId)
            ?: getVehicleLogFromCloud(uid, logId)
            ?: throw IllegalArgumentException("Active vehicle log not found for ID: $logId")

        val rateConfig = dao?.getRateConfig() ?: RateConfig(userId = uid)
        val pass = dao?.getPassForPlate(existingLog.plateNumber)
        val credit = dao?.getCreditForPlate(existingLog.plateNumber)

        val bill = calculateExitBill(
            activeLog = existingLog,
            exitTimestamp = exitTimestamp,
            rateConfig = rateConfig,
            activePass = pass,
            unpaidCredit = credit
        )

        val finalPaymentStatus = when {
            bill.totalAmountDue == 0.0 && bill.isCoveredByPass -> PaymentStatus.PASS_COVERED
            paymentChoice == PaymentStatus.PAID_CASH -> PaymentStatus.PAID_CASH
            paymentChoice == PaymentStatus.PAID_UPI -> PaymentStatus.PAID_UPI
            paymentChoice == PaymentStatus.UNPAID_CREDIT -> PaymentStatus.UNPAID_CREDIT
            else -> PaymentStatus.PAID_CASH
        }

        val collectedAmount = when (finalPaymentStatus) {
            PaymentStatus.PAID_CASH, PaymentStatus.PAID_UPI -> bill.totalAmountDue
            else -> 0.0
        }

        // If the entry log was never synced to Firestore yet, keep its operation as CREATE_VEHICLE_LOG
        val wasNeverSynced = existingLog.syncStatus != SyncStatus.SYNCED
        val updatedLog = existingLog.copy(
            exitTimestamp = exitTimestamp,
            status = VehicleStatus.EXITED,
            paymentStatus = finalPaymentStatus,
            daysCharged = bill.daysCharged,
            baseFee = bill.baseParkingFee,
            previousCreditAdded = bill.previousPendingCredit,
            totalAmountDue = bill.totalAmountDue,
            amountCollected = collectedAmount,
            notes = notes ?: existingLog.notes,
            syncStatus = if (dao != null) SyncStatus.PENDING_SYNC else SyncStatus.SYNCED
        )

        val logRef = firestore.collection("users").document(uid).collection("vehicle_logs").document(logId)

        val finalLogResult = if (dao == null) {
            try {
                logRef.set(updatedLog.toFirestoreMap(isCreate = false), SetOptions.merge()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.UPDATE, logRef.path)
                throw e
            }
            updatedLog
        } else {
            // 1. Update local Room log and enqueue in sync_queue
            dao.upsertVehicleLog(updatedLog)
            dao.enqueueSyncItem(
                SyncQueueEntity(
                    userId = uid,
                    entityType = SyncEntityType.VEHICLE_LOG,
                    entityId = logId,
                    operationType = if (wasNeverSynced) {
                        SyncOperationType.CREATE_VEHICLE_LOG
                    } else {
                        SyncOperationType.UPDATE_VEHICLE_LOG
                    },
                    enqueuedAt = exitTimestamp
                )
            )

            // 2. Attempt fast cloud upload; if offline, leave queued in Room
            try {
                withTimeout(FAST_CLOUD_WRITE_TIMEOUT_MS) {
                    if (wasNeverSynced) {
                        logRef.set(updatedLog.toFirestoreMap(isCreate = true)).await()
                    } else {
                        logRef.set(updatedLog.toFirestoreMap(isCreate = false), SetOptions.merge()).await()
                    }
                }
                dao.deleteSyncItemForEntity(SyncEntityType.VEHICLE_LOG, logId)
                dao.updateVehicleLogSyncStatus(logId, SyncStatus.SYNCED)
                updatedLog.copy(syncStatus = SyncStatus.SYNCED)
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                updatedLog
            } catch (e: Exception) {
                if (isFatalSecurityError(e)) {
                    handleFirestoreError(e, OperationType.UPDATE, logRef.path)
                    throw e
                }
                updatedLog
            }
        }

        // 3. Handle "Pay Tomorrow" (Credit/Udhaar) accumulation or settlement
        if (finalPaymentStatus == PaymentStatus.UNPAID_CREDIT && bill.totalAmountDue > 0.0) {
            upsertCreditBalance(
                plateNumber = existingLog.plateNumber,
                pendingAmount = bill.totalAmountDue,
                timestamp = exitTimestamp,
                notes = "Unpaid exit (${bill.daysCharged}d stay)",
                isSettled = false
            ).getOrThrow()
        } else if (
            (finalPaymentStatus == PaymentStatus.PAID_CASH || finalPaymentStatus == PaymentStatus.PAID_UPI) &&
            bill.previousPendingCredit > 0.0
        ) {
            upsertCreditBalance(
                plateNumber = existingLog.plateNumber,
                pendingAmount = 0.0,
                timestamp = exitTimestamp,
                notes = "Settled via $finalPaymentStatus on exit",
                isSettled = true
            ).getOrThrow()
        }

        finalLogResult
    }

    suspend fun registerOrRenewPass(
        plateNumber: String,
        passType: String,
        paymentMode: String,
        customAmount: Double? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): Result<PassEntity> = runCatching {
        val uid = requireUserId()
        val normalizedPlate = normalizePlateNumber(plateNumber)
        require(normalizedPlate.length >= 4) { "Valid plate number required for Pass" }

        val dao = parkingDao
        val rateConfig = dao?.getRateConfig() ?: RateConfig(userId = uid)
        val durationMs = if (passType == PassType.MONTHLY_PASS) MONTHLY_DURATION_MS else WEEKLY_DURATION_MS
        val amount = customAmount ?: if (passType == PassType.MONTHLY_PASS) {
            rateConfig.monthlyPassRate
        } else {
            rateConfig.weeklyPassRate
        }

        val existingPass = dao?.getPassForPlate(normalizedPlate)
        val baseStart = if (existingPass != null && existingPass.expiryDate > nowMillis) {
            existingPass.expiryDate
        } else {
            nowMillis
        }

        val passEntity = PassEntity(
            plateNumber = normalizedPlate,
            userId = uid,
            passType = passType,
            startDate = nowMillis,
            expiryDate = baseStart + durationMs,
            amountPaid = amount,
            paymentMode = paymentMode,
            createdAtEpoch = nowMillis
        )

        val passRef = firestore.collection("users").document(uid).collection("passes").document(normalizedPlate)

        if (dao == null) {
            try {
                passRef.set(passEntity.toFirestoreMap(isCreate = true), SetOptions.merge()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, passRef.path)
                throw e
            }
            return@runCatching passEntity
        }

        dao.upsertPass(passEntity)
        dao.enqueueSyncItem(
            SyncQueueEntity(
                userId = uid,
                entityType = SyncEntityType.PASS,
                entityId = normalizedPlate,
                operationType = SyncOperationType.UPSERT_PASS,
                enqueuedAt = nowMillis
            )
        )

        try {
            withTimeout(FAST_CLOUD_WRITE_TIMEOUT_MS) {
                passRef.set(passEntity.toFirestoreMap(isCreate = true), SetOptions.merge()).await()
            }
            dao.deleteSyncItemForEntity(SyncEntityType.PASS, normalizedPlate)
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
        } catch (e: Exception) {
            if (isFatalSecurityError(e)) {
                handleFirestoreError(e, OperationType.WRITE, passRef.path)
                throw e
            }
        }

        passEntity
    }

    suspend fun upsertCreditBalance(
        plateNumber: String,
        pendingAmount: Double,
        timestamp: Long = System.currentTimeMillis(),
        notes: String = "Pay Tomorrow credit",
        isSettled: Boolean = false
    ): Result<CreditEntity> = runCatching {
        val uid = requireUserId()
        val normalizedPlate = normalizePlateNumber(plateNumber)
        val creditEntity = CreditEntity(
            plateNumber = normalizedPlate,
            userId = uid,
            pendingAmount = pendingAmount,
            lastUnpaidTimestamp = timestamp,
            notes = notes.take(450),
            isSettled = isSettled
        )

        val dao = parkingDao
        val creditRef = firestore.collection("users").document(uid).collection("credits").document(normalizedPlate)

        if (dao == null) {
            try {
                creditRef.set(creditEntity.toFirestoreMap(isCreate = true), SetOptions.merge()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, creditRef.path)
                throw e
            }
            return@runCatching creditEntity
        }

        dao.upsertCredit(creditEntity)
        dao.enqueueSyncItem(
            SyncQueueEntity(
                userId = uid,
                entityType = SyncEntityType.CREDIT,
                entityId = normalizedPlate,
                operationType = SyncOperationType.UPSERT_CREDIT,
                enqueuedAt = timestamp
            )
        )

        try {
            withTimeout(FAST_CLOUD_WRITE_TIMEOUT_MS) {
                creditRef.set(creditEntity.toFirestoreMap(isCreate = true), SetOptions.merge()).await()
            }
            dao.deleteSyncItemForEntity(SyncEntityType.CREDIT, normalizedPlate)
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
        } catch (e: Exception) {
            if (isFatalSecurityError(e)) {
                handleFirestoreError(e, OperationType.WRITE, creditRef.path)
                throw e
            }
        }

        creditEntity
    }

    suspend fun updateRateConfig(
        dailyRate: Double,
        weeklyPassRate: Double,
        monthlyPassRate: Double,
        ownerPin: String,
        lowLightThreshold: Double
    ): Result<RateConfig> = runCatching {
        val uid = requireUserId()
        val config = RateConfig(
            id = 1,
            userId = uid,
            dailyRate = dailyRate.coerceIn(1.0, 10000.0),
            weeklyPassRate = weeklyPassRate.coerceIn(0.0, 50000.0),
            monthlyPassRate = monthlyPassRate.coerceIn(0.0, 100000.0),
            ownerPin = ownerPin.trim().takeIf { it.length in 4..8 } ?: "1234",
            lowLightThreshold = lowLightThreshold.coerceIn(5.0, 200.0)
        )

        val dao = parkingDao
        val configRef = firestore.collection("users").document(uid).collection("config").document("rates")

        if (dao == null) {
            try {
                configRef.set(config.toFirestoreMap(isCreate = true), SetOptions.merge()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.WRITE, configRef.path)
                throw e
            }
            return@runCatching config
        }

        dao.upsertRateConfig(config)
        dao.enqueueSyncItem(
            SyncQueueEntity(
                userId = uid,
                entityType = SyncEntityType.RATE_CONFIG,
                entityId = "rates",
                operationType = SyncOperationType.UPSERT_RATE_CONFIG,
                enqueuedAt = System.currentTimeMillis()
            )
        )

        try {
            withTimeout(FAST_CLOUD_WRITE_TIMEOUT_MS) {
                configRef.set(config.toFirestoreMap(isCreate = true), SetOptions.merge()).await()
            }
            dao.deleteSyncItemForEntity(SyncEntityType.RATE_CONFIG, "rates")
        } catch (e: CancellationException) {
            if (e !is TimeoutCancellationException) throw e
        } catch (e: Exception) {
            if (isFatalSecurityError(e)) {
                handleFirestoreError(e, OperationType.WRITE, configRef.path)
                throw e
            }
        }

        config
    }

    /**
     * Flushes all pending items in the Room `sync_queue` to Firebase Firestore in FIFO order.
     * Invoked automatically when cellular/Wi-Fi connectivity is restored or manually by the user.
     */
    suspend fun flushSyncQueue(): SyncFlushResult = syncMutex.withLock {
        val dao = parkingDao ?: return SyncFlushResult(0, 0, 0)
        val uid = auth.currentUser?.uid ?: return SyncFlushResult(0, 0, dao.getAllPendingSyncItems().size)

        val pendingItems = dao.getAllPendingSyncItems()
        if (pendingItems.isEmpty()) {
            return SyncFlushResult(0, 0, 0)
        }

        var uploadedCount = 0
        var failedCount = 0

        for (item in pendingItems) {
            try {
                withTimeout(QUEUE_FLUSH_ITEM_TIMEOUT_MS) {
                    when (item.entityType) {
                        SyncEntityType.VEHICLE_LOG -> {
                            val localLog = dao.getVehicleLogById(item.entityId)
                            if (localLog == null) {
                                dao.deleteSyncItemById(item.queueId)
                            } else {
                                val docRef = firestore.collection("users")
                                    .document(uid)
                                    .collection("vehicle_logs")
                                    .document(localLog.id)

                                if (item.operationType == SyncOperationType.CREATE_VEHICLE_LOG) {
                                    docRef.set(localLog.toFirestoreMap(isCreate = true)).await()
                                } else {
                                    // Verify if document already exists on cloud; if not, create it
                                    val snap = docRef.get().await()
                                    if (snap.exists()) {
                                        docRef.set(localLog.toFirestoreMap(isCreate = false), SetOptions.merge()).await()
                                    } else {
                                        docRef.set(localLog.toFirestoreMap(isCreate = true)).await()
                                    }
                                }
                                dao.deleteSyncItemById(item.queueId)
                                dao.updateVehicleLogSyncStatus(localLog.id, SyncStatus.SYNCED)
                                uploadedCount++
                            }
                        }

                        SyncEntityType.PASS -> {
                            val localPass = dao.getPassForPlate(item.entityId)
                            if (localPass == null) {
                                dao.deleteSyncItemById(item.queueId)
                            } else {
                                firestore.collection("users")
                                    .document(uid)
                                    .collection("passes")
                                    .document(localPass.plateNumber)
                                    .set(localPass.toFirestoreMap(isCreate = true), SetOptions.merge())
                                    .await()
                                dao.deleteSyncItemById(item.queueId)
                                uploadedCount++
                            }
                        }

                        SyncEntityType.CREDIT -> {
                            val localCredit = dao.getCreditForPlate(item.entityId)
                            if (localCredit == null) {
                                dao.deleteSyncItemById(item.queueId)
                            } else {
                                firestore.collection("users")
                                    .document(uid)
                                    .collection("credits")
                                    .document(localCredit.plateNumber)
                                    .set(localCredit.toFirestoreMap(isCreate = true), SetOptions.merge())
                                    .await()
                                dao.deleteSyncItemById(item.queueId)
                                uploadedCount++
                            }
                        }

                        SyncEntityType.RATE_CONFIG -> {
                            val localConfig = dao.getRateConfig()
                            if (localConfig == null) {
                                dao.deleteSyncItemById(item.queueId)
                            } else {
                                firestore.collection("users")
                                    .document(uid)
                                    .collection("config")
                                    .document("rates")
                                    .set(localConfig.toFirestoreMap(isCreate = true), SetOptions.merge())
                                    .await()
                                dao.deleteSyncItemById(item.queueId)
                                uploadedCount++
                            }
                        }

                        else -> {
                            dao.deleteSyncItemById(item.queueId)
                        }
                    }
                }
            } catch (e: CancellationException) {
                if (e !is TimeoutCancellationException) throw e
                failedCount++
                dao.updateSyncItemRetry(
                    queueId = item.queueId,
                    retryCount = item.retryCount + 1,
                    lastError = "Upload timed out"
                )
            } catch (e: Exception) {
                failedCount++
                dao.updateSyncItemRetry(
                    queueId = item.queueId,
                    retryCount = item.retryCount + 1,
                    lastError = e.localizedMessage ?: "Sync error"
                )
                if (item.entityType == SyncEntityType.VEHICLE_LOG) {
                    dao.updateVehicleLogSyncStatus(item.entityId, SyncStatus.SYNC_FAILED)
                }
            }
        }

        val remainingCount = dao.getAllPendingSyncItems().size
        SyncFlushResult(
            syncedCount = uploadedCount,
            remainingCount = remainingCount,
            failedCount = failedCount
        )
    }

    suspend fun getVehicleLogFromCloud(targetUserId: String, logId: String): VehicleLogEntity? {
        val docRef = firestore.collection("users").document(targetUserId).collection("vehicle_logs").document(logId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) snap.toVehicleLogEntity() else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    // --- Safe DocumentSnapshot Deserialization Helpers ---

    private fun DocumentSnapshot.toVehicleLogEntity(): VehicleLogEntity? {
        val data = this.data ?: return null
        return VehicleLogEntity(
            id = this.id,
            userId = (data["userId"] as? String).orEmpty(),
            plateNumber = (data["plateNumber"] as? String).orEmpty(),
            entryTimestamp = (data["entryTimestamp"] as? Number)?.toLong() ?: 0L,
            exitTimestamp = (data["exitTimestamp"] as? Number)?.toLong(),
            photoPath = (data["photoPath"] as? String).orEmpty(),
            status = (data["status"] as? String) ?: VehicleStatus.INSIDE,
            paymentStatus = (data["paymentStatus"] as? String) ?: PaymentStatus.PENDING,
            daysCharged = (data["daysCharged"] as? Number)?.toInt() ?: 1,
            baseFee = (data["baseFee"] as? Number)?.toDouble() ?: 20.0,
            previousCreditAdded = (data["previousCreditAdded"] as? Number)?.toDouble() ?: 0.0,
            totalAmountDue = (data["totalAmountDue"] as? Number)?.toDouble() ?: 20.0,
            amountCollected = (data["amountCollected"] as? Number)?.toDouble() ?: 0.0,
            workerName = (data["workerName"] as? String) ?: "Worker",
            notes = data["notes"] as? String,
            syncStatus = SyncStatus.SYNCED
        )
    }

    private fun DocumentSnapshot.toPassEntity(): PassEntity? {
        val data = this.data ?: return null
        return PassEntity(
            plateNumber = (data["plateNumber"] as? String) ?: this.id,
            userId = (data["userId"] as? String).orEmpty(),
            passType = (data["passType"] as? String) ?: PassType.WEEKLY_PASS,
            startDate = (data["startDate"] as? Number)?.toLong() ?: 0L,
            expiryDate = (data["expiryDate"] as? Number)?.toLong() ?: 0L,
            amountPaid = (data["amountPaid"] as? Number)?.toDouble() ?: 0.0,
            paymentMode = (data["paymentMode"] as? String) ?: PaymentStatus.PAID_CASH
        )
    }

    private fun DocumentSnapshot.toCreditEntity(): CreditEntity? {
        val data = this.data ?: return null
        return CreditEntity(
            plateNumber = (data["plateNumber"] as? String) ?: this.id,
            userId = (data["userId"] as? String).orEmpty(),
            pendingAmount = (data["pendingAmount"] as? Number)?.toDouble() ?: 0.0,
            lastUnpaidTimestamp = (data["lastUnpaidTimestamp"] as? Number)?.toLong() ?: 0L,
            notes = (data["notes"] as? String).orEmpty(),
            isSettled = (data["isSettled"] as? Boolean) ?: false
        )
    }

    private fun DocumentSnapshot.toRateConfig(defaultUid: String): RateConfig {
        val data = this.data ?: return RateConfig(userId = defaultUid)
        return RateConfig(
            id = 1,
            userId = (data["userId"] as? String) ?: defaultUid,
            dailyRate = (data["dailyRate"] as? Number)?.toDouble() ?: 20.0,
            weeklyPassRate = (data["weeklyPassRate"] as? Number)?.toDouble() ?: 100.0,
            monthlyPassRate = (data["monthlyPassRate"] as? Number)?.toDouble() ?: 350.0,
            ownerPin = (data["ownerPin"] as? String) ?: "1234",
            lowLightThreshold = (data["lowLightThreshold"] as? Number)?.toDouble() ?: 45.0
        )
    }
}
