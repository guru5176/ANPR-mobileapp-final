package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.ParkingDatabase
import com.example.data.local.PaymentStatus
import com.example.data.local.SyncStatus
import com.example.data.local.VehicleStatus
import com.example.data.repository.BillingRepository
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOut
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAppLanguage
import com.example.ui.i18n.tr
import com.example.ui.owner.OwnerDashboardScreen
import com.example.ui.owner.OwnerSection
import com.example.ui.scanner.CameraAnprScreen
import com.example.ui.scanner.IssuePassDialog
import com.example.ui.theme.AsphaltObsidian
import com.example.ui.theme.CardSlateBorder
import com.example.ui.theme.CashEmerald
import com.example.ui.theme.DebtCrimson
import com.example.ui.theme.ElevatedSlate
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MutedSteel
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SafetyAmber
import com.example.ui.theme.UpiCyan
import com.example.ui.viewmodel.AppRole
import com.example.ui.viewmodel.ParkingViewModel
import com.example.ui.viewmodel.ScanMode
import com.example.util.configureAppCheck
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // STEP 1: Configure Firebase App Check with launch intent BEFORE setContent or ViewModels
        configureAppCheck(this, intent)

        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                AppRootNavigation()
            }
        }
    }
}

internal fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

@Composable
fun AppRootNavigation(auth: FirebaseAuth = Firebase.auth) {
    val currentUser by auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)
    val user = currentUser

    if (user == null) {
        AuthScreen(onAuthSuccess = {})
    } else {
        AuthenticatedParkingApp(currentUserId = user.uid)
    }
}

/**
 * Navigation destinations clearly separated into:
 * - Worker Tasks: SCANNER (ANPR Entry/Exit Scanner) & PARKING_LOG (Active Vehicles + Passes/Credits)
 * - Owner Tasks: OWNER_LEDGER (Shift Cash Drawer Ledger & Tariffs) & OWNER_AUDIT (Audit Reports & AI Fraud Check)
 */
enum class MainTab(val isOwnerTask: Boolean) {
    SCANNER(isOwnerTask = false),
    PARKING_LOG(isOwnerTask = false),
    OWNER_LEDGER(isOwnerTask = true),
    OWNER_AUDIT(isOwnerTask = true)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatedParkingApp(currentUserId: String) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember { CredentialManager.create(context) }

    val viewModel: ParkingViewModel = viewModel(
        key = currentUserId,
        factory = viewModelFactory {
            initializer {
                val app = checkNotNull(this[APPLICATION_KEY])
                val databaseId = app.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(databaseId)
                val dao = ParkingDatabase.getInstance(app).parkingDao()
                ParkingViewModel(
                    repository = BillingRepository(firestore = firestore, parkingDao = dao),
                    currentUserId = currentUserId,
                    applicationContext = app.applicationContext
                )
            }
        }
    )

    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()
    val currentRole by viewModel.currentRole.collectAsStateWithLifecycle()
    val scanMode by viewModel.scanMode.collectAsStateWithLifecycle()
    val summary by viewModel.shiftAuditSummary.collectAsStateWithLifecycle()
    val statusBanner by viewModel.statusBannerMessage.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isSyncingQueue by viewModel.isSyncingQueue.collectAsStateWithLifecycle()

