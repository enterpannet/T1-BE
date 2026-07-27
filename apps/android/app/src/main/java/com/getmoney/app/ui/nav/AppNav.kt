package com.getmoney.app.ui.nav

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.data.slipimage.SlipImageStore
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ocr.SlipIntake
import com.getmoney.app.ui.account.AccountScreen
import com.getmoney.app.ui.auth.LoginScreen
import com.getmoney.app.ui.auth.RegisterScreen
import com.getmoney.app.ui.budget.BudgetScreen
import com.getmoney.app.ui.home.HomeScreen
import com.getmoney.app.ui.slip.AddSlipScreen
import com.getmoney.app.ui.summary.SummaryScreen
import com.getmoney.app.ui.tx.TransactionsScreen
import com.getmoney.app.ui.theme.CarbonButtonDefaults
import kotlinx.coroutines.launch

private data class MainTab(val route: String, val label: String)

private val mainTabs = listOf(
    MainTab("today", "Today"),
    MainTab("tx", "Tx"),
    MainTab("summary", "Summary"),
    MainTab("budget", "Budget"),
    MainTab("account", "Account"),
)

@Composable
fun AppNav(
    authRepository: AuthRepository,
    budgetRepository: BudgetRepository,
    transactionRepository: TransactionRepository,
    slipImageStore: SlipImageStore,
    slipIntake: SlipIntake,
    autoScanStore: AutoScanStore,
    autoScanCoordinator: AutoScanCoordinator,
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
            slipIntake = slipIntake,
            autoScanStore = autoScanStore,
            autoScanCoordinator = autoScanCoordinator,
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
        contentAlignment = androidx.compose.ui.Alignment.Center,
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
    slipIntake: SlipIntake,
    autoScanStore: AutoScanStore,
    autoScanCoordinator: AutoScanCoordinator,
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
    val pendingSlipCount = if (!bannerDismissed) queue.size else 0
    var pendingAccountScanNow by remember { mutableStateOf(false) }
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
        autoScanCoordinator.dismissBanner()
        autoScanCoordinator.clearQueue()
    }

    fun handleScanNowComplete(foundCount: Int) {
        if (foundCount == 0) {
            scope.launch {
                snackbarHostState.showSnackbar("No new slips found")
            }
        }
        // foundCount > 0 → dialog below appears from queue StateFlow
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
            }
        }
    }

    LaunchedEffect(Unit) {
        if (hasPhotoPermission) {
            autoScanCoordinator.runScanIfNeeded(true)
        } else {
            permissionLauncher.launch(photoPermission)
        }
    }

    LaunchedEffect(sharedImageUri) {
        if (sharedImageUri != null) {
            navController.navigate("add_slip") {
                launchSingleTop = true
            }
        }
    }

    val showSlipPopup = pendingSlipCount > 0 && currentRoute != "add_slip"

    if (showSlipPopup) {
        AlertDialog(
            onDismissRequest = { /* require explicit choice */ },
            title = {
                Text("พบสลิปใหม่ $pendingSlipCount ใบ")
            },
            text = {
                Text("ต้องการตรวจและบันทึกตอนนี้หรือไม่?")
            },
            confirmButton = {
                Button(
                    onClick = { openSlipQueue() },
                    shape = MaterialTheme.shapes.small,
                    colors = CarbonButtonDefaults.primaryButtonColors(),
                    elevation = CarbonButtonDefaults.primaryButtonElevation(),
                ) {
                    Text("ดูเลย")
                }
            },
            dismissButton = {
                TextButton(onClick = { dismissPendingSlips() }) {
                    Text("ภายหลัง")
                }
            },
        )
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            icon = { Text(tab.label.take(1)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "today",
            modifier = Modifier.padding(padding),
        ) {
            composable("today") {
                HomeScreen(
                    budgetRepository = budgetRepository,
                    transactionRepository = transactionRepository,
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
                    pendingSlipCount = pendingSlipCount,
                    onReviewPendingSlips = { openSlipQueue() },
                    onDismissPendingBanner = { dismissPendingSlips() },
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
                    sharedImageUri = sharedImageUri,
                    onShareUriConsumed = onShareUriConsumed,
                    autoScanCoordinator = autoScanCoordinator,
                    startInQueueMode = startInQueueMode,
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
                    hasPhotoPermission = hasPhotoPermission,
                    onRequestPhotoPermission = {
                        pendingAccountScanNow = true
                        permissionLauncher.launch(photoPermission)
                    },
                    onScanNowComplete = ::handleScanNowComplete,
                )
            }
        }
    }
}
