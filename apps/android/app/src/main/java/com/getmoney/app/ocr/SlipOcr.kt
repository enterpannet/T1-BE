package com.getmoney.app.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class SlipOcr(
    private val context: Context,
) {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /**
     * Returns OCR text with line breaks preserved (better for amount/keyword pairing).
     * ML Kit Latin model still reads Latin digits/words on Thai slips; Thai glyphs may be noisy.
     */
    suspend fun recognize(uri: Uri): String = withContext(Dispatchers.IO) {
        val image = InputImage.fromFilePath(context, uri)
        val result = recognizer.process(image).awaitTask()
        // Prefer structured lines over flattened text blob
        val lines = result.textBlocks
            .flatMap { it.lines }
            .map { it.text.trim() }
            .filter { it.isNotEmpty() }
        if (lines.isNotEmpty()) {
            lines.joinToString("\n")
        } else {
            result.text
        }
    }

    private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
        suspendCancellableCoroutine { continuation ->
            addOnSuccessListener { value ->
                if (continuation.isActive) {
                    continuation.resume(value)
                }
            }
            addOnFailureListener { error ->
                if (continuation.isActive) {
                    continuation.resumeWithException(error)
                }
            }
        }
}