    var currentTab by remember { mutableStateOf(MainTab.SCANNER) }
    var pendingOwnerTab by remember { mutableStateOf<MainTab?>(null) }
    var showOwnerPinModal by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(statusBanner) {
        val msg = statusBanner
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(msg)
            viewModel.clearBannerMessage()
        }
    }

    if (currentTab != MainTab.SCANNER) {
        BackHandler {
            currentTab = MainTab.SCANNER
        }
    }

    fun selectTab(target: MainTab) {
        if (target.isOwnerTask && currentRole != AppRole.OWNER) {
            pendingOwnerTab = target
            pinError = null
            showOwnerPinModal = true
        } else {
            currentTab = target
        }
    }

    CompositionLocalProvider(LocalAppLanguage provides appLanguage) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp

            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = AsphaltObsidian,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = AsphaltObsidian,
                            titleContentColor = Color.White
                        ),
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(
                                    text = "MotoVault",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
                                )

                                // Minimal Cloud Sync Status Pill
                                val syncPillColor = when {
                                    pendingSyncCount > 0 -> SafetyAmber
                                    !isOnline -> DebtCrimson
                                    else -> CashEmerald
                                }
                                val syncPillLabel = when {
                                    isSyncingQueue -> tr(appLanguage, "Syncing", "இணைக்கிறது")
                                    pendingSyncCount > 0 -> tr(appLanguage, "$pendingSyncCount Queued", "$pendingSyncCount நிலுவை")
                                    !isOnline -> tr(appLanguage, "Offline", "ஆஃப்லைன்")
                                    else -> tr(appLanguage, "Live", "ஆன்லைன்")
                                }
                                Surface(
                                    color = syncPillColor.copy(alpha = 0.12f),
                                    shape = RoundedCornerShape(20.dp),
                                    border = BorderStroke(1.dp, syncPillColor.copy(alpha = 0.4f)),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .clickable { viewModel.syncOfflineQueueNow(silent = false) }
                                        .testTag("sync_queue_status_pill")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(RoundedCornerShape(3.dp))
                                                .clickable(enabled = false) {}
                                        ) {
                                            Surface(
                                                color = syncPillColor,
                                                shape = RoundedCornerShape(3.dp),
                                                modifier = Modifier.fillMaxSize()
                                            ) {}
                                        }
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = syncPillLabel,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = syncPillColor,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        },
                        actions = {
                            // One-Tap Tamil / English Language Toggle Pill
                            Surface(
                                color = if (appLanguage == AppLanguage.TAMIL) {
                                    SafetyAmber
                                } else {
                                    ElevatedSlate
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, SafetyAmber.copy(alpha = 0.7f)),
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable { viewModel.toggleLanguage() }
                                    .testTag("language_toggle_button")
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 12.dp)
                                ) {
                                    Text(
                                        text = if (appLanguage == AppLanguage.ENGLISH) "தமிழ்" else "EN",
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = if (appLanguage == AppLanguage.TAMIL) Color.Black else SafetyAmber,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Unified Role Badge & Switcher Button (Worker <-> PIN-Protected Owner)
                            val isOwner = currentRole == AppRole.OWNER
                            val roleAccent = if (isOwner) CashEmerald else SafetyAmber
                            Surface(
                                color = roleAccent.copy(alpha = 0.12f),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, roleAccent.copy(alpha = 0.5f)),
                                modifier = Modifier
                                    .height(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable {
                                        if (isOwner) {
                                            viewModel.switchToWorkerRole()
                                            if (currentTab.isOwnerTask) {
                                                currentTab = MainTab.SCANNER
                                            }
                                        } else {
                                            pendingOwnerTab = MainTab.OWNER_LEDGER
                                            pinError = null
                                            showOwnerPinModal = true
                                        }
                                    }
                                    .testTag("role_switch_button")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isOwner) {
                                            Icons.Default.AdminPanelSettings
                                        } else {
                                            Icons.Default.Badge
                                        },
                                        contentDescription = "Current Role",
                                        modifier = Modifier.size(15.dp),
                                        tint = roleAccent
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = if (isOwner) {
                                            tr(appLanguage, "Owner", "உரிமையாளர்")
                                        } else {
                                            tr(appLanguage, "Worker", "பணியாளர்")
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = roleAccent,
                                        maxLines = 1
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    signOut(
                                        context = context,
                                        credentialManager = credentialManager,
                                        onSignOutComplete = {},
                                        scope = scope
                                    )
                                },
                                modifier = Modifier.testTag("sign_out_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Sign Out",
                                    tint = MutedSteel,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    )
                },
                bottomBar = {
                    if (!useNavigationRail) {
                        BottomRoleNavigationBar(
                            currentTab = currentTab,
                            insideCount = summary.totalBikesInside,
                            onSelectTab = { selectTab(it) }
                        )
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        SideRoleNavigationRail(
                            currentTab = currentTab,
                            insideCount = summary.totalBikesInside,
                            onSelectTab = { selectTab(it) }
                        )
                        VerticalDivider(color = CardSlateBorder, thickness = 1.dp)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        when (currentTab) {
                            MainTab.SCANNER -> CameraAnprScreen(viewModel = viewModel)
                            MainTab.PARKING_LOG -> WorkerParkingLogScreen(
                                viewModel = viewModel,
                                onJumpToExitScanner = {
                                    viewModel.setScanMode(ScanMode.EXIT)
                                    currentTab = MainTab.SCANNER
                                }
                            )
                            MainTab.OWNER_LEDGER -> OwnerDashboardScreen(
                                viewModel = viewModel,
                                initialSection = OwnerSection.LEDGER
                            )
                            MainTab.OWNER_AUDIT -> OwnerDashboardScreen(
                                viewModel = viewModel,
                                initialSection = OwnerSection.AUDIT_REPORTS
                            )
                        }
                    }
                }
            }
        }

        if (showOwnerPinModal) {
            OwnerPinUnlockDialog(
                errorMessage = pinError,
                onVerifyPin = { enteredPin ->
                    val ok = viewModel.unlockOwnerRoleWithPin(enteredPin)
                    if (ok) {
                        showOwnerPinModal = false
                        currentTab = pendingOwnerTab ?: MainTab.OWNER_LEDGER
                        pendingOwnerTab = null
                    } else {
                        pinError = tr(
                            appLanguage,
                            "Invalid Owner PIN (Default: 1234)",
                            "தவறான உரிமையாளர் PIN (இயல்பு: 1234)"
                        )
                    }
                },
                onDismiss = {
                    showOwnerPinModal = false
                    pendingOwnerTab = null
                }
            )
        }
    }
}

