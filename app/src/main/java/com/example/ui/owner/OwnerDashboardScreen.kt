package com.example.ui.owner

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.CreditEntity
import com.example.data.local.PaymentStatus
import com.example.data.local.RateConfig
import com.example.data.local.SyncStatus
import com.example.data.local.VehicleLogEntity
import com.example.data.local.VehicleStatus
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
import com.example.ui.theme.UpiCyan
import com.example.ui.viewmodel.AiTaskState
import com.example.ui.viewmodel.AppRole
import com.example.ui.viewmodel.ParkingViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class OwnerSection {
    LEDGER,
    AUDIT_REPORTS
}

@Composable
fun OwnerDashboardScreen(
    viewModel: ParkingViewModel,
    initialSection: OwnerSection = OwnerSection.LEDGER
) {
    val lang = LocalAppLanguage.current
    val summary by viewModel.shiftAuditSummary.collectAsStateWithLifecycle()
    val logs by viewModel.vehicleLogs.collectAsStateWithLifecycle()
    val credits by viewModel.credits.collectAsStateWithLifecycle()
    val rateConfig by viewModel.rateConfig.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val deepAuditState by viewModel.ownerDeepAuditState.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSyncingQueue by viewModel.isSyncingQueue.collectAsStateWithLifecycle()

    var activeSection by remember { mutableStateOf(initialSection) }
    LaunchedEffect(initialSection) {
        activeSection = initialSection
    }

    var selectedFilter by remember { mutableStateOf("ALL") }
    var showRateSettingsDialog by remember { mutableStateOf(false) }
    var selectedPhotoPreviewLog by remember { mutableStateOf<VehicleLogEntity?>(null) }

    val filteredLogs = remember(logs, selectedFilter) {
        when (selectedFilter) {
            "INSIDE" -> logs.filter { it.status == VehicleStatus.INSIDE }
            "CASH" -> logs.filter { it.paymentStatus == PaymentStatus.PAID_CASH }
            "UPI" -> logs.filter { it.paymentStatus == PaymentStatus.PAID_UPI }
            "CREDIT" -> logs.filter { it.paymentStatus == PaymentStatus.UNPAID_CREDIT }
            else -> logs
        }
    }

    val openCredits = remember(credits) {
        credits.filter { !it.isSettled && it.pendingAmount > 0.0 }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Offline Sync Queue Banner (visible when offline or when items are queued in Room)
        if (!isOnline || pendingSyncCount > 0) {
            item {
                Surface(
                    color = ElevatedSlate,
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(
                        1.dp,
                        if (pendingSyncCount > 0) SafetyAmber.copy(alpha = 0.7f) else DebtCrimson.copy(alpha = 0.7f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("owner_offline_sync_queue_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!isOnline) {
                                    "Offline Mode • Room Sync Queue Active"
                                } else {
                                    "Sync Queue: $pendingSyncCount record(s) pending upload"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (pendingSyncCount > 0) SafetyAmber else DebtCrimson
                            )
                            Text(
                                text = "Vehicle logs are safely stored in Room and auto-upload to Firestore once connection restores.",
                                style = MaterialTheme.typography.labelMedium,
                                color = MutedSteel
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.syncOfflineQueueNow(silent = false) },
                            enabled = !isSyncingQueue,
                            border = BorderStroke(1.dp, SafetyAmber),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SafetyAmber),
                            modifier = Modifier.testTag("owner_sync_now_button")
                        ) {
                            Text(
                                text = if (isSyncingQueue) "Syncing..." else "Sync Now",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
        // Segmented Owner View Switcher (Shift Ledger vs Audit Reports)
        item {
            Surface(
                color = ElevatedSlate,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CardSlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isLedger = activeSection == OwnerSection.LEDGER
                    Surface(
                        color = if (isLedger) SafetyAmber else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { activeSection = OwnerSection.LEDGER }
                            .testTag("owner_section_ledger_tab")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountBalanceWallet,
                                contentDescription = "Shift Ledger",
                                tint = if (isLedger) Color.Black else MutedSteel,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr(lang, "Shift Ledger", "கல்லா கணக்கு"),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isLedger) Color.Black else MutedSteel
                            )
                        }
                    }

                    Surface(
                        color = if (!isLedger) SafetyAmber else Color.Transparent,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { activeSection = OwnerSection.AUDIT_REPORTS }
                            .testTag("owner_section_reports_tab")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Assessment,
                                contentDescription = "Audit Reports",
                                tint = if (!isLedger) Color.Black else MutedSteel,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr(lang, "Audit Reports (${logs.size})", "ஆய்வு அறிக்கை (${logs.size})"),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (!isLedger) Color.Black else MutedSteel
                            )
                        }
                    }
                }
            }
        }

        if (activeSection == OwnerSection.LEDGER) {
            // 1. Expected Cash-in-Drawer Hero Audit Card
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("expected_cash_in_drawer_card"),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = CashEmeraldDim),
                    border = BorderStroke(1.dp, CashEmerald)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = tr(lang, "Expected Cash in Drawer", "கல்லாவில் இருக்க வேண்டிய ரொக்கம்"),
                                    style = MaterialTheme.typography.labelLarge,
                                    color = CashEmerald
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "₹${summary.expectedCashInDrawer.toInt()}",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = Color.White
                                )
                            }
                            if (currentRole == AppRole.OWNER) {
                                OutlinedButton(
                                    onClick = { showRateSettingsDialog = true },
                                    border = BorderStroke(1.dp, SafetyAmber),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = SafetyAmber),
                                    modifier = Modifier.testTag("edit_tariff_rates_button")
                                ) {
                                    Icon(
                                        Icons.Default.Settings,
                                        contentDescription = "Edit Tariff Rates",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(tr(lang, "Tariff Rates", "கட்டண விகிதம்"), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = tr(
                                lang,
                                "Cash Exits (₹${summary.cashFromExitsToday.toInt()}) + Cash Passes (₹${summary.cashFromPassesToday.toInt()}). Excludes UPI & Credits.",
                                "ரொக்க வெளியேற்றம் (₹${summary.cashFromExitsToday.toInt()}) + ரொக்க பாஸ் (₹${summary.cashFromPassesToday.toInt()}). UPI சேர்க்கப்படவில்லை."
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }
            }

            // 2. Real-Time Shift Metrics Grid
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricAuditCard(
                        modifier = Modifier.weight(1f),
                        title = tr(lang, "Bikes Inside", "உள்ளே உள்ளவை"),
                        value = "${summary.totalBikesInside}",
                        subtitle = tr(lang, "Currently parked", "தற்போது நிற்பவை"),
                        accentColor = SafetyAmber,
                        icon = Icons.AutoMirrored.Filled.DirectionsBike
                    )
                    MetricAuditCard(
                        modifier = Modifier.weight(1f),
                        title = tr(lang, "Exits Today", "இன்று வெளியேறியவை"),
                        value = "${summary.totalExitsToday}",
                        subtitle = tr(lang, "Completed stays", "முடிந்த பதிவுகள்"),
                        accentColor = Color.White,
                        icon = Icons.Default.History
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    MetricAuditCard(
                        modifier = Modifier.weight(1f),
                        title = tr(lang, "Digital UPI Today", "இன்றைய UPI"),
                        value = "₹${summary.totalUpiToday.toInt()}",
                        subtitle = "Exits ₹${summary.upiFromExitsToday.toInt()} • Passes ₹${summary.upiFromPassesToday.toInt()}",
                        accentColor = UpiCyan,
                        icon = Icons.Default.QrCode
                    )
                    MetricAuditCard(
                        modifier = Modifier.weight(1f),
                        title = tr(lang, "Pending Credit", "நிலுவை கடன்"),
                        value = "₹${summary.totalPendingCreditAmount.toInt()}",
                        subtitle = "${summary.activeCreditAccountsCount} ${tr(lang, "unpaid vehicle(s)", "வாகனங்கள்")}",
                        accentColor = DebtCrimson,
                        icon = Icons.Default.Warning
                    )
                }
            }

            // 3. Active Tariff Summary Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
                    border = BorderStroke(1.dp, CardSlateBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Active Stand Tariffs",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White
                            )
                            Text(
                                text = "Daily: ₹${rateConfig.dailyRate.toInt()}/24h • Weekly: ₹${rateConfig.weeklyPassRate.toInt()} • Monthly: ₹${rateConfig.monthlyPassRate.toInt()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSteel
                            )
                        }
                        Text(
                            text = "${summary.activePassesCount} passes",
                            style = MaterialTheme.typography.labelLarge,
                            color = CashEmerald
                        )
                    }
                }
            }

            // 4. Active Unpaid "Pay Tomorrow" Credits Section
            item {
                Text(
                    text = "Pending 'Pay Tomorrow' Credits (${openCredits.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (openCredits.isEmpty()) MutedSteel else DebtCrimson
                )
            }

            if (openCredits.isEmpty()) {
                item {
                    Surface(
                        color = ElevatedSlate,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "All customer credit accounts are settled.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }
            } else {
                items(openCredits, key = { "credit_${it.plateNumber}" }) { credit ->
                    CreditAuditRow(
                        credit = credit,
                        onSettleCash = { viewModel.settlePendingCredit(credit, PaymentStatus.PAID_CASH) },
                        onSettleUpi = { viewModel.settlePendingCredit(credit, PaymentStatus.PAID_UPI) }
                    )
                }
            }
        } else {
            // AUDIT REPORTS SECTION: AI Fraud Audit + Chronological Shift Log with Thumbnails
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
                    border = BorderStroke(1.dp, CardSlateBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "AI Shift Fraud & Skimming Audit",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = Color.White
                                )
                                Text(
                                    text = "Checks cash-to-UPI ratios, overdue stays, and unpaid credits",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
                            Button(
                                onClick = { viewModel.runDeepOwnerAudit() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SafetyAmber,
                                    contentColor = Color.Black
                                ),
                                modifier = Modifier.testTag("run_deep_owner_audit_button")
                            ) {
                                Icon(
                                    Icons.Default.AutoAwesome,
                                    contentDescription = "Run Deep Audit",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Run Audit", style = MaterialTheme.typography.labelLarge)
                            }
                        }

                        when (val state = deepAuditState) {
                            is AiTaskState.Loading -> {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        color = SafetyAmber,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Analyzing shift ledger...",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = SafetyAmber
                                    )
                                }
                            }
                            is AiTaskState.Streaming -> {
                                Surface(
                                    color = AsphaltObsidian,
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.partialText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            is AiTaskState.Success -> {
                                Surface(
                                    color = AsphaltObsidian,
                                    shape = RoundedCornerShape(10.dp),
                                    border = BorderStroke(1.dp, CardSlateBorder),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = state.resultText,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }
                            is AiTaskState.Error -> {
                                Text(
                                    text = state.message,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = DebtCrimson
                                )
                            }
                            else -> {}
                        }
                    }
                }
            }

            // Chronological Audit Log Filter Bar
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Chronological Audit Log (${filteredLogs.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Immutable records",
                        style = MaterialTheme.typography.labelMedium,
                        color = MutedSteel
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "All (${logs.size})",
                        "INSIDE" to "Inside (${logs.count { it.status == VehicleStatus.INSIDE }})",
                        "CASH" to "Cash",
                        "UPI" to "UPI",
                        "CREDIT" to "Credit"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = selectedFilter == key,
                            onClick = { selectedFilter = key },
                            label = { Text(label, style = MaterialTheme.typography.labelLarge) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SafetyAmber,
                                selectedLabelColor = Color.Black,
                                containerColor = ElevatedSlate,
                                labelColor = Color.White
                            ),
                            modifier = Modifier.testTag("audit_filter_chip_$key")
                        )
                    }
                }
            }

            if (filteredLogs.isEmpty()) {
                item {
                    Surface(
                        color = ElevatedSlate,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardSlateBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                                contentDescription = "No logs",
                                tint = MutedSteel,
                                modifier = Modifier.size(30.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "No vehicle entries matching this filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSteel
                            )
                        }
                    }
                }
            } else {
                items(filteredLogs, key = { it.id }) { log ->
                    AuditLogItemCard(
                        log = log,
                        onClickExit = { viewModel.openExitSheetForVehicle(log) },
                        onClickThumbnail = { selectedPhotoPreviewLog = log }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }

    if (showRateSettingsDialog) {
        RateConfigEditorDialog(
            currentConfig = rateConfig,
            onSave = { daily, weekly, monthly, pin, luma ->
                viewModel.saveOwnerRateConfig(daily, weekly, monthly, pin, luma)
                showRateSettingsDialog = false
            },
            onDismiss = { showRateSettingsDialog = false }
        )
    }

    val previewLog = selectedPhotoPreviewLog
    if (previewLog != null) {
        AlertDialog(
            onDismissRequest = { selectedPhotoPreviewLog = null },
            containerColor = ElevatedSlate,
            title = {
                Text(
                    text = "Entry Photo • ${previewLog.plateNumber}",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (previewLog.photoPath.isNotBlank() && File(previewLog.photoPath).exists()) {
                        AsyncImage(
                            model = File(previewLog.photoPath),
                            contentDescription = "Burned-in timestamp entry photo",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Text(
                            text = "Snapshot path: ${previewLog.photoPath}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                    Text(
                        text = "Logged by ${previewLog.workerName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedPhotoPreviewLog = null },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SafetyAmber,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Close")
                }
            }
        )
    }
}

@Composable
private fun MetricAuditCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    subtitle: String,
    accentColor: Color,
    icon: ImageVector
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
        border = BorderStroke(1.dp, CardSlateBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, style = MaterialTheme.typography.bodyMedium, color = MutedSteel)
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.displayMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                color = accentColor
            )
            Text(text = subtitle, style = MaterialTheme.typography.labelMedium, color = MutedSteel)
        }
    }
}

