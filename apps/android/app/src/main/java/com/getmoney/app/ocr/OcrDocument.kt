package com.getmoney.app.ocr

/** One OCR line with approximate position (Paddle box center / left). */
data class OcrLine(
    val text: String,
    val yCenter: Float,
    val xLeft: Float = 0f,
    val confidence: Float = 1f,
)

data class OcrDocument(
    val text: String,
    val lines: List<OcrLine>,
    val engine: Engine,
) {
    enum class Engine {
        Paddle,
        Tesseract,
    }
}
