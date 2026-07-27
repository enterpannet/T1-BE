package com.getmoney.app.autoscan

object AutoScanCursor {
    /** Start of history — scan from the oldest MediaStore images upward. */
    const val BEGINNING_OF_HISTORY: Long = 0L

    /** Batch size when catching up on gallery history. */
    const val BATCH_LIMIT: Int = 100

    fun initialCursor(): Long = BEGINNING_OF_HISTORY

    /**
     * Advance past images already inspected in this batch.
     * Empty batch → [scanStartedAtEpochSec] (caught up to "now").
     */
    fun advanceAfterBatch(
        inspectedDateAddedSecs: List<Long>,
        scanStartedAtEpochSec: Long,
    ): Long {
        if (inspectedDateAddedSecs.isEmpty()) return scanStartedAtEpochSec
        return inspectedDateAddedSecs.maxOrNull() ?: scanStartedAtEpochSec
    }
}
