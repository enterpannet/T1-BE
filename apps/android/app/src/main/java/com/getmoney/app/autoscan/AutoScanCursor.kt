package com.getmoney.app.autoscan

import kotlin.math.max

object AutoScanCursor {
    /** Start of history — scan from the oldest MediaStore images upward. */
    const val BEGINNING_OF_HISTORY: Long = 0L

    /** Batch size when catching up on gallery history. */
    const val BATCH_LIMIT: Int = 100

    /** Default Scan now / auto-scan lookback window. */
    const val DEFAULT_LOOKBACK_DAYS: Int = 7

    private const val SECONDS_PER_DAY: Long = 24L * 60L * 60L

    /** Full-history reset cursor (not used for default lookback scans). */
    fun initialCursor(): Long = BEGINNING_OF_HISTORY

    fun lookbackFloorEpochSec(nowEpochSec: Long): Long =
        nowEpochSec - DEFAULT_LOOKBACK_DAYS * SECONDS_PER_DAY

    /**
     * Cursor used for default scans: never older than [lookbackFloorEpochSec].
     * Null or ancient stored cursors jump to the lookback floor.
     */
    fun effectiveCursor(stored: Long?, nowEpochSec: Long): Long {
        val floor = lookbackFloorEpochSec(nowEpochSec)
        return max(stored ?: floor, floor)
    }

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
