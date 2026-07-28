package com.getmoney.app.autoscan

/**
 * Live progress while a gallery scan is running.
 * [current]/[total] are the current batch (OCR/save pass).
 * [overallCurrent]/[overallTotal] span the whole full-history run when set.
 */
data class ScanProgress(
    val phase: Phase = Phase.Idle,
    val current: Int = 0,
    val total: Int = 0,
    val overallCurrent: Int = 0,
    val overallTotal: Int = 0,
    val saved: Int = 0,
    val skipped: Int = 0,
    val needReview: Int = 0,
    val failed: Int = 0,
) {
    enum class Phase {
        Idle,
        Listing,
        Reading,
        Saving,
        Paused,
        Done,
    }

    val isActive: Boolean
        get() = phase != Phase.Idle && phase != Phase.Done

    fun statusLine(): String {
        val parts = mutableListOf<String>()
        val readCurrent = if (overallTotal > 0) overallCurrent else current
        val readTotal = if (overallTotal > 0) overallTotal else total
        when (phase) {
            Phase.Listing -> parts += "กำลังเตรียมรายการรูป…"
            Phase.Reading -> parts += "กำลังอ่าน $readCurrent/$readTotal"
            Phase.Saving -> {
                val saveCurrent = if (overallTotal > 0) overallCurrent else current
                val saveTotal = if (overallTotal > 0) overallTotal else total
                parts += "กำลังบันทึก $saveCurrent/$saveTotal"
            }
            Phase.Paused -> parts += "พักไว้ · อ่านแล้ว $readCurrent/$readTotal"
            Phase.Done -> parts += if (readTotal > 0) {
                "สแกนครบ $readTotal รูป"
            } else if (total > 0) {
                "สแกนครบ $total รูป"
            } else {
                "สแกนเสร็จ"
            }
            Phase.Idle -> return ""
        }
        if (saved > 0) parts += "สำเร็จ $saved"
        if (skipped > 0) parts += "ข้าม $skipped"
        val reviewOrError = needReview + failed
        if (reviewOrError > 0) {
            parts += if (failed > 0 && needReview > 0) {
                "ต้องตรวจ $needReview · error $failed"
            } else if (failed > 0) {
                "error $failed"
            } else {
                "ต้องตรวจ $needReview"
            }
        }
        return parts.joinToString(" · ")
    }
}
