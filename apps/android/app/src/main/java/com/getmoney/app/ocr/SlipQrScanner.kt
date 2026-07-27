package com.getmoney.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SlipQrScanner(
    context: Context,
) {
    // context reserved for future InputImage helpers / content resolver
    @Suppress("unused")
    private val appContext = context.applicationContext

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build(),
    )

    suspend fun scanPayloads(uri: Uri): List<String> = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(appContext, uri)
        val barcodes = scanner.process(image).awaitTask()
        barcodes.mapNotNull { barcode ->
            barcode.rawValue?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { value ->
                if (continuation.isActive) continuation.resume(value)
            }
            addOnFailureListener { error ->
                if (continuation.isActive) continuation.resumeWithException(error)
            }
        }
}
