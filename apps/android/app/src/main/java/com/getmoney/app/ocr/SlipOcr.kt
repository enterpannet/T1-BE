package com.getmoney.app.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import android.util.Log
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.math.max

/**
 * Hybrid on-device OCR:
 * 1) PaddleOCR Thai (primary) — keeps line boxes for KBank layout
 * 2) Tesseract tha+eng (fallback) — synthetic Y from line index
 * Images never leave the device.
 */
class SlipOcr(
    private val context: Context,
) {
    private val mutex = Mutex()
    private val paddle = PaddleSlipOcr(context)
    private var tess: TessBaseAPI? = null

    suspend fun recognize(uri: Uri): String = recognizeDocument(uri).text

    suspend fun recognizeDocument(uri: Uri): OcrDocument = withContext(Dispatchers.IO) {
        mutex.withLock {
            val loaded = loadBitmap(uri) ?: return@withLock OcrDocument("", emptyList(), OcrDocument.Engine.Tesseract)
            // Paddle Thai models work best on natural color slips — avoid grayscale wash-out
            // of small header dates like "4 ก.ค. 69 19:24 น."
            val paddleInput = upscaleIfSmall(loaded)
            try {
                val paddleDoc = paddle.recognizeDocument(paddleInput)
                if (paddleDoc != null && looksUseful(paddleDoc.text)) {
                    // Don't trust date-looking OCR alone — only short-circuit when parser
                    // actually recovers spentAt (K+ txn ids with OCR "I" for "1" often fail).
                    if (parserRecoversSpentAt(paddleDoc)) {
                        return@withLock paddleDoc
                    }
                    // Header date is tiny on K+ — second pass on top band only.
                    var enriched = enrichWithTopCropDate(paddleInput, paddleDoc)
                    if (parserRecoversSpentAt(enriched)) {
                        return@withLock enriched
                    }
                    val forTess = preprocessForTesseract(paddleInput)
                    val tessDoc = try {
                        recognizeWithTesseract(forTess)
                    } finally {
                        if (forTess !== paddleInput) forTess.recycle()
                    }
                    enriched = mergePreferringDate(enriched, tessDoc)
                    return@withLock enriched
                }
                Log.i(TAG, "Falling back to Tesseract (paddle empty/weak)")
                val forTess = preprocessForTesseract(paddleInput)
                try {
                    recognizeWithTesseract(forTess)
                } finally {
                    if (forTess !== paddleInput) forTess.recycle()
                }
            } finally {
                if (paddleInput !== loaded) paddleInput.recycle()
                loaded.recycle()
            }
        }
    }

    /** Prefer Paddle layout/parties; borrow spentAt line coverage from Tess when needed. */
    private fun mergePreferringDate(paddle: OcrDocument, tess: OcrDocument): OcrDocument {
        if (parserRecoversSpentAt(paddle) || !hasLikelySlipDate(tess.text)) return paddle
        val combined = paddle.text + "\n" + tess.text
        return paddle.copy(text = combined)
    }

    private fun parserRecoversSpentAt(doc: OcrDocument): Boolean =
        !SlipParser.parse(doc).spentAtIso.isNullOrBlank()

    /** Crop top ~28% (status + date) and merge any recovered datetime lines. */
    private suspend fun enrichWithTopCropDate(full: Bitmap, base: OcrDocument): OcrDocument {
        val cropH = (full.height * 0.28f).toInt().coerceIn(80, full.height)
        if (cropH >= full.height) return base
        val top = Bitmap.createBitmap(full, 0, 0, full.width, cropH)
        return try {
            val topDoc = paddle.recognizeDocument(top) ?: return base
            if (!hasLikelySlipDate(topDoc.text) && topDoc.text.length < 8) return base
            val combined = topDoc.text + "\n" + base.text
            val topLines = topDoc.lines.map { it.copy(yCenter = it.yCenter * 0.28f) }
            base.copy(text = combined, lines = topLines + base.lines)
        } finally {
            top.recycle()
        }
    }

    private fun hasLikelySlipDate(text: String): Boolean {
        if (thaiMonthDateHint.containsMatchIn(text)) return true
        if (looseDateHint.containsMatchIn(text)) return true
        if (kbankTxnIdHint.containsMatchIn(text.replace(Regex("""(?i)(?<![0-9A-Za-z])O(?=16\d)"""), "0"))) {
            return true
        }
        if (Regex("""(?<!\d)20[2-3]\d(?:0[1-9]|1[0-2])(?:0[1-9]|[12]\d|3[01])\d{4,}""").containsMatchIn(text)) {
            return true
        }
        if (Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\s+\d{1,2}[:.]\d{2}""").containsMatchIn(text)) {
            return true
        }
        return false
    }

    private val thaiMonthDateHint = Regex(
        """\d{1,2}\s*(?:ม\.?\s*ค|ก\.?\s*พ|มี\.?\s*ค|เม\.?\s*ย|พ\.?\s*ค|มิ\.?\s*ย|ก\.?\s*ค|ส\.?\s*ค|ก\.?\s*ย|ต\.?\s*ค|พ\.?\s*ย|ธ\.?\s*ค)""",
    )

    private val looseDateHint = Regex(
        """\d{1,2}\s+\S{0,12}?\s*(?:69|6[0-9]|25\d{2})\s+\d{1,2}[:.]\d{2}""",
    )

    private val kbankTxnIdHint = Regex(
        """(?i)(?<![0-9A-Z])[O0]?16[0-9OIl|]{8,}[A-Z0-9OIl|]*""",
    )

    private fun recognizeWithTesseract(bitmap: Bitmap): OcrDocument {
        val api = ensureTess()
        api.setImage(bitmap)
        val lines = api.getUTF8Text()
            ?.lines()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val ocrLines = lines.mapIndexed { index, text ->
            OcrLine(text = text, yCenter = index * 10f, xLeft = 0f)
        }
        return OcrDocument(
            text = lines.joinToString("\n"),
            lines = ocrLines,
            engine = OcrDocument.Engine.Tesseract,
        )
    }

    private fun ensureTess(): TessBaseAPI {
        tess?.let { return it }
        val dataPath = TessDataInstaller.ensureDataPath(context)
        val api = TessBaseAPI()
        check(api.init(dataPath, "tha+eng")) {
            "Failed to initialize Tesseract (tha+eng)"
        }
        api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
        runCatching { api.setVariable("preserve_interword_spaces", "1") }
        tess = api
        return api
    }

    private fun loadBitmap(uri: Uri): Bitmap? {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        val maxSide = max(bounds.outWidth, bounds.outHeight).coerceAtLeast(1)
        while (maxSide / sample > MAX_SIDE_PX) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    /** Color-preserving upscale for small gallery thumbs (Paddle path). */
    private fun upscaleIfSmall(src: Bitmap): Bitmap {
        val maxSide = max(src.width, src.height)
        if (maxSide >= 900) return src
        val scale = 2
        val tw = (src.width * scale).coerceAtLeast(1)
        val th = (src.height * scale).coerceAtLeast(1)
        return Bitmap.createScaledBitmap(src, tw, th, true)
    }

    /** Grayscale + contrast only for Tesseract fallback. */
    private fun preprocessForTesseract(src: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val matrix = ColorMatrix().apply {
            setSaturation(0f)
            val contrast = 1.15f
            val translate = (-0.5f * contrast + 0.5f) * 255f
            postConcat(
                ColorMatrix(
                    floatArrayOf(
                        contrast, 0f, 0f, 0f, translate,
                        0f, contrast, 0f, 0f, translate,
                        0f, 0f, contrast, 0f, translate,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(matrix)
        }
        canvas.drawBitmap(src, 0f, 0f, paint)
        return out
    }

    /** Prefer Paddle when it produced enough content (esp. Thai letters or digits). */
    private fun looksUseful(text: String): Boolean {
        val letters = text.count { it.isLetter() || it in '\u0E00'..'\u0E7F' }
        val digits = text.count { it.isDigit() }
        return text.length >= 8 && (letters + digits) >= 6
    }

    companion object {
        private const val MAX_SIDE_PX = 2200
        private const val TAG = "SlipOcr"
    }
}