@Composable
private fun CreditAuditRow(
    credit: CreditEntity,
    onSettleCash: () -> Unit,
    onSettleUpi: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
        border = BorderStroke(1.dp, DebtCrimson.copy(alpha = 0.7f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${credit.plateNumber} • ₹${credit.pendingAmount.toInt()}",
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = Color.White
                )
                Text(
                    text = "${fmt.format(Date(credit.lastUnpaidTimestamp))} • ${credit.notes}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = onSettleCash,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CashEmerald,
                        contentColor = Color.Black
                    ),
                    modifier = Modifier.testTag("settle_credit_cash_${credit.plateNumber}")
                ) {
                    Text("Cash", style = MaterialTheme.typography.labelLarge)
                }
                OutlinedButton(
                    onClick = onSettleUpi,
                    border = BorderStroke(1.dp, UpiCyan),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UpiCyan),
                    modifier = Modifier.testTag("settle_credit_upi_${credit.plateNumber}")
                ) {
                    Text("UPI", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun AuditLogItemCard(
    log: VehicleLogEntity,
    onClickExit: () -> Unit,
    onClickThumbnail: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH) }
    val entryTimeStr = remember(log.entryTimestamp) { fmt.format(Date(log.entryTimestamp)) }
    val exitTimeStr = remember(log.exitTimestamp) {
        log.exitTimestamp?.let { fmt.format(Date(it)) } ?: "Parked inside"
    }

    val badgeColor = when (log.paymentStatus) {
        PaymentStatus.PAID_CASH -> CashEmerald
        PaymentStatus.PAID_UPI -> UpiCyan
        PaymentStatus.UNPAID_CREDIT -> DebtCrimson
        PaymentStatus.PASS_COVERED -> CashEmerald
        else -> SafetyAmber
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("audit_log_card_${log.id}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
        border = BorderStroke(1.dp, CardSlateBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AsphaltObsidian)
                    .clickable { onClickThumbnail() },
                contentAlignment = Alignment.Center
            ) {
                if (log.photoPath.isNotBlank() && File(log.photoPath).exists()) {
                    AsyncImage(
                        model = File(log.photoPath),
                        contentDescription = "Entry thumbnail for ${log.plateNumber}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                        contentDescription = "Vehicle icon",
                        tint = SafetyAmber,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = log.plateNumber,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontFamily = JetBrainsMonoFontFamily
                        ),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        color = badgeColor.copy(alpha = 0.14f),
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.6f))
                    ) {
                        Text(
                            text = if (log.status == VehicleStatus.INSIDE) "INSIDE" else log.paymentStatus.replace("_", " "),
                            style = MaterialTheme.typography.labelMedium,
                            color = badgeColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "In: $entryTimeStr • Out: $exitTimeStr",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )

                val isSynced = log.syncStatus == SyncStatus.SYNCED
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${log.daysCharged}d • Due ₹${log.totalAmountDue.toInt()} • ${if (isSynced) "Synced" else "Queued Offline"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isSynced) Color.White else SafetyAmber
                    )
                    if (log.status == VehicleStatus.INSIDE) {
                        OutlinedButton(
                            onClick = onClickExit,
                            border = BorderStroke(1.dp, CashEmerald),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = CashEmerald),
                            modifier = Modifier.height(34.dp)
                        ) {
                            Text("Exit & Bill", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RateConfigEditorDialog(
    currentConfig: RateConfig,
    onSave: (daily: Double, weekly: Double, monthly: Double, pin: String, luma: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var dailyStr by remember { mutableStateOf(currentConfig.dailyRate.toInt().toString()) }
    var weeklyStr by remember { mutableStateOf(currentConfig.weeklyPassRate.toInt().toString()) }
    var monthlyStr by remember { mutableStateOf(currentConfig.monthlyPassRate.toInt().toString()) }
    var pinStr by remember { mutableStateOf(currentConfig.ownerPin) }
    var lumaStr by remember { mutableStateOf(currentConfig.lowLightThreshold.toInt().toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElevatedSlate,
        title = {
            Text(
                text = "Tariff Rates & Settings",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = dailyStr,
                    onValueChange = { dailyStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Base 24-Hour Daily Rate (₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rate_daily_input")
                )
                OutlinedTextField(
                    value = weeklyStr,
                    onValueChange = { weeklyStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Weekly Pass Rate (7 Days • ₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rate_weekly_input")
                )
                OutlinedTextField(
                    value = monthlyStr,
                    onValueChange = { monthlyStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Monthly Pass Rate (30 Days • ₹)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rate_monthly_input")
                )
                OutlinedTextField(
                    value = pinStr,
                    onValueChange = { pinStr = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text("Owner Security PIN (4-8 digits)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rate_pin_input")
                )
                OutlinedTextField(
                    value = lumaStr,
                    onValueChange = { lumaStr = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Auto-Torch Low-Light Threshold (5-200)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        dailyStr.toDoubleOrNull() ?: 20.0,
                        weeklyStr.toDoubleOrNull() ?: 100.0,
                        monthlyStr.toDoubleOrNull() ?: 350.0,
                        pinStr.ifBlank { "1234" },
                        lumaStr.toDoubleOrNull() ?: 45.0
                    )
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyAmber,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("save_rate_config_button")
            ) {
                Text("Save", style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
