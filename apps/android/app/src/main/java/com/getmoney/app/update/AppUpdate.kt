package com.getmoney.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.getmoney.app.BuildConfig
import com.getmoney.app.data.api.AppUpdateApi
import com.getmoney.app.data.api.AppVersionResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

object AppUpdate {
    fun isNewer(remote: AppVersionResponse, localVersionCode: Int = BuildConfig.VERSION_CODE): Boolean =
        remote.versionCode > localVersionCode

    private val downloadClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.MINUTES)
        .writeTimeout(2, TimeUnit.MINUTES)
        .build()

    suspend fun fetchIfNewer(api: AppUpdateApi): AppVersionResponse? = withContext(Dispatchers.IO) {
        runCatching { api.version() }.getOrNull()?.takeIf { isNewer(it) }
    }

    /**
     * Download APK to cache/updates/. [onProgress] receives 0f..1f when Content-Length is known.
     */
    suspend fun downloadApk(
        context: Context,
        apkUrl: String,
        onProgress: (Float?) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "update.apk")
        if (out.exists()) out.delete()

        val request = Request.Builder().url(apkUrl).get().build()
        downloadClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("ดาวน์โหลดไม่สำเร็จ (${response.code})")
            }
            val body = response.body ?: error("ไฟล์ว่าง")
            val total = body.contentLength().takeIf { it > 0 }
            body.byteStream().use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var readTotal = 0L
                    while (true) {
                        val n = input.read(buffer)
                        if (n <= 0) break
                        output.write(buffer, 0, n)
                        readTotal += n
                        if (total != null) {
                            onProgress((readTotal.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        } else {
                            onProgress(null)
                        }
                    }
                    output.flush()
                }
            }
        }
        onProgress(1f)
        out
    }

    fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun canRequestInstall(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installPermissionSettingsIntent(context: Context): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
