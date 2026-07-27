package com.getmoney.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.getmoney.app.data.api.ApiClient
import com.getmoney.app.data.api.BudgetApi
import com.getmoney.app.data.api.SummaryApi
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.data.auth.TokenStore
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.ui.nav.AppNav
import com.getmoney.app.ui.theme.GetMoneyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val tokenStore = TokenStore(applicationContext)
        val apiClient = ApiClient(tokenStore)
        val authRepository = AuthRepository(apiClient.authApi, tokenStore)
        val budgetRepository = BudgetRepository(
            budgetApi = apiClient.createService(BudgetApi::class.java),
            summaryApi = apiClient.createService(SummaryApi::class.java),
        )

        setContent {
            GetMoneyTheme {
                AppNav(
                    authRepository = authRepository,
                    budgetRepository = budgetRepository,
                )
            }
        }
    }
}
