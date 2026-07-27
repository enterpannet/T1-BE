package com.getmoney.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ocr.SlipIntake
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
    sharedImageUri: Uri? = null,
    onShareUriConsumed: () -> Unit = {},
) {
    val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = null)

    when (isLoggedIn) {
        null -> LoadingScreen()
        true -> MainShell(
            authRepository = authRepository,
            budgetRepository = budgetRepository,
            transactionRepository = transactionRepository,
            slipIntake = slipIntake,
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
    sharedImageUri: Uri?,
    onShareUriConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

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
