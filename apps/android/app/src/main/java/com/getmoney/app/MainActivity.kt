package com.getmoney.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.getmoney.app.data.api.ApiClient
import com.getmoney.app.data.api.BudgetApi
import com.getmoney.app.data.api.SummaryApi
import com.getmoney.app.data.api.TransactionApi
import com.getmoney.app.data.auth.AuthRepository
import com.getmoney.app.data.auth.TokenStore
import com.getmoney.app.data.budget.BudgetRepository
import com.getmoney.app.data.tx.TransactionRepository
import com.getmoney.app.ocr.SlipIntake
import com.getmoney.app.ocr.SlipOcr
import com.getmoney.app.ocr.SlipQrScanner
import com.getmoney.app.ui.nav.AppNav
import com.getmoney.app.ui.theme.GetMoneyTheme

class MainActivity : ComponentActivity() {
    private var sharedImageUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        sharedImageUri = extractShareUri(intent)

        val tokenStore = TokenStore(applicationContext)
        val apiClient = ApiClient(tokenStore)
        val authRepository = AuthRepository(apiClient.authApi, tokenStore)
        val budgetRepository = BudgetRepository(
            budgetApi = apiClient.createService(BudgetApi::class.java),
            summaryApi = apiClient.createService(SummaryApi::class.java),
        )
        val transactionRepository = TransactionRepository(
            transactionApi = apiClient.createService(TransactionApi::class.java),
        )
        val slipIntake = SlipIntake(
            qrScanner = SlipQrScanner(applicationContext),
            ocr = SlipOcr(applicationContext),
        )

        setContent {
            GetMoneyTheme {
                AppNav(
                    authRepository = authRepository,
                    budgetRepository = budgetRepository,
                    transactionRepository = transactionRepository,
                    slipIntake = slipIntake,
                    sharedImageUri = sharedImageUri,
                    onShareUriConsumed = { sharedImageUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        sharedImageUri = extractShareUri(intent)
    }

    private fun extractShareUri(intent: Intent?): Uri? {
        if (intent?.action != Intent.ACTION_SEND) return null
        if (intent.type?.startsWith("image/") != true) return null
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}