@Composable
private fun BottomRoleNavigationBar(
    currentTab: MainTab,
    insideCount: Int,
    onSelectTab: (MainTab) -> Unit
) {
    val lang = LocalAppLanguage.current
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = Color.Black,
        selectedTextColor = SafetyAmber,
        indicatorColor = SafetyAmber,
        unselectedIconColor = MutedSteel,
        unselectedTextColor = MutedSteel
    )

    NavigationBar(
        containerColor = ElevatedSlate,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Worker Task 1: ANPR Scanner (Entry & Exit)
        NavigationBarItem(
            selected = currentTab == MainTab.SCANNER,
            onClick = { onSelectTab(MainTab.SCANNER) },
            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Worker Scanner") },
            label = { Text(tr(lang, "Scanner", "ஸ்கேனர்"), style = MaterialTheme.typography.labelMedium) },
            colors = itemColors,
            modifier = Modifier.testTag("nav_tab_scanner")
        )

        // Worker Task 2: Parking Log (Inside Vehicles & Passes/Credits)
        NavigationBarItem(
            selected = currentTab == MainTab.PARKING_LOG,
            onClick = { onSelectTab(MainTab.PARKING_LOG) },
            icon = { Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = "Worker Parking Log") },
            label = { Text(tr(lang, "Log ($insideCount)", "வாகனங்கள் ($insideCount)"), style = MaterialTheme.typography.labelMedium) },
            colors = itemColors,
            modifier = Modifier.testTag("nav_tab_inside")
        )

        // Owner Task 1: Shift Ledger
        NavigationBarItem(
            selected = currentTab == MainTab.OWNER_LEDGER,
            onClick = { onSelectTab(MainTab.OWNER_LEDGER) },
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Owner Shift Ledger") },
            label = { Text(tr(lang, "Ledger", "கணக்கு"), style = MaterialTheme.typography.labelMedium) },
            colors = itemColors,
            modifier = Modifier.testTag("nav_tab_passes")
        )

        // Owner Task 2: Audit Reports
        NavigationBarItem(
            selected = currentTab == MainTab.OWNER_AUDIT,
            onClick = { onSelectTab(MainTab.OWNER_AUDIT) },
            icon = { Icon(Icons.Default.Assessment, contentDescription = "Owner Audit Reports") },
            label = { Text(tr(lang, "Audit", "ஆய்வு"), style = MaterialTheme.typography.labelMedium) },
            colors = itemColors,
            modifier = Modifier.testTag("nav_tab_owner")
        )
    }
}

