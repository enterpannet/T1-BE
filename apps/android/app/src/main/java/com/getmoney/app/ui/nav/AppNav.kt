package com.getmoney.app.ui.nav

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.getmoney.app.autoscan.AutoScanCoordinator
import com.getmoney.app.autoscan.AutoScanStore
import com.getmoney.app.autoscan.ReviewSlipZipExporter
import com.getmoney.app.autoscan.ScanProgress
import com.getmoney.app.data.api.AppUpdateApi
import com.getmoney.app.data.api.AppVersionResponse
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.data.cloudinary.CloudUploadStore
import com.getmoney.app.data.cloudinary.CloudinaryUploader
import com.getmoney.app.data.identity.MyIdentityStore
import com.getmoney.app.data.slipimage.SlipImageStore
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ocr.SlipIntake
import com.getmoney.app.ui.account.AccountScreen
import com.getmoney.app.ui.auth.LoginScreen
import com.getmoney.app.ui.auth.RegisterScreen
import com.getmoney.app.ui.budget.BudgetScreen
import com.getmoney.app.ui.components.AppDialog
import com.getmoney.app.ui.home.HomeScreen
import com.getmoney.app.ui.slip.AddSlipScreen
import com.getmoney.app.ui.summary.SummaryScreen
import com.getmoney.app.ui.theme.Ink
import com.getmoney.app.ui.theme.InkMuted
import com.getmoney.app.ui.tx.TransactionsScreen
import com.getmoney.app.update.AppUpdate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableFloatStateOf

private data class MainTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val mainTabs = listOf(
    MainTab("today", "Today", Icons.Outlined.Today),
    MainTab("tx", "Tx", Icons.AutoMirrored.Outlined.ReceiptLong),
    MainTab("summary", "Summary", Icons.Outlined.BarChart),
    MainTab("budget", "Budget", Icons.Outlined.AccountBalanceWallet),
    MainTab("account", "Account", Icons.Outlined.Person),
)

