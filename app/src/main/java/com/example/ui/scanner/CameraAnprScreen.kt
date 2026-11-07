package com.example.ui.scanner

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.RectF
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.ExitBillCalculation
import com.example.data.local.PassType
import com.example.data.local.PaymentStatus
import com.example.data.local.VehicleLogEntity
import com.example.data.local.VehicleStatus
import com.example.data.repository.BillingRepository
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
import com.example.ui.theme.AsphaltObsidian
import com.example.ui.theme.CardSlateBorder
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.CashEmeraldDim
import com.example.ui.theme.DebtCrimson
import com.example.ui.theme.DebtCrimsonDim
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.SafetyAmberDim
import com.example.ui.theme.UpiCyan
import com.example.ui.theme.UpiCyanDim
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.ParkingViewModel
import com.example.ui.viewmodel.ScanMode
import com.example.vision.AnprFrameAnalyzer
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

private val LOCAL_RTO_PREFIXES = listOf("TN39", "TN42", "TN33", "TN37", "TN38", "TN66", "TN86", "KA01")

@Composable
fun CameraAnprScreen(
    viewModel: ParkingViewModel
) {
    val context = LocalContext.current
    val lang = LocalAppLanguage.current
    val scanMode by viewModel.scanMode.collectAsStateWithLifecycle()
    val plateInput by viewModel.plateInput.collectAsStateWithLifecycle()
    val inspection by viewModel.plateInspection.collectAsStateWithLifecycle()
    val currentLuminosity by viewModel.currentLuminosity.collectAsStateWithLifecycle()
    val isTorchOn by viewModel.isTorchOn.collectAsStateWithLifecycle()
    val autoTorchEnabled by viewModel.autoTorchEnabled.collectAsStateWithLifecycle()
    val rateConfig by viewModel.rateConfig.collectAsStateWithLifecycle()
    val lastDetection by viewModel.lastDetectionResult.collectAsStateWithLifecycle()
    val visionState by viewModel.visionAnalysisState.collectAsStateWithLifecycle()
    val fastRtoState by viewModel.fastRtoHintState.collectAsStateWithLifecycle()
    val activeExitLog by viewModel.activeExitDialogLog.collectAsStateWithLifecycle()
    val disputeState by viewModel.disputeSummaryState.collectAsStateWithLifecycle()
    val logs by viewModel.vehicleLogs.collectAsStateWithLifecycle()
    val passes by viewModel.passes.collectAsStateWithLifecycle()
    val credits by viewModel.credits.collectAsStateWithLifecycle()

    val insideVehicles = remember(logs) { logs.filter { it.status == VehicleStatus.INSIDE } }
    val modeAccentColor = if (scanMode == ScanMode.ENTRY) SafetyAmber else CashEmerald

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    var showIssuePassDialog by remember { mutableStateOf(false) }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bmp = BitmapFactory.decodeStream(stream)
                    if (bmp != null) {
                        viewModel.analyzePlateImageWithGemini(bmp)
                    }
                }
            } catch (_: Exception) {
            }
        }
    }

    val takePicturePreviewLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicturePreview()
    ) { bmp ->
        if (bmp != null) {
            viewModel.analyzePlateImageWithGemini(bmp)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 1. Clean Single-Row Segmented Entry / Exit Scan Mode Toggle
        ScanModeToggleHeader(
            scanMode = scanMode,
            insideCount = insideVehicles.size,
            onSelectMode = { viewModel.setScanMode(it) },
            onToggleMode = { viewModel.toggleScanMode() }
        )

        // 2. Live CameraX Viewfinder + Minimal Floating Controls
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, modeAccentColor.copy(alpha = 0.45f)),
            colors = CardDefaults.cardColors(containerColor = ElevatedSlate)
        ) {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp)
                        .background(Color.Black)
                ) {
                    if (hasCameraPermission) {
                        CameraPreviewWithAnpr(
                            isTorchEnabled = isTorchOn,
                            lowLightThresholdProvider = { rateConfig.lowLightThreshold },
                            onLuminosityMeasured = { luma, shouldAutoEnable ->
                                viewModel.onLuminosityUpdated(luma, shouldAutoEnable)
                            },
                            onPlateDetected = { result ->
                                viewModel.onLiveAnprPlateDetected(result)
                            }
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = "Camera permission required",
                                tint = modeAccentColor,
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = tr(
                                    lang,
                                    "Allow camera for live plate scan & entry photo proof",
                                    "வாகன எண்ணை ஸ்கேன் செய்ய கேமரா அனுமதி தேவை"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = modeAccentColor,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.testTag("grant_camera_permission_button")
                            ) {
                                Text(tr(lang, "Enable Camera", "கேமராவை இயக்கு"), style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }

                    // Bounding-Box Reticle Overlay
                    AnprBoundingBoxOverlay(
                        normalizedBox = lastDetection?.boundingBoxNormalized
                            ?: RectF(0.14f, 0.30f, 0.86f, 0.70f),
                        detectedPlate = lastDetection?.normalizedPlate,
                        accentColor = modeAccentColor
                    )

                    // Minimal Floating Top Overlay Pills
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left Floating Pill: Scan Mode + Ambient Light
                        Surface(
                            color = Color.Black.copy(alpha = 0.65f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .background(modeAccentColor, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (currentLuminosity < rateConfig.lowLightThreshold) {
                                        tr(lang, "ANPR • Low Light", "ANPR • குறைந்த வெளிச்சம்")
                                    } else {
                                        tr(lang, "Live ANPR", "நேரடி ANPR")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                        }

                        // Right Floating Controls: Compact Auto-Torch Chip + Manual Torch Button
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                color = if (autoTorchEnabled) {
                                    modeAccentColor.copy(alpha = 0.22f)
                                } else {
                                    Color.Black.copy(alpha = 0.65f)
                                },
                                shape = RoundedCornerShape(20.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (autoTorchEnabled) modeAccentColor else Color.White.copy(alpha = 0.2f)
                                ),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .clickable { viewModel.setAutoTorchEnabled(!autoTorchEnabled) }
                                    .testTag("auto_torch_switch")
                            ) {
                                Text(
                                    text = if (autoTorchEnabled) {
                                        tr(lang, "Auto Light: ON", "தானியங்கி: ஆன்")
                                    } else {
                                        tr(lang, "Auto Light: OFF", "தானியங்கி: ஆஃப்")
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (autoTorchEnabled) modeAccentColor else MutedSteel,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            IconButton(
                                onClick = { viewModel.toggleTorchManual() },
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        if (isTorchOn) SafetyAmber else Color.Black.copy(alpha = 0.65f),
                                        shape = CircleShape
                                    )
                                    .border(
                                        1.dp,
                                        if (isTorchOn) SafetyAmber else Color.White.copy(alpha = 0.2f),
                                        CircleShape
                                    )
                                    .testTag("manual_torch_toggle_button")
                            ) {
                                Icon(
                                    imageVector = if (isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                    contentDescription = "Toggle Flashlight Torch",
                                    tint = if (isTorchOn) Color.Black else Color.White,
                                    modifier = Modifier.size(17.dp)
                                )
                            }
                        }
                    }
                }

                // Clean Action Strip for Photo OCR & AI Vision Fallback
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("upload_plate_photo_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardSlateBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Analyze plate photo from gallery",
                            modifier = Modifier.size(16.dp),
                            tint = SafetyAmber
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr(lang, "Photo OCR", "போட்டோ ஸ்கேன்"), style = MaterialTheme.typography.labelLarge)
                    }

                    OutlinedButton(
                        onClick = { takePicturePreviewLauncher.launch(null) },
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .testTag("gemini_vision_snap_button"),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardSlateBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Vision Plate Scan",
                            modifier = Modifier.size(16.dp),
                            tint = UpiCyan
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr(lang, "AI Camera", "AI கேமரா"), style = MaterialTheme.typography.labelLarge)
                    }

                    IconButton(
                        onClick = { viewModel.runFastPlateLookup(plateInput) },
                        modifier = Modifier
                            .size(42.dp)
                            .border(1.dp, CardSlateBorder, RoundedCornerShape(10.dp))
                            .testTag("fast_rto_lookup_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Bolt,
                            contentDescription = "Fast RTO Lookup",
                            tint = SafetyAmber,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                // AI Vision or Fast Lookup Output
                AnimatedVisibility(visible = visionState !is AiTaskState.Idle || fastRtoState !is AiTaskState.Idle) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        when (val vState = visionState) {
                            is AiTaskState.Loading -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(14.dp),
                                        color = SafetyAmber,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = tr(lang, "Analyzing vehicle plate...", "வாகன எண்ணை ஆய்வு செய்கிறது..."),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SafetyAmber
                                    )
                                }
                            }
                            is AiTaskState.Success -> {
                                Surface(
                                    color = AsphaltObsidian,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, UpiCyan.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = vState.resultText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            is AiTaskState.Error -> {
                                Text(
                                    text = vState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DebtCrimson
                                )
                            }
                            else -> {}
                        }

                        when (val fState = fastRtoState) {
                            is AiTaskState.Loading -> {
                                Text(
                                    text = tr(lang, "Checking RTO region...", "RTO விவரம் தேடுகிறது..."),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
                            is AiTaskState.Success -> {
                                Surface(
                                    color = AsphaltObsidian,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.4f))
                                ) {
                                    Text(
                                        text = fState.resultText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(10.dp)
                                    )
                                }
                            }
                            is AiTaskState.Error -> {
                                Text(
                                    text = fState.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DebtCrimson
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }
        }

        // 3. AUTO-DEBT ALERT BANNER ("PREVIOUS PENDING BALANCE")
        val pendingCredit = inspection.unpaidCredit
        if (pendingCredit != null && !pendingCredit.isSettled && pendingCredit.pendingAmount > 0.0) {
            val dateStr = remember(pendingCredit.lastUnpaidTimestamp) {
                SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                    .format(Date(pendingCredit.lastUnpaidTimestamp))
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("previous_pending_balance_banner"),
                colors = CardDefaults.cardColors(containerColor = DebtCrimsonDim),
                border = BorderStroke(1.dp, DebtCrimson),
                shape = RoundedCornerShape(14.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Pending credit warning",
                        tint = DebtCrimson,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(
                                lang,
                                "PREVIOUS PENDING BALANCE: ₹${pendingCredit.pendingAmount.toInt()} from $dateStr",
                                "முந்தைய நிலுவை பாக்கி: ₹${pendingCredit.pendingAmount.toInt()} ($dateStr)"
                            ),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White
                        )
                        Text(
                            text = tr(
                                lang,
                                "Added automatically to ${pendingCredit.plateNumber}'s bill.",
                                "${pendingCredit.plateNumber} தற்போதைய கட்டணத்துடன் சேர்க்கப்பட்டது."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFFFFB4A1)
                        )
                    }
                }
            }
        }

        // 4. ACTIVE WEEKLY / MONTHLY PASS BANNER
        val passEntity = inspection.passEntity
        if (passEntity != null) {
            if (inspection.isPassActive) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("valid_pass_banner"),
                    colors = CardDefaults.cardColors(containerColor = CashEmeraldDim),
                    border = BorderStroke(1.dp, CashEmerald),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Verified,
                            contentDescription = "Valid Pass",
                            tint = CashEmerald,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(
                                    lang,
                                    "VALID PASS - ${inspection.passDaysLeft} DAYS LEFT",
                                    "பாஸ் செல்லுபடியாகும் - ${inspection.passDaysLeft} நாட்கள் உள்ளன"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = CashEmerald
                            )
                            Text(
                                text = tr(
                                    lang,
                                    "${passEntity.passType.replace("_", " ")} • ₹0 daily fee",
                                    "${passEntity.passType.replace("_", " ")} • ₹0 தினசரி கட்டணம்"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expired_pass_banner"),
                    colors = CardDefaults.cardColors(containerColor = SafetyAmberDim),
                    border = BorderStroke(1.dp, SafetyAmber),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(
                                    lang,
                                    "Pass expired for ${passEntity.plateNumber}",
                                    "${passEntity.plateNumber} பாஸ் காலாவதியானது"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = SafetyAmber
                            )
                            Text(
                                text = tr(
                                    lang,
                                    "₹${rateConfig.dailyRate.toInt()}/24h rate applies",
                                    "₹${rateConfig.dailyRate.toInt()}/நாள் கட்டணம் பொருந்தும்"
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White
                            )
                        }
                        Button(
                            onClick = { showIssuePassDialog = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafetyAmber,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.testTag("renew_pass_button")
                        ) {
                            Text(tr(lang, "Renew", "புதுப்பி"), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }

        // 5. CLEAN PLATE INPUT + QUICK RTO BAR + PRIMARY ACTION CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardSlateBorder),
            colors = CardDefaults.cardColors(containerColor = ElevatedSlate)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (scanMode == ScanMode.ENTRY) {
                            tr(lang, "License Plate", "வாகன எண் (உள்ளே)")
                        } else {
                            tr(lang, "License Plate (Exit)", "வாகன எண் (வெளியே)")
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    if (inspection.isValidFormat) {
                        Text(
                            text = AnprFrameAnalyzer.lookupRtoDistrict(plateInput),
                            style = MaterialTheme.typography.labelMedium,
                            color = CashEmerald
                        )
                    }
                }

                // In EXIT Mode, show currently parked bikes as one-tap chips right inside the card
                AnimatedVisibility(visible = scanMode == ScanMode.EXIT && insideVehicles.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = tr(
                                lang,
                                "Parked Inside (${insideVehicles.size}) — Tap to select:",
                                "உள்ளே உள்ளவை (${insideVehicles.size}) — தொடவும்:"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = CashEmerald
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            insideVehicles.forEach { parked ->
                                val isSelected = plateInput == parked.plateNumber
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { viewModel.onPlateInputChanged(parked.plateNumber) },
                                    label = {
                                        Text(
                                            text = parked.plateNumber,
                                            fontFamily = JetBrainsMonoFontFamily,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = CashEmerald,
                                        selectedLabelColor = Color.Black,
                                        containerColor = AsphaltObsidian,
                                        labelColor = Color.White
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = CashEmerald.copy(alpha = 0.4f)
                                    ),
                                    modifier = Modifier.testTag("quick_exit_chip_${parked.plateNumber}")
                                )
                            }
                        }
                    }
                }

                // Quick-Prefix Bar for Local RTO Prefixes
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    LOCAL_RTO_PREFIXES.forEach { prefix ->
                        FilterChip(
                            selected = plateInput.startsWith(prefix),
                            onClick = { viewModel.appendRtoPrefix(prefix) },
                            label = {
                                Text(
                                    text = prefix,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = modeAccentColor,
                                selectedLabelColor = Color.Black,
                                containerColor = AsphaltObsidian,
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = plateInput.startsWith(prefix),
                                borderColor = CardSlateBorder
                            ),
                            modifier = Modifier
                                .height(38.dp)
                                .testTag("rto_prefix_chip_$prefix")
                        )
                    }
                }

                // Clean Monospace Plate Input Field
                OutlinedTextField(
                    value = plateInput,
                    onValueChange = { viewModel.onPlateInputChanged(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("plate_number_input"),
                    textStyle = MaterialTheme.typography.displayMedium.copy(
                        color = Color.White,
                        fontFamily = JetBrainsMonoFontFamily
                    ),
                    placeholder = {
                        Text(
                            text = "TN39AB1234",
                            style = MaterialTheme.typography.displayMedium,
                            color = MutedSteel.copy(alpha = 0.35f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters
                    ),
                    leadingIcon = {
                        Surface(
                            color = Color(0xFF1E3A8A),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.padding(start = 10.dp)
                        ) {
                            Text(
                                text = "IND",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                            )
                        }
                    },
                    trailingIcon = {
                        if (plateInput.isNotEmpty()) {
                            IconButton(
                                onClick = { viewModel.onPlateInputChanged("") },
                                modifier = Modifier.testTag("clear_plate_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear plate input",
                                    tint = MutedSteel
                                )
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = modeAccentColor,
                        unfocusedBorderColor = CardSlateBorder,
                        focusedContainerColor = AsphaltObsidian,
                        unfocusedContainerColor = AsphaltObsidian
                    ),
                    shape = RoundedCornerShape(12.dp)
                )

                val activeInside = inspection.activeInsideLog
                val exitPreview = inspection.exitBillPreview

                // Mode-Specific Status & Fee Preview Card
                if (scanMode == ScanMode.EXIT) {
                    if (activeInside != null && exitPreview != null) {
                        InlineExitSettlementCard(
                            activeLog = activeInside,
                            bill = exitPreview,
                            onSettleCash = {
                                viewModel.completeVehicleExit(
                                    logId = activeInside.id,
                                    paymentChoice = PaymentStatus.PAID_CASH
                                )
                            },
                            onSettleUpi = {
                                viewModel.completeVehicleExit(
                                    logId = activeInside.id,
                                    paymentChoice = PaymentStatus.PAID_UPI
                                )
                            },
                            onSettleCredit = {
                                viewModel.completeVehicleExit(
                                    logId = activeInside.id,
                                    paymentChoice = PaymentStatus.UNPAID_CREDIT
                                )
                            },
                            onOpenFullSheet = {
                                viewModel.openExitSheetForVehicle(activeInside)
                            }
                        )
                    } else if (plateInput.length >= 4) {
                        Surface(
                            color = AsphaltObsidian,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CardSlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tr(
                                        lang,
                                        "$plateInput is not parked inside",
                                        "$plateInput உள்ளே பதிவாகவில்லை"
                                    ),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.setScanMode(ScanMode.ENTRY) },
                                    border = BorderStroke(1.dp, SafetyAmber),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SafetyAmber)
                                ) {
                                    Text(tr(lang, "Entry Mode", "உள்ளே மாற்று"), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                } else {
                    // ENTRY MODE: If scanned plate already has an active record, prompt to switch to Exit mode
                    if (activeInside != null && exitPreview != null) {
                        Surface(
                            color = AsphaltObsidian,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.6f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = tr(
                                            lang,
                                            "${activeInside.plateNumber} is already Inside",
                                            "${activeInside.plateNumber} ஏற்கனவே உள்ளே உள்ளது"
                                        ),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = SafetyAmber
                                    )
                                    Text(
                                        text = tr(
                                            lang,
                                            "Fee due: ₹${exitPreview.totalAmountDue.toInt()} (${exitPreview.daysCharged}d)",
                                            "கட்டணம்: ₹${exitPreview.totalAmountDue.toInt()} (${exitPreview.daysCharged} நாள்)"
                                        ),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MutedSteel
                                    )
                                }
                                Button(
                                    onClick = { viewModel.setScanMode(ScanMode.EXIT) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CashEmerald,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text(tr(lang, "Exit", "வெளியே"), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }

                // Primary Action Row Driven Strictly by Entry / Exit Toggle State
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (scanMode == ScanMode.EXIT) {
                        Button(
                            onClick = { viewModel.processPlateForCurrentMode(context) },
                            enabled = plateInput.length >= 4,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("process_exit_primary_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CashEmerald,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "Search Active Record and Calculate Exit Fee",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            val labelText = if (activeInside != null && exitPreview != null) {
                                tr(
                                    lang,
                                    "Bill & Exit • ₹${exitPreview.totalAmountDue.toInt()}",
                                    "பில் & வெளியேற்று • ₹${exitPreview.totalAmountDue.toInt()}"
                                )
                            } else {
                                tr(lang, "Find & Calculate Fee", "தேடு & கட்டணம் கணக்கிடு")
                            }
                            Text(
                                text = labelText,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    } else {
                        Button(
                            onClick = { viewModel.processPlateForCurrentMode(context) },
                            enabled = plateInput.length >= 4,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp)
                                .testTag("log_entry_primary_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SafetyAmber,
                                contentColor = Color.Black
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.TwoWheeler,
                                contentDescription = "Create New Entry Record",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr(lang, "Log Vehicle Entry", "உள்ளே பதிவு செய்"),
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { showIssuePassDialog = true },
                        enabled = plateInput.length >= 4,
                        modifier = Modifier
                            .height(52.dp)
                            .testTag("open_pass_dialog_button"),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardSlateBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CreditCard,
                            contentDescription = "Issue Pass",
                            modifier = Modifier.size(16.dp),
                            tint = SafetyAmber
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(tr(lang, "Pass", "பாஸ்"), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    // 6. EXIT BILLING & PAYMENT SETTLEMENT MODAL
    val exitLog = activeExitLog
    if (exitLog != null) {
        val activePass = passes.firstOrNull { it.plateNumber == exitLog.plateNumber }
        val unpaidCredit = credits.firstOrNull { it.plateNumber == exitLog.plateNumber }
        val exitBill = remember(exitLog, rateConfig, activePass, unpaidCredit) {
            BillingRepository.calculateExitBill(
                activeLog = exitLog,
                exitTimestamp = System.currentTimeMillis(),
                rateConfig = rateConfig,
                activePass = activePass,
                unpaidCredit = unpaidCredit
            )
        }
        ExitBillingDialog(
            log = exitLog,
            bill = exitBill,
            disputeState = disputeState,
            onRequestAiReceiptExplanation = { viewModel.generateDisputeExplanation(exitLog) },
            onSelectPayment = { paymentMode ->
                viewModel.completeVehicleExit(
                    logId = exitLog.id,
                    paymentChoice = paymentMode
                )
            },
            onDismiss = { viewModel.dismissExitSheet() }
        )
    }

    // 7. WEEKLY / MONTHLY PASS REGISTRATION DIALOG
    if (showIssuePassDialog) {
        IssuePassDialog(
            initialPlate = plateInput,
            weeklyRate = rateConfig.weeklyPassRate,
            monthlyRate = rateConfig.monthlyPassRate,
            onConfirmPass = { plate, passType, paymentMode ->
                viewModel.issueOrRenewPass(plate, passType, paymentMode)
                showIssuePassDialog = false
            },
            onDismiss = { showIssuePassDialog = false }
        )
    }
}

@Composable
private fun ScanModeToggleHeader(
    scanMode: ScanMode,
    insideCount: Int,
    onSelectMode: (ScanMode) -> Unit,
    onToggleMode: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val isEntry = scanMode == ScanMode.ENTRY
    Surface(
        color = ElevatedSlate,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            1.dp,
            if (isEntry) SafetyAmber.copy(alpha = 0.45f) else CashEmerald.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Surface(
                color = if (isEntry) SafetyAmber else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectMode(ScanMode.ENTRY) }
                    .testTag("scan_mode_entry_tab")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Login,
                        contentDescription = "Entry Scan Mode",
                        tint = if (isEntry) Color.Black else MutedSteel,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr(lang, "Entry (In)", "உள்ளே (In)"),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isEntry) Color.Black else MutedSteel
                    )
                }
            }

            Surface(
                color = if (!isEntry) CashEmerald else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onSelectMode(ScanMode.EXIT) }
                    .testTag("scan_mode_exit_tab")
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Exit Scan Mode",
                        tint = if (!isEntry) Color.Black else MutedSteel,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = tr(lang, "Exit ($insideCount)", "வெளியே ($insideCount)"),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (!isEntry) Color.Black else MutedSteel
                    )
                }
            }

            IconButton(
                onClick = onToggleMode,
                modifier = Modifier
                    .size(44.dp)
                    .background(AsphaltObsidian, RoundedCornerShape(10.dp))
                    .testTag("scan_mode_toggle_button")
            ) {
                Icon(
                    imageVector = Icons.Default.SwapHoriz,
                    contentDescription = "Toggle Entry or Exit Mode",
                    tint = if (isEntry) SafetyAmber else CashEmerald,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun InlineExitSettlementCard(
    activeLog: VehicleLogEntity,
    bill: ExitBillCalculation,
    onSettleCash: () -> Unit,
    onSettleUpi: () -> Unit,
    onSettleCredit: () -> Unit,
    onOpenFullSheet: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val timeFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH) }
    Surface(
        color = AsphaltObsidian,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, CashEmerald.copy(alpha = 0.6f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("inline_exit_settlement_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${tr(lang, "Parked since", "உள்ளே வந்த நேரம்:")} ${timeFmt.format(Date(activeLog.entryTimestamp))}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                    Text(
                        text = "${bill.daysCharged}${tr(lang, "d stay", " நாள்")} • " +
                            if (bill.isCoveredByPass) {
                                tr(lang, "Pass covered", "பாஸ் உள்ளது")
                            } else {
                                "₹${bill.baseParkingFee.toInt()} ${tr(lang, "base", "கட்டணம்")}"
                            } +
                            if (bill.previousPendingCredit > 0) {
                                " + ₹${bill.previousPendingCredit.toInt()} ${tr(lang, "debt", "பாக்கி")}"
                            } else {
                                ""
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = tr(lang, "Total Due", "மொத்த கட்டணம்"),
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSteel
                    )
                    Text(
                        text = "₹${bill.totalAmountDue.toInt()}",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = CashEmerald
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Button(
                    onClick = onSettleCash,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("inline_exit_cash_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CashEmerald,
                        contentColor = Color.Black
                    )
                ) {
                    Text(
                        text = "${tr(lang, "Cash", "ரொக்கம்")} ₹${bill.totalAmountDue.toInt()}",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                OutlinedButton(
                    onClick = onSettleUpi,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("inline_exit_upi_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, UpiCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UpiCyan)
                ) {
                    Text("UPI", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onSettleCredit,
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .testTag("inline_exit_credit_button"),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, DebtCrimson),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DebtCrimson)
                ) {
                    Text(tr(lang, "Credit", "கடன்"), style = MaterialTheme.typography.labelLarge)
                }
                IconButton(
                    onClick = onOpenFullSheet,
                    modifier = Modifier
                        .size(42.dp)
                        .border(1.dp, CardSlateBorder, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.ReceiptLong,
                        contentDescription = "Full Exit Receipt",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPreviewWithAnpr(
    isTorchEnabled: Boolean,
    lowLightThresholdProvider: () -> Double,
    onLuminosityMeasured: (Double, Boolean) -> Unit,
    onPlateDetected: (com.example.vision.AnprDetectionResult) -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    LaunchedEffect(isTorchEnabled, activeCamera) {
        try {
            activeCamera?.cameraControl?.enableTorch(isTorchEnabled)
        } catch (_: Exception) {
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraExecutor.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
            }
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            analysis.setAnalyzer(
                                cameraExecutor,
                                AnprFrameAnalyzer(
                                    context = ctx,
                                    getLowLightThreshold = lowLightThresholdProvider,
                                    onLuminosityMeasured = onLuminosityMeasured,
                                    onPlateDetected = onPlateDetected
                                )
                            )
                        }

                    cameraProvider.unbindAll()
                    val camera = cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        imageAnalysis
                    )
                    activeCamera = camera
                } catch (_: Exception) {
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        }
    )
}

@Composable
private fun AnprBoundingBoxOverlay(
    normalizedBox: RectF,
    detectedPlate: String?,
    accentColor: Color
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val left = (normalizedBox.left.coerceIn(0.05f, 0.85f)) * size.width
            val top = (normalizedBox.top.coerceIn(0.15f, 0.80f)) * size.height
            val right = (normalizedBox.right.coerceIn(left / size.width + 0.1f, 0.95f)) * size.width
            val bottom = (normalizedBox.bottom.coerceIn(top / size.height + 0.1f, 0.90f)) * size.height

            val boxColor = if (!detectedPlate.isNullOrBlank()) CashEmerald else accentColor
            drawRoundRect(
                color = boxColor,
                topLeft = Offset(left, top),
                size = Size(right - left, bottom - top),
                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                style = Stroke(width = 2.dp.toPx())
            )
        }

        if (!detectedPlate.isNullOrBlank()) {
            Surface(
                color = CashEmerald,
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp)
            ) {
                Text(
                    text = detectedPlate,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoFontFamily,
                        fontSize = 13.sp
                    ),
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ExitBillingDialog(
    log: VehicleLogEntity,
    bill: ExitBillCalculation,
    disputeState: AiTaskState,
    onRequestAiReceiptExplanation: () -> Unit,
    onSelectPayment: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    BackHandler { onDismiss() }
    val dateFormat = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH) }
    val entryFormatted = remember(bill.entryTimestamp) { dateFormat.format(Date(bill.entryTimestamp)) }
    val exitFormatted = remember(bill.exitTimestamp) { dateFormat.format(Date(bill.exitTimestamp)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElevatedSlate,
        titleContentColor = Color.White,
        textContentColor = Color.White,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "${tr(lang, "Exit Billing", "வெளியேறும் பில்")} • ${bill.plateNumber}",
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White
                    )
                    Text(
                        text = tr(lang, "24-hour rolling duration audit", "24 மணி நேர கணக்கீடு"),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close dialog", tint = MutedSteel)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (bill.photoPath.isNotBlank() && File(bill.photoPath).exists()) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardSlateBorder)
                    ) {
                        AsyncImage(
                            model = File(bill.photoPath),
                            contentDescription = "Watermarked vehicle entry photo proof",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(145.dp),
                            contentScale = ContentScale.Crop
                        )
                    }
                }

                if (bill.previousPendingCredit > 0.0) {
                    Surface(
                        color = DebtCrimsonDim,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, DebtCrimson),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = tr(
                                    lang,
                                    "Previous Pending Balance: ₹${bill.previousPendingCredit.toInt()}",
                                    "முந்தைய நிலுவை பாக்கி: ₹${bill.previousPendingCredit.toInt()}"
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = tr(
                                    lang,
                                    "Included in total due below.",
                                    "கீழே உள்ள மொத்த கட்டணத்தில் சேர்க்கப்பட்டுள்ளது."
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFFFB4A1)
                            )
                        }
                    }
                }

                Surface(
                    color = AsphaltObsidian,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, CardSlateBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BillingRow(tr(lang, "Entry", "உள்ளே வந்த நேரம்"), entryFormatted)
                        BillingRow(tr(lang, "Exit", "வெளியேறும் நேரம்"), exitFormatted)
                        BillingRow(
                            tr(lang, "Duration Charged", "மொத்த நாட்கள்"),
                            "${bill.daysCharged} ${tr(lang, "day(s)", "நாள்")}"
                        )
                        BillingRow(
                            tr(lang, "Stay Fee", "பார்க்கிங் கட்டணம்"),
                            if (bill.isCoveredByPass) {
                                tr(
                                    lang,
                                    "₹0 (Pass • ${bill.passDaysRemaining}d left)",
                                    "₹0 (பாஸ் • ${bill.passDaysRemaining} நாட்கள் உள்ளன)"
                                )
                            } else {
                                "₹${bill.baseParkingFee.toInt()} (${bill.daysCharged} × ₹${bill.dailyRateUsed.toInt()})"
                            }
                        )
                        if (bill.previousPendingCredit > 0.0) {
                            BillingRow(
                                tr(lang, "Previous Debt", "பழைய பாக்கி"),
                                "+ ₹${bill.previousPendingCredit.toInt()}",
                                valueColor = DebtCrimson
                            )
                        }
                        HorizontalDivider(color = CardSlateBorder, modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr(lang, "Total Due", "மொத்த கட்டணம்"),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "₹${bill.totalAmountDue.toInt()}",
                                style = MaterialTheme.typography.displayMedium,
                                color = CashEmerald
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = onRequestAiReceiptExplanation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("generate_dispute_receipt_button"),
                    border = BorderStroke(1.dp, CardSlateBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SafetyAmber)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Generate customer dispute explanation",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        tr(lang, "Explain Bill for Customer", "வாடிக்கையாளருக்கு கட்டண விளக்கம்"),
                        style = MaterialTheme.typography.labelLarge
                    )
                }

                when (disputeState) {
                    is AiTaskState.Loading -> {
                        Text(
                            text = tr(lang, "Preparing receipt breakdown...", "கட்டண விவரம் தயாராகிறது..."),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                    is AiTaskState.Success -> {
                        Surface(
                            color = AsphaltObsidian,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, CardSlateBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = disputeState.resultText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                    is AiTaskState.Error -> {
                        Text(
                            text = disputeState.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = DebtCrimson
                        )
                    }
                    else -> {}
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = tr(lang, "Payment method", "பணம் செலுத்தும் முறை"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MutedSteel
                )

                Button(
                    onClick = { onSelectPayment(PaymentStatus.PAID_CASH) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("exit_paid_cash_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CashEmerald,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = tr(
                            lang,
                            "Paid Cash • ₹${bill.totalAmountDue.toInt()}",
                            "ரொக்கம் (Cash) • ₹${bill.totalAmountDue.toInt()}"
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Button(
                    onClick = { onSelectPayment(PaymentStatus.PAID_UPI) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("exit_paid_upi_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = UpiCyan,
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = "UPI / GPay • ₹${bill.totalAmountDue.toInt()}",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                OutlinedButton(
                    onClick = { onSelectPayment(PaymentStatus.UNPAID_CREDIT) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("exit_pay_tomorrow_button"),
                    border = BorderStroke(1.dp, DebtCrimson),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = DebtCrimson),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = tr(
                            lang,
                            "Pay Tomorrow (Credit • ₹${bill.totalAmountDue.toInt()})",
                            "நாளை தருகிறேன் (கடன் • ₹${bill.totalAmountDue.toInt()})"
                        ),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun BillingRow(
    label: String,
    value: String,
    valueColor: Color = Color.White
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = valueColor
        )
    }
}

@Composable
fun IssuePassDialog(
    initialPlate: String,
    weeklyRate: Double,
    monthlyRate: Double,
    onConfirmPass: (plate: String, passType: String, paymentMode: String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    BackHandler { onDismiss() }
    var plate by remember { mutableStateOf(initialPlate) }
    var selectedPassType by remember { mutableStateOf(PassType.WEEKLY_PASS) }
    var selectedPaymentMode by remember { mutableStateOf(PaymentStatus.PAID_CASH) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElevatedSlate,
        title = {
            Text(
                text = tr(lang, "Vehicle Pass", "வாகன பாஸ் (Pass)"),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = plate,
                    onValueChange = { plate = it.uppercase().replace(Regex("[^A-Z0-9]"), "").take(12) },
                    label = { Text(tr(lang, "License Plate", "வாகன எண்")) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("pass_dialog_plate_input")
                )

                Text(
                    tr(lang, "Pass Duration", "பாஸ் காலம்"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MutedSteel
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPassType == PassType.WEEKLY_PASS,
                        onClick = { selectedPassType = PassType.WEEKLY_PASS },
                        label = {
                            Text(
                                tr(
                                    lang,
                                    "Weekly (₹${weeklyRate.toInt()})",
                                    "வாரம் (₹${weeklyRate.toInt()})"
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedPassType == PassType.MONTHLY_PASS,
                        onClick = { selectedPassType = PassType.MONTHLY_PASS },
                        label = {
                            Text(
                                tr(
                                    lang,
                                    "Monthly (₹${monthlyRate.toInt()})",
                                    "மாதம் (₹${monthlyRate.toInt()})"
                                )
                            )
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Text(
                    tr(lang, "Payment Mode", "பணம் செலுத்தும் முறை"),
                    style = MaterialTheme.typography.labelLarge,
                    color = MutedSteel
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedPaymentMode == PaymentStatus.PAID_CASH,
                        onClick = { selectedPaymentMode = PaymentStatus.PAID_CASH },
                        label = { Text(tr(lang, "Cash", "ரொக்கம்")) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = selectedPaymentMode == PaymentStatus.PAID_UPI,
                        onClick = { selectedPaymentMode = PaymentStatus.PAID_UPI },
                        label = { Text("UPI") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmPass(plate, selectedPassType, selectedPaymentMode) },
                enabled = plate.length >= 4,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CashEmerald,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("confirm_issue_pass_button")
            ) {
                Text(tr(lang, "Activate Pass", "பாஸ் செயல்படுத்து"), style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(tr(lang, "Cancel", "ரத்து"))
            }
        }
    )
}