@Composable
private fun SideRoleNavigationRail(
    currentTab: MainTab,
    insideCount: Int,
    onSelectTab: (MainTab) -> Unit
) {
    val lang = LocalAppLanguage.current
    val railItemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = Color.Black,
        selectedTextColor = SafetyAmber,
        indicatorColor = SafetyAmber,
        unselectedIconColor = MutedSteel,
        unselectedTextColor = MutedSteel
    )

    NavigationRail(
        containerColor = ElevatedSlate,
        modifier = Modifier.fillMaxHeight()
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = tr(lang, "Worker", "பணியாளர்"),
            style = MaterialTheme.typography.labelMedium,
            color = MutedSteel,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        NavigationRailItem(
            selected = currentTab == MainTab.SCANNER,
            onClick = { onSelectTab(MainTab.SCANNER) },
            icon = { Icon(Icons.Default.QrCodeScanner, contentDescription = "Worker Scanner") },
            label = { Text(tr(lang, "Scanner", "ஸ்கேனர்"), style = MaterialTheme.typography.labelMedium) },
            colors = railItemColors,
            modifier = Modifier.testTag("nav_tab_scanner")
        )
        NavigationRailItem(
            selected = currentTab == MainTab.PARKING_LOG,
            onClick = { onSelectTab(MainTab.PARKING_LOG) },
            icon = { Icon(Icons.AutoMirrored.Filled.DirectionsBike, contentDescription = "Worker Parking Log") },
            label = { Text(tr(lang, "Log ($insideCount)", "வாகனங்கள் ($insideCount)"), style = MaterialTheme.typography.labelMedium) },
            colors = railItemColors,
            modifier = Modifier.testTag("nav_tab_inside")
        )

        HorizontalDivider(
            color = CardSlateBorder,
            modifier = Modifier
                .width(48.dp)
                .padding(vertical = 10.dp)
        )

        Text(
            text = tr(lang, "Owner", "உரிமையாளர்"),
            style = MaterialTheme.typography.labelMedium,
            color = MutedSteel,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        NavigationRailItem(
            selected = currentTab == MainTab.OWNER_LEDGER,
            onClick = { onSelectTab(MainTab.OWNER_LEDGER) },
            icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = "Owner Ledger") },
            label = { Text(tr(lang, "Ledger", "கணக்கு"), style = MaterialTheme.typography.labelMedium) },
            colors = railItemColors,
            modifier = Modifier.testTag("nav_tab_passes")
        )
        NavigationRailItem(
            selected = currentTab == MainTab.OWNER_AUDIT,
            onClick = { onSelectTab(MainTab.OWNER_AUDIT) },
            icon = { Icon(Icons.Default.Assessment, contentDescription = "Owner Audit Reports") },
            label = { Text(tr(lang, "Audit", "ஆய்வு"), style = MaterialTheme.typography.labelMedium) },
            colors = railItemColors,
            modifier = Modifier.testTag("nav_tab_owner")
        )
    }
}

private enum class WorkerLogSubTab {
    PARKED_VEHICLES,
    PASSES_AND_CREDITS
}

