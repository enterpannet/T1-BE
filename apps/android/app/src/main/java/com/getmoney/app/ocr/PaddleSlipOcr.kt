package com.getmoney.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * On-device PaddleOCR (PP-OCRv5 mobile det + Thai rec).
 * Models live in app assets under `models/det` and `models/rec`.
 */
class PaddleSlipOcr(
    private val context: Context,
) {
    private val mutex = Mutex()
    private var ocr: PaddleOCR? = null
    private var initFailed = false

    suspend fun recognize(bitmap: Bitmap): String? =
        recognizeDocument(bitmap)?.text

    suspend fun recognizeDocument(bitmap: Bitmap): OcrDocument? = mutex.withLock {
        val engine = ensureEngine() ?: return@withLock null
        return@withLock runCatching {
            val results = engine.recognize(bitmap).results
            val lines = results.mapNotNull { result ->
                val text = result.text.trim()
                if (text.isEmpty()) return@mapNotNull null
                val pts = result.box.points
                val y = pts.map { it.y }.average().toFloat()
                val x = pts.minOf { it.x }
                OcrLine(
                    text = text,
                    yCenter = y,
                    xLeft = x,
                    confidence = result.confidence,
                )
            }.sortedWith(compareBy({ it.yCenter }, { it.xLeft }))
            if (lines.isEmpty()) return@runCatching null
            OcrDocument(
                text = lines.joinToString("\n") { it.text },
                lines = lines,
                engine = OcrDocument.Engine.Paddle,
            )
        }.onFailure { err ->
            Log.w(TAG, "PaddleOCR recognize failed", err)
        }.getOrNull()
    }

    private suspend fun ensureEngine(): PaddleOCR? {
        if (initFailed) return null
        ocr?.let { return it }
        return try {
            check(OpenCVUtils.init(context)) { "OpenCV init failed" }
            val created = PaddleOCR.create(
                context = context.applicationContext,
                config = PaddleOCRConfig(
                    recScoreThresh = 0.3f,
                    recBatchSize = 1,
                ),
                engineConfig = EngineConfig(numThreads = 4),
                detModelAssetPath = "models/det/inference.onnx",
                recModelAssetPath = "models/rec/inference.onnx",
                recConfigAssetPath = "models/rec/inference.yml",
            )
            ocr = created
            created
        } catch (t: Throwable) {
            Log.e(TAG, "PaddleOCR init failed; will use Tesseract fallback", t)
            initFailed = true
            null
        }
    }

    companion object {
        private const val TAG = "PaddleSlipOcr"
    }
}
