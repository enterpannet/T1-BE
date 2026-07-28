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
import com.getmoney.app.autoscan.AutoScanCoordinator
import com.getmoney.app.autoscan.AutoScanStore
import com.getmoney.app.autoscan.GallerySlipScanner
import com.getmoney.app.autoscan.SlipIntakeReader
import com.getmoney.app.autoscan.TransactionSlipAutoPersister
import com.getmoney.app.data.cloudinary.CloudUploadStore
import com.getmoney.app.data.cloudinary.CloudinaryUploader
import com.getmoney.app.data.identity.MyIdentityStore
import com.getmoney.app.data.slipimage.SlipImageStore
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
        val slipImageStore = SlipImageStore(applicationContext)
        val transactionRepository = TransactionRepository(
            transactionApi = apiClient.createService(TransactionApi::class.java),
            slipImageStore = slipImageStore,
        )
        val cloudUploadStore = CloudUploadStore(applicationContext)
        val cloudinaryUploader = CloudinaryUploader()
        val myIdentityStore = MyIdentityStore(applicationContext)
        val slipIntake = SlipIntake(
            qrScanner = SlipQrScanner(applicationContext),
            ocr = SlipOcr(applicationContext),
            myIdentityStore = myIdentityStore,
        )
        val autoScanStore = AutoScanStore(applicationContext)
        val gallerySlipScanner = GallerySlipScanner(applicationContext)
        val autoScanCoordinator = AutoScanCoordinator(
            store = autoScanStore,
            scanner = gallerySlipScanner,
            intake = SlipIntakeReader { uri -> slipIntake.process(uri) },
            autoPersister = TransactionSlipAutoPersister(
                transactionRepository = transactionRepository,
                slipImageStore = slipImageStore,
            ),
        )

        setContent {
            GetMoneyTheme {
                AppNav(
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