@Composable
fun AppNav(
    authRepository: AuthRepository,
    budgetRepository: BudgetRepository,
    transactionRepository: TransactionRepository,
    slipImageStore: SlipImageStore,
    cloudUploadStore: CloudUploadStore,
    cloudinaryUploader: CloudinaryUploader,
    slipIntake: SlipIntake,
    autoScanStore: AutoScanStore,
    autoScanCoordinator: AutoScanCoordinator,
    myIdentityStore: MyIdentityStore,
    appUpdateApi: AppUpdateApi,
    sharedImageUri: Uri? = null,
    onShareUriConsumed: () -> Unit = {},
) {
    val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = null)

    LaunchedEffect(isLoggedIn) {
        if (isLoggedIn == false) {
            autoScanCoordinator.clearQueue()
        }
    }

    when (isLoggedIn) {
        null -> LoadingScreen()
        true -> MainShell(
            authRepository = authRepository,
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            slipImageStore = slipImageStore,
            cloudUploadStore = cloudUploadStore,
            cloudinaryUploader = cloudinaryUploader,
            slipIntake = slipIntake,
            autoScanStore = autoScanStore,
            autoScanCoordinator = autoScanCoordinator,
            myIdentityStore = myIdentityStore,
            appUpdateApi = appUpdateApi,
            sharedImageUri = sharedImageUri,
            onShareUriConsumed = onShareUriConsumed,
        )
        false -> AuthNav(authRepository = authRepository)
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun AuthNav(authRepository: AuthRepository) {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = "login",
    ) {
        composable("login") {
            LoginScreen(
                authRepository = authRepository,
                onLoginSuccess = { },
                onNavigateRegister = { navController.navigate("register") },
            )
        }
        composable("register") {
            RegisterScreen(
                authRepository = authRepository,
                onRegisterSuccess = { },
                onNavigateLogin = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun MainShell(
    authRepository: AuthRepository,
    budgetRepository: BudgetRepository,
    transactionRepository: TransactionRepository,
    slipImageStore: SlipImageStore,
    cloudUploadStore: CloudUploadStore,
    cloudinaryUploader: CloudinaryUploader,
    slipIntake: SlipIntake,
    autoScanStore: AutoScanStore,
    autoScanCoordinator: AutoScanCoordinator,
    myIdentityStore: MyIdentityStore,
    appUpdateApi: AppUpdateApi,
    sharedImageUri: Uri?,
    onShareUriConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route?.substringBefore("?")
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val queue by autoScanCoordinator.queue.collectAsState()
    val bannerDismissed by autoScanCoordinator.bannerDismissed.collectAsState()
    val scanProgress by autoScanCoordinator.scanProgress.collectAsState()
    /** Slips waiting for manual review — kept until opened / skipped / saved. */
    val reviewQueueCount = queue.size
    var pendingAccountScanNow by remember { mutableStateOf(false) }
    var zipExporting by remember { mutableStateOf(false) }
    var pendingUpdate by remember { mutableStateOf<AppVersionResponse?>(null) }
    var updateDownloading by remember { mutableStateOf(false) }
    var updateProgress by remember { mutableFloatStateOf(-1f) }
    var updateError by remember { mutableStateOf<String?>(null) }
    val photoPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val hasPhotoPermission = ContextCompat.checkSelfPermission(context, photoPermission) ==
        PackageManager.PERMISSION_GRANTED

    fun openSlipQueue() {
        navController.navigate("add_slip?queue=true") {
            launchSingleTop = true
        }
    }

    fun dismissPendingSlips() {
        // Hide the blocking popup only — keep queue so user can open it later from Today.
        autoScanCoordinator.dismissBanner()
    }

    fun shareReviewZip() {
        if (zipExporting) return
        val slips = autoScanCoordinator.slipsForZipExport()
        if (slips.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar("ไม่มีสลิปที่ต้องส่งออก") }
            return
        }
        zipExporting = true
        scope.launch {
            try {
                val intent = ReviewSlipZipExporter.buildShareIntent(context, slips)
                if (intent == null) {
                    snackbarHostState.showSnackbar("สร้าง ZIP ไม่สำเร็จ")
                } else {
                    context.startActivity(Intent.createChooser(intent, "ส่งออกสลิปที่ต้องตรวจ"))
                }
            } finally {
                zipExporting = false
            }
        }
    }

    fun handleScanNowComplete(foundCount: Int) {
        val summary = autoScanCoordinator.consumeAutoSaveSummary()
        scope.launch {
            when {
                summary != null -> snackbarHostState.showSnackbar(summary.snackbarMessage())
                foundCount == 0 -> snackbarHostState.showSnackbar("ไม่พบสลิปใหม่")
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch {
            if (pendingAccountScanNow) {
                pendingAccountScanNow = false
                if (granted) {
                    autoScanCoordinator.runScanNow(true)
                    handleScanNowComplete(autoScanCoordinator.queue.value.size)
                }
            } else {
                autoScanCoordinator.runScanIfNeeded(granted)
                autoScanCoordinator.consumeAutoSaveSummary()?.let { summary ->
                    snackbarHostState.showSnackbar(summary.snackbarMessage())
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasPhotoPermission) {
            autoScanCoordinator.runScanIfNeeded(true)
            autoScanCoordinator.consumeAutoSaveSummary()?.let { summary ->
                snackbarHostState.showSnackbar(summary.snackbarMessage())
            }
        } else {
            permissionLauncher.launch(photoPermission)
        }
    }

    LaunchedEffect(Unit) {
        pendingUpdate = AppUpdate.fetchIfNewer(appUpdateApi)
    }

    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) {
            navController.navigate("add_slip") {
                launchSingleTop = true
            }
        }
    }

    val showSlipPopup = pendingUpdate == null &&
        !bannerDismissed &&
        reviewQueueCount > 0 &&
        currentRoute != "add_slip"

    pendingUpdate?.let { remote ->
        val notes = remote.notes.trim().ifEmpty { "แนะนำให้อัปเดตเพื่อความถูกต้องของสลิปและฟีเจอร์ใหม่" }
        AppDialog(
            onDismissRequest = {
                if (!remote.force && !updateDownloading) pendingUpdate = null
            },
            title = "มีเวอร์ชันใหม่ ${remote.versionName}",
            supportingText = buildString {
                append(notes)
                if (updateError != null) {
                    append("\n\n")
                    append(updateError)
                }
            },
            dismissOnClickOutside = !remote.force && !updateDownloading,
            dismissOnBackPress = !remote.force && !updateDownloading,
            primaryLabel = when {
                updateDownloading && updateProgress >= 0f ->
                    "กำลังดาวน์โหลด ${(updateProgress * 100).toInt()}%"
                updateDownloading -> "กำลังดาวน์โหลด…"
                else -> "อัปเดต"
            },
            onPrimary = {
                if (updateDownloading) return@AppDialog
                if (!AppUpdate.canRequestInstall(context)) {
                    context.startActivity(AppUpdate.installPermissionSettingsIntent(context))
                    updateError = "เปิดอนุญาต “ติดตั้งแอปที่ไม่รู้จัก” แล้วกดอัปเดตอีกครั้ง"
                    return@AppDialog
                }
                updateDownloading = true
                updateError = null
                updateProgress = -1f
                scope.launch {
                    try {
                        val file = withContext(Dispatchers.IO) {
                            AppUpdate.downloadApk(context, remote.apkUrl) { p ->
                                scope.launch(Dispatchers.Main.immediate) {
                                    updateProgress = p ?: -1f
                                }
                            }
                        }
                        AppUpdate.installApk(context, file)
                        if (!remote.force) pendingUpdate = null
                    } catch (e: Exception) {
                        updateError = e.message ?: "ดาวน์โหลดไม่สำเร็จ"
                    } finally {
                        updateDownloading = false
                    }
                }
            },
            primaryEnabled = !updateDownloading,
            secondaryLabel = if (remote.force || updateDownloading) null else "ภายหลัง",
            onSecondary = if (remote.force || updateDownloading) {
                null
            } else {
                { pendingUpdate = null }
            },
            content = if (updateDownloading && updateProgress >= 0f) {
                {
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { updateProgress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                null
            },
        )
    }

    if (showSlipPopup) {
        AppDialog(
            onDismissRequest = { /* require explicit choice */ },
            title = "มีสลิป $reviewQueueCount ใบที่ต้องตรวจ",
            icon = Icons.AutoMirrored.Outlined.ReceiptLong,
            supportingText = "บันทึกอัตโนมัติแล้วบางส่วน — ใบเหล่านี้ยังไม่ครบหรือบันทึกไม่สำเร็จ\nกดภายหลังแล้วกลับมาดูได้ที่หน้า Today\nหรือส่งออก ZIP เพื่อส่งให้ช่วยดูทีเดียว",
            dismissOnClickOutside = false,
            dismissOnBackPress = false,
            primaryLabel = "ดูเลย",
            onPrimary = { openSlipQueue() },
            secondaryLabel = if (zipExporting) "กำลังสร้าง ZIP…" else "ส่งออก ZIP",
            onSecondary = { shareReviewZip() },
            tertiaryLabel = "ภายหลัง",
            onTertiary = { dismissPendingSlips() },
        )
    }

    Scaffold(
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = Ink,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    actionColor = MaterialTheme.colorScheme.primary,
                    shape = MaterialTheme.shapes.small,
                )
            }
        },
        bottomBar = {
            if (currentRoute != "add_slip") {
                NavigationBar {
                    mainTabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            label = { Text(tab.label) },
                            icon = {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                )
                            },
                        )
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            val showProgress = scanProgress.phase != ScanProgress.Phase.Idle
            if (showProgress) {
                val progressDenom = when {
                    scanProgress.overallTotal > 0 -> scanProgress.overallTotal
                    scanProgress.total > 0 -> scanProgress.total
                    else -> 0
                }
                val progressNumer = when {
                    scanProgress.overallTotal > 0 -> scanProgress.overallCurrent
                    else -> scanProgress.current
                }
                val isPaused = scanProgress.phase == ScanProgress.Phase.Paused
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (scanProgress.isActive && !isPaused) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        }
                        Text(
                            text = scanProgress.statusLine(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (progressDenom > 0 && scanProgress.isActive) {
                        LinearProgressIndicator(
                            progress = {
                                (progressNumer.toFloat() / progressDenom.toFloat())
                                    .coerceIn(0f, 1f)
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    if (scanProgress.isActive) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            if (isPaused) {
                                TextButton(onClick = { autoScanCoordinator.resumeScan() }) {
                                    Text("ต่อ")
                                }
                            } else {
                                TextButton(onClick = { autoScanCoordinator.pauseScan() }) {
                                    Text("พัก")
                                }
                            }
                            TextButton(onClick = { autoScanCoordinator.stopScan() }) {
                                Text("หยุด")
                            }
                        }
                    }
                }
            }
            NavHost(
                navController = navController,
                startDestination = "today",
                modifier = Modifier.weight(1f),
            ) {
            composable("today") {
                HomeScreen(
                    budgetRepository = budgetRepository,
                    transactionRepository = transactionRepository,
                    slipImageStore = slipImageStore,
                    onNavigateBudget = {
                        navController.navigate("budget") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onAddSlip = { navController.navigate("add_slip") },
                    onViewAllTransactions = {
                        navController.navigate("tx") {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    pendingSlipCount = reviewQueueCount,
                    onReviewPendingSlips = { openSlipQueue() },
                    onExportReviewZip = { shareReviewZip() },
                    zipExporting = zipExporting,
                )
            }
            composable("tx") {
                TransactionsScreen(
                    transactionRepository = transactionRepository,
                    slipImageStore = slipImageStore,
                )
            }
            composable(
                route = "add_slip?queue={queue}",
                arguments = listOf(
                    navArgument("queue") {
                        type = NavType.BoolType
                        defaultValue = false
                    },
                ),
            ) { entry ->
                val startInQueueMode = entry.arguments?.getBoolean("queue") ?: false
                AddSlipScreen(
                    slipIntake = slipIntake,
                    transactionRepository = transactionRepository,
                    slipImageStore = slipImageStore,
                    cloudUploadStore = cloudUploadStore,
                    cloudinaryUploader = cloudinaryUploader,
                    sharedImageUri = sharedImageUri,
                    onShareUriConsumed = onShareUriConsumed,
                    autoScanCoordinator = autoScanCoordinator,
                    startInQueueMode = startInQueueMode,
                    onUploadFailed = { message ->
                        scope.launch {
                            snackbarHostState.showSnackbar(message)
                        }
                    },
                    onDone = { navController.popBackStack() },
                )
            }
            composable("summary") {
                SummaryScreen(budgetRepository = budgetRepository)
            }
            composable("budget") {
                BudgetScreen(budgetRepository = budgetRepository)
            }
            composable("account") {
                AccountScreen(
                    authRepository = authRepository,
                    autoScanStore = autoScanStore,
                    autoScanCoordinator = autoScanCoordinator,
                    cloudUploadStore = cloudUploadStore,
                    myIdentityStore = myIdentityStore,
                    hasPhotoPermission = hasPhotoPermission,
                    onRequestPhotoPermission = {
                        pendingAccountScanNow = true
                        permissionLauncher.launch(photoPermission)
                    },
                    onScanNowComplete = ::handleScanNowComplete,
                    onExportReviewZip = { shareReviewZip() },
                    zipExporting = zipExporting,
                )
            }
            }
        }
    }
}