@Composable
private fun WorkerParkingLogScreen(
    viewModel: ParkingViewModel,
    onJumpToExitScanner: () -> Unit
) {
    val lang = LocalAppLanguage.current
    val logs by viewModel.vehicleLogs.collectAsStateWithLifecycle()
    val passes by viewModel.passes.collectAsStateWithLifecycle()
    val credits by viewModel.credits.collectAsStateWithLifecycle()
    val rateConfig by viewModel.rateConfig.collectAsStateWithLifecycle()
    val pendingSyncCount by viewModel.pendingSyncCount.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val isSyncingQueue by viewModel.isSyncingQueue.collectAsStateWithLifecycle()

    var subTab by remember { mutableStateOf(WorkerLogSubTab.PARKED_VEHICLES) }
    var showNewPassDialog by remember { mutableStateOf(false) }

    val insideLogs = remember(logs) { logs.filter { it.status == VehicleStatus.INSIDE } }
    val activeCredits = remember(credits) { credits.filter { !it.isSettled && it.pendingAmount > 0.0 } }
    val timeFmt = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.ENGLISH) }
    val dateFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (!isOnline || pendingSyncCount > 0) {
            item {
                Surface(
                    color = ElevatedSlate,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        1.dp,
                        if (pendingSyncCount > 0) SafetyAmber.copy(alpha = 0.6f) else DebtCrimson.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("worker_offline_sync_queue_banner")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (!isOnline) {
                                    tr(lang, "Offline Mode • Saved Locally", "ஆஃப்லைன் முறை • லோக்கல் பதிவு")
                                } else {
                                    tr(lang, "$pendingSyncCount Pending Cloud Sync", "$pendingSyncCount பதிவுகள் நிலுவையில்")
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = if (pendingSyncCount > 0) SafetyAmber else DebtCrimson
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = { viewModel.syncOfflineQueueNow(silent = false) },
                            enabled = !isSyncingQueue,
                            border = BorderStroke(1.dp, SafetyAmber),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = SafetyAmber),
                            modifier = Modifier.testTag("worker_sync_now_button")
                        ) {
                            Text(
                                text = if (isSyncingQueue) {
                                    tr(lang, "Syncing...", "இணைக்கிறது...")
                                } else {
                                    tr(lang, "Sync Now", "இப்போது இணை")
                                },
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }

        // Sub-Tab Selector: Parked Vehicles vs Passes & Credits
        item {
            Surface(
                color = ElevatedSlate,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardSlateBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val isParked = subTab == WorkerLogSubTab.PARKED_VEHICLES
                    Surface(
                        color = if (isParked) SafetyAmber else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { subTab = WorkerLogSubTab.PARKED_VEHICLES }
                            .testTag("worker_log_subtab_parked")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                                contentDescription = "Parked Inside",
                                tint = if (isParked) Color.Black else MutedSteel,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr(lang, "Parked (${insideLogs.size})", "உள்ளே (${insideLogs.size})"),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (isParked) Color.Black else MutedSteel
                            )
                        }
                    }

                    Surface(
                        color = if (!isParked) SafetyAmber else Color.Transparent,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable { subTab = WorkerLogSubTab.PASSES_AND_CREDITS }
                            .testTag("worker_log_subtab_passes")
                    ) {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreditCard,
                                contentDescription = "Passes and Credits",
                                tint = if (!isParked) Color.Black else MutedSteel,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = tr(lang, "Passes & Credit", "பாஸ் & கடன்"),
                                style = MaterialTheme.typography.titleMedium,
                                color = if (!isParked) Color.Black else MutedSteel
                            )
                        }
                    }
                }
            }
        }

        if (subTab == WorkerLogSubTab.PARKED_VEHICLES) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tr(lang, "Active Vehicles (${insideLogs.size})", "உள்ளே நிற்கும் வாகனங்கள் (${insideLogs.size})"),
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White
                    )
                    OutlinedButton(
                        onClick = onJumpToExitScanner,
                        border = BorderStroke(1.dp, CashEmerald.copy(alpha = 0.7f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = CashEmerald),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("jump_to_exit_scanner_button")
                    ) {
                        Text(tr(lang, "Scan Out", "வெளியே ஸ்கேன்"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            if (insideLogs.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
                        border = BorderStroke(1.dp, CardSlateBorder),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.DirectionsBike,
                                contentDescription = "No bikes inside",
                                tint = MutedSteel,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = tr(lang, "No vehicles currently parked inside.", "தற்போது உள்ளே வாகனங்கள் எதுவும் இல்லை."),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MutedSteel
                            )
                        }
                    }
                }
            } else {
                items(insideLogs, key = { it.id }) { log ->
                    val isSynced = log.syncStatus == SyncStatus.SYNCED
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
                        border = BorderStroke(1.dp, CardSlateBorder)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text(
                                        text = log.plateNumber,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = Color.White,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    if (!isSynced) {
                                        Text(
                                            text = tr(lang, "Offline", "ஆஃப்லைன்"),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = SafetyAmber
                                        )
                                    }
                                }
                                Text(
                                    text = "${tr(lang, "In", "உள்ளே")}: ${timeFmt.format(Date(log.entryTimestamp))}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                                if (log.previousCreditAdded > 0.0) {
                                    Text(
                                        text = tr(
                                            lang,
                                            "+ ₹${log.previousCreditAdded.toInt()} previous credit",
                                            "+ ₹${log.previousCreditAdded.toInt()} பழைய பாக்கி"
                                        ),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = DebtCrimson
                                    )
                                }
                            }
                            Button(
                                onClick = { viewModel.openExitSheetForVehicle(log) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CashEmerald,
                                    contentColor = Color.Black
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.testTag("inside_list_exit_button_${log.plateNumber}")
                            ) {
                                Text(tr(lang, "Exit & Bill", "வெளியே & பில்"), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }
        } else {
            // PASSES & CREDITS SUB-TAB
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tr(lang, "Passes & Credit", "பாஸ் & கடன்"),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White
                        )
                        Text(
                            text = tr(
                                lang,
                                "Weekly ₹${rateConfig.weeklyPassRate.toInt()} • Monthly ₹${rateConfig.monthlyPassRate.toInt()}",
                                "வாரம் ₹${rateConfig.weeklyPassRate.toInt()} • மாதம் ₹${rateConfig.monthlyPassRate.toInt()}"
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MutedSteel
                        )
                    }
                    Button(
                        onClick = { showNewPassDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SafetyAmber,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("new_pass_screen_button")
                    ) {
                        Text(tr(lang, "+ New Pass", "+ புதிய பாஸ்"), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item {
                Text(
                    text = tr(
                        lang,
                        "Pending Credit (${activeCredits.size})",
                        "நிலுவை கடன் (${activeCredits.size})"
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (activeCredits.isEmpty()) MutedSteel else DebtCrimson
                )
            }

            if (activeCredits.isEmpty()) {
                item {
                    Text(
                        text = tr(lang, "No pending customer credits.", "நிலுவையில் வாடிக்கையாளர் கடன் எதுவும் இல்லை."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                }
            } else {
                items(activeCredits, key = { "debt_${it.plateNumber}" }) { credit ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
                        border = BorderStroke(1.dp, DebtCrimson.copy(alpha = 0.7f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${credit.plateNumber} • ₹${credit.pendingAmount.toInt()}",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = JetBrainsMonoFontFamily
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = "${dateFmt.format(Date(credit.lastUnpaidTimestamp))} • ${credit.notes}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MutedSteel
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = { viewModel.settlePendingCredit(credit, PaymentStatus.PAID_CASH) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = CashEmerald,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text(tr(lang, "Cash", "ரொக்கம்"), style = MaterialTheme.typography.labelLarge)
                                }
                                OutlinedButton(
                                    onClick = { viewModel.settlePendingCredit(credit, PaymentStatus.PAID_UPI) },
                                    border = BorderStroke(1.dp, UpiCyan),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = UpiCyan)
                                ) {
                                    Text("UPI", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = tr(lang, "Registered Passes (${passes.size})", "பதிவு செய்யப்பட்ட பாஸ்கள் (${passes.size})"),
                    style = MaterialTheme.typography.titleMedium,
                    color = CashEmerald
                )
            }

            if (passes.isEmpty()) {
                item {
                    Text(
                        text = tr(lang, "No passes issued yet.", "இதுவரை பாஸ்கள் எதுவும் வழங்கப்படவில்லை."),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MutedSteel
                    )
                }
            } else {
                items(passes, key = { "pass_${it.plateNumber}" }) { pass ->
                    val isActive = pass.isActive()
                    val daysLeft = pass.daysRemaining()
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = ElevatedSlate),
                        border = BorderStroke(1.dp, if (isActive) CashEmerald.copy(alpha = 0.6f) else CardSlateBorder),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Verified,
                                        contentDescription = "Pass status",
                                        tint = if (isActive) CashEmerald else SafetyAmber,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = pass.plateNumber,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = if (isActive) {
                                        tr(
                                            lang,
                                            "Valid • $daysLeft days left (exp ${dateFmt.format(Date(pass.expiryDate))})",
                                            "செல்லுபடியாகும் • $daysLeft நாட்கள் உள்ளன (${dateFmt.format(Date(pass.expiryDate))})"
                                        )
                                    } else {
                                        tr(
                                            lang,
                                            "Expired on ${dateFmt.format(Date(pass.expiryDate))}",
                                            "காலாவதியானது: ${dateFmt.format(Date(pass.expiryDate))}"
                                        )
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (isActive) CashEmerald else SafetyAmber
                                )
                                Text(
                                    text = "${pass.passType.replace("_", " ")} • ₹${pass.amountPaid.toInt()} (${pass.paymentMode})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MutedSteel
                                )
                            }
                            if (!isActive) {
                                Button(
                                    onClick = {
                                        viewModel.issueOrRenewPass(
                                            plateNumber = pass.plateNumber,
                                            passType = pass.passType,
                                            paymentMode = PaymentStatus.PAID_CASH
                                        )
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = SafetyAmber,
                                        contentColor = Color.Black
                                    )
                                ) {
                                    Text(tr(lang, "Renew", "புதுப்பி"), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showNewPassDialog) {
        IssuePassDialog(
            initialPlate = "",
            weeklyRate = rateConfig.weeklyPassRate,
            monthlyRate = rateConfig.monthlyPassRate,
            onConfirmPass = { plate, passType, paymentMode ->
                viewModel.issueOrRenewPass(plate, passType, paymentMode)
                showNewPassDialog = false
            },
            onDismiss = { showNewPassDialog = false }
        )
    }
}

@Composable
private fun OwnerPinUnlockDialog(
    errorMessage: String?,
    onVerifyPin: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val lang = LocalAppLanguage.current
    var pin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = ElevatedSlate,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = "Owner PIN", tint = SafetyAmber, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = tr(lang, "Unlock Owner Access", "உரிமையாளர் பகுதி (PIN)"),
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = tr(
                        lang,
                        "Enter the Owner PIN to view the shift cash ledger and audit reports (Default: 1234).",
                        "கல்லா கணக்கு மற்றும் அறிக்கைகளைப் பார்க்க உரிமையாளர் PIN எண் உள்ளிடவும் (இயல்பு: 1234)."
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedSteel
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { ch -> ch.isDigit() }.take(8) },
                    label = { Text(tr(lang, "Owner PIN", "உரிமையாளர் PIN")) },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("owner_pin_input")
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = DebtCrimson
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onVerifyPin(pin) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = SafetyAmber,
                    contentColor = Color.Black
                ),
                modifier = Modifier.testTag("confirm_owner_pin_button")
            ) {
                Text(tr(lang, "Unlock", "திற"), style = MaterialTheme.typography.titleMedium)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(tr(lang, "Cancel", "ரத்து"))
            }
        }
    )
}
