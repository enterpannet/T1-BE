package com.getmoney.app.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.ui.auth.LoginScreen
import com.getmoney.app.ui.auth.RegisterScreen
import kotlinx.coroutines.launch

@Composable
fun AppNav(authRepository: AuthRepository) {
    val isLoggedIn by authRepository.isLoggedIn.collectAsState(initial = null)

    when (isLoggedIn) {
        null -> {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        }

        true -> HomeScreen(authRepository = authRepository)

        false -> {
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
    }
}

@Composable
private fun HomeScreen(authRepository: AuthRepository) {
    val scope = rememberCoroutineScope()

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "GetMoney",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            TextButton(
                onClick = { scope.launch { authRepository.logout() } },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp),
            ) {
                Text("Sign out")
            }
        }
    }
}
