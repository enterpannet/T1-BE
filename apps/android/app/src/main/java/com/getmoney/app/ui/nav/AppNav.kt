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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.getmoney.app.autoscan.AutoScanCoordinator
import com.getmoney.app.autoscan.AutoScanStore
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ocr.SlipIntake
import kotlinx.coroutines.launch
import com.getmoney.app.ui.account.AccountScreen
import com.getmoney.app.ui.auth.LoginScreen
import com.getmoney.app.ui.auth.RegisterScreen
import com.getmoney.app.ui.budget.BudgetScreen
import com.getmoney.app.ui.home.HomeScreen
import com.getmoney.app.ui.slip.AddSlipScreen
import com.getmoney.app.ui.summary.SummaryScreen

private data class MainTab(val route: String, val label: String)

private val mainTabs = listOf(
    MainTab("today", "Today"),
    MainTab("summary", "Summary"),
    MainTab("budget", "Budget"),
    MainTab("account", "Account"),
)

@Composable
fun AppNav(
    authRepository: AuthRepository,
    budgetRepository: BudgetRepository,
    transactionRepository: TransactionRepository,
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
            slipIntake = slipIntake,
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
    slipIntake: SlipIntake,
    autoScanCoordinator: AutoScanCoordinator,
    sharedImageUri: Uri?,
    onShareUriConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val photoPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_IMAGES
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        scope.launch { autoScanCoordinator.runScanIfNeeded(granted) }
    }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(context, photoPermission) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
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

    Scaffold(
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
                )
            }
            composable("add_slip") {
                AddSlipScreen(
                    slipIntake = slipIntake,
                    transactionRepository = transactionRepository,
                    sharedImageUri = sharedImageUri,
                    onShareUriConsumed = onShareUriConsumed,
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
                AccountScreen(authRepository = authRepository)
            }
        }
    }
}
