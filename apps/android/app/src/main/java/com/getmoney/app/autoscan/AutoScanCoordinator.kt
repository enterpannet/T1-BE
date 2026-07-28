package com.getmoney.app.autoscan

import android.net.Uri
import com.getmoney.app.ocr.SlipIntake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

interface AutoScanStoreReader {
    suspend fun isEnabled(): Boolean

    suspend fun isAutoSaveEnabled(): Boolean

    suspend fun getLastScanCursorEpochSec(): Long?

    suspend fun setLastScanCursorEpochSec(value: Long)

    suspend fun getExtraBucketIds(): Set<String>
}

interface GallerySlipScannerReader {
    suspend fun listNewImages(
        afterEpochSec: Long,
        extraBucketIds: Set<String> = emptySet(),
        limit: Int = 30,
    ): List<ScannedImage>

    /** Total image files in the given MediaStore buckets (no date filter). */
    suspend fun countImages(bucketIds: Set<String>): Int
}

fun interface SlipIntakeReader {
    suspend fun process(uri: Uri): SlipIntake.Outcome
}

class AutoScanCoordinator(
    private val store: AutoScanStoreReader,
    private val scanner: GallerySlipScannerReader,
    private val intake: SlipIntakeReader,
    private val autoPersister: SlipAutoPersister? = null,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val _queue = MutableStateFlow<List<QueuedSlip>>(emptyList())
    val queue: StateFlow<List<QueuedSlip>> = _queue.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    private val _lastAutoSaveSummary = MutableStateFlow<AutoSaveSummary?>(null)
    val lastAutoSaveSummary: StateFlow<AutoSaveSummary?> = _lastAutoSaveSummary.asStateFlow()

    private val _scanProgress = MutableStateFlow(ScanProgress())
    val scanProgress: StateFlow<ScanProgress> = _scanProgress.asStateFlow()

    /** Review / error slips from the last completed scan (for ZIP export). */
    private val _exportableReviewSlips = MutableStateFlow<List<QueuedSlip>>(emptyList())
    val exportableReviewSlips: StateFlow<List<QueuedSlip>> = _exportableReviewSlips.asStateFlow()

    private var scannedThisSession = false

    private val scanGeneration = AtomicInteger(0)
    private val pauseRequested = MutableStateFlow(false)
    private val stopRequested = AtomicBoolean(false)

    suspend fun runScanIfNeeded(hasPhotoPermission: Boolean) {
        if (!store.isEnabled() || !hasPhotoPermission) return
        if (scannedThisSession) return

        val now = clock()
        if (store.getLastScanCursorEpochSec() == null) {
            store.setLastScanCursorEpochSec(AutoScanCursor.initialCursor())
        }

        performSingleBatchScan(now)
        scannedThisSession = true
    }

    suspend fun runScanNow(hasPhotoPermission: Boolean) {
        if (!store.isEnabled() || !hasPhotoPermission) return

        val now = clock()
        if (store.getLastScanCursorEpochSec() == null) {
            store.setLastScanCursorEpochSec(AutoScanCursor.initialCursor())
        }

        performSingleBatchScan(now)
        scannedThisSession = true
    }

    /** Reset cursor and scan every image in selected folders in continuous batches. */
    suspend fun resetAndScanAllHistory(hasPhotoPermission: Boolean) {
        if (!store.isEnabled() || !hasPhotoPermission) return
        store.setLastScanCursorEpochSec(AutoScanCursor.BEGINNING_OF_HISTORY)
        performFullHistoryScan(clock())
        scannedThisSession = true
    }

    fun pauseScan() {
        if (_scanProgress.value.isActive) {
            pauseRequested.value = true
        }
    }

    fun resumeScan() {
        pauseRequested.value = false
    }

    fun stopScan() {
        stopRequested.set(true)
        pauseRequested.value = false
    }

    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    fun clearQueue() {
        scanGeneration.incrementAndGet()
        stopRequested.set(true)
        pauseRequested.value = false
        _queue.value = emptyList()
        _scanProgress.value = ScanProgress()
    }

    fun consumeAutoSaveSummary(): AutoSaveSummary? {
        val summary = _lastAutoSaveSummary.value
        _lastAutoSaveSummary.value = null
        return summary
    }

    fun skipCurrent(): QueuedSlip? {
        val current = _queue.value.firstOrNull() ?: return null
        _queue.value = _queue.value.drop(1)
        return current
    }

    fun peekCurrent(): QueuedSlip? = _queue.value.firstOrNull()

    fun removeCurrentAfterSave() {
        if (_queue.value.isNotEmpty()) {
            _queue.value = _queue.value.drop(1)
        }
    }

    /** Prefer live queue; fall back to last scan's review list. */
    fun slipsForZipExport(): List<QueuedSlip> {
        val live = _queue.value
        if (live.isNotEmpty()) return live
        return _exportableReviewSlips.value
    }

    private fun beginScanRun() {
        stopRequested.set(false)
        pauseRequested.value = false
        _lastAutoSaveSummary.value = null
    }

    private suspend fun performSingleBatchScan(now: Long) {
        beginScanRun()
        val generation = scanGeneration.get()
        val buckets = store.getExtraBucketIds()
        if (buckets.isEmpty()) {
            if (scanGeneration.get() != generation) return
            _queue.value = emptyList()
            _scanProgress.value = ScanProgress()
            return
        }
        val folderFileCount = scanner.countImages(buckets)
        publishProgress(ScanProgress(phase = ScanProgress.Phase.Listing, overallTotal = 0))
        val cursor = store.getLastScanCursorEpochSec() ?: return
        val images = scanner.listNewImages(
            afterEpochSec = cursor,
            extraBucketIds = buckets,
            limit = AutoScanCursor.BATCH_LIMIT,
        )
        val totals = AccTotals(folderFileCount = folderFileCount)
        val reviewAcc = mutableListOf<QueuedSlip>()
        val outcome = processBatch(
            images = images,
            generation = generation,
            scanStartedAt = now,
            advanceCursorOnComplete = true,
            overallBase = 0,
            overallTotal = 0,
            totals = totals,
            reviewAcc = reviewAcc,
        )
        when (outcome) {
            BatchOutcome.Cancelled -> {
                _scanProgress.value = ScanProgress()
            }
            BatchOutcome.Stopped, BatchOutcome.Completed -> {
                finishRun(totals, reviewAcc, replaceQueue = true)
            }
        }
    }

    private suspend fun performFullHistoryScan(now: Long) {
        beginScanRun()
        val generation = scanGeneration.get()
        val buckets = store.getExtraBucketIds()
        if (buckets.isEmpty()) {
            if (scanGeneration.get() != generation) return
            _queue.value = emptyList()
            _scanProgress.value = ScanProgress()
            return
        }
        val folderFileCount = scanner.countImages(buckets)
        val overallTotal = folderFileCount
        val totals = AccTotals(folderFileCount = folderFileCount)
        val reviewAcc = mutableListOf<QueuedSlip>()
        var overallBase = 0

        publishProgress(
            ScanProgress(
                phase = ScanProgress.Phase.Listing,
                overallCurrent = 0,
                overallTotal = overallTotal,
            ),
        )

        while (true) {
            val gate = awaitRunning(generation, totals, overallBase, overallTotal)
            if (gate == GateResult.Cancelled) {
                _scanProgress.value = ScanProgress()
                return
            }
            if (gate == GateResult.Stopped) {
                finishRun(totals, reviewAcc, replaceQueue = true)
                return
            }

            val cursor = store.getLastScanCursorEpochSec() ?: return
            publishProgress(
                ScanProgress(
                    phase = ScanProgress.Phase.Listing,
                    overallCurrent = overallBase,
                    overallTotal = overallTotal,
                    saved = totals.saved,
                    skipped = totals.skipped(),
                    needReview = totals.needReview,
                    failed = totals.failed,
                ),
            )
            val images = scanner.listNewImages(
                afterEpochSec = cursor,
                extraBucketIds = buckets,
                limit = AutoScanCursor.BATCH_LIMIT,
            )
            if (images.isEmpty()) {
                store.setLastScanCursorEpochSec(
                    AutoScanCursor.advanceAfterBatch(
                        inspectedDateAddedSecs = emptyList(),
                        scanStartedAtEpochSec = now,
                    ),
                )
                finishRun(totals, reviewAcc, replaceQueue = true)
                return
            }

            val outcome = processBatch(
                images = images,
                generation = generation,
                scanStartedAt = now,
                advanceCursorOnComplete = true,
                overallBase = overallBase,
                overallTotal = overallTotal,
                totals = totals,
                reviewAcc = reviewAcc,
            )
            when (outcome) {
                BatchOutcome.Cancelled -> {
                    _scanProgress.value = ScanProgress()
                    return
                }
                BatchOutcome.Stopped -> {
                    // Partial batch: do not advance cursor (already skipped inside processBatch).
                    finishRun(totals, reviewAcc, replaceQueue = true)
                    return
                }
                BatchOutcome.Completed -> {
                    overallBase += images.size
                    if (images.size < AutoScanCursor.BATCH_LIMIT) {
                        finishRun(totals, reviewAcc, replaceQueue = true)
                        return
                    }
                }
            }
        }
    }

    private fun finishRun(
        totals: AccTotals,
        reviewAcc: MutableList<QueuedSlip>,
        replaceQueue: Boolean,
    ) {
        if (replaceQueue) {
            _queue.value = reviewAcc.toList()
            _exportableReviewSlips.value = reviewAcc.toList()
        } else {
            _exportableReviewSlips.value = _queue.value
        }
        _bannerDismissed.value = _queue.value.isEmpty()
        _lastAutoSaveSummary.value = AutoSaveSummary(
            folderFileCount = totals.folderFileCount,
            saved = totals.saved,
            duplicates = totals.duplicates,
            needReview = totals.needReview,
            failed = totals.failed,
        )
        val doneTotal = if (totals.overallShownTotal > 0) {
            totals.overallShownTotal
        } else {
            totals.inspected
        }
        publishProgress(
            ScanProgress(
                phase = ScanProgress.Phase.Done,
                current = totals.inspected,
                total = totals.inspected,
                overallCurrent = totals.inspected,
                overallTotal = doneTotal,
                saved = totals.saved,
                skipped = totals.skipped(),
                needReview = totals.needReview,
                failed = totals.failed,
            ),
        )
        stopRequested.set(false)
        pauseRequested.value = false
    }

    private enum class BatchOutcome { Completed, Stopped, Cancelled }

    private enum class GateResult { Ok, Stopped, Cancelled }

    private data class AccTotals(
        val folderFileCount: Int,
        var saved: Int = 0,
        var duplicates: Int = 0,
        var needReview: Int = 0,
        var failed: Int = 0,
        var notSlip: Int = 0,
        var inspected: Int = 0,
        var overallShownTotal: Int = 0,
    ) {
        fun skipped(): Int = duplicates + notSlip
    }

    private suspend fun processBatch(
        images: List<ScannedImage>,
        generation: Int,
        scanStartedAt: Long,
        advanceCursorOnComplete: Boolean,
        overallBase: Int,
        overallTotal: Int,
        totals: AccTotals,
        reviewAcc: MutableList<QueuedSlip>,
    ): BatchOutcome {
        totals.overallShownTotal = overallTotal
        val batchSize = images.size
        val found = mutableListOf<QueuedSlip>()

        for ((index, image) in images.withIndex()) {
            when (awaitRunning(generation, totals, overallBase + index, overallTotal)) {
                GateResult.Cancelled -> return BatchOutcome.Cancelled
                GateResult.Stopped -> {
                    flushUnreadFound(found, reviewAcc, totals)
                    publishQueue(reviewAcc)
                    return BatchOutcome.Stopped
                }
                GateResult.Ok -> Unit
            }
            publishProgress(
                ScanProgress(
                    phase = ScanProgress.Phase.Reading,
                    current = index + 1,
                    total = batchSize,
                    overallCurrent = overallBase + index + 1,
                    overallTotal = overallTotal,
                    saved = totals.saved,
                    skipped = totals.skipped(),
                    needReview = totals.needReview,
                    failed = totals.failed,
                ),
            )
            val outcome = runCatching { intake.process(image.uri) }.getOrNull()
            totals.inspected++
            if (outcome == null || !SlipCandidateFilter.isCandidate(outcome)) {
                totals.notSlip++
                continue
            }
            found += QueuedSlip(image.uri, outcome.draft, outcome.source, outcome.rawText)
        }

        when (awaitRunning(generation, totals, overallBase + batchSize, overallTotal)) {
            GateResult.Cancelled -> return BatchOutcome.Cancelled
            GateResult.Stopped -> {
                flushUnreadFound(found, reviewAcc, totals)
                publishQueue(reviewAcc)
                return BatchOutcome.Stopped
            }
            GateResult.Ok -> Unit
        }

        if (store.isAutoSaveEnabled() && autoPersister != null && found.isNotEmpty()) {
            val pending = found.toMutableList()
            while (pending.isNotEmpty()) {
                when (awaitRunning(generation, totals, overallBase + batchSize, overallTotal)) {
                    GateResult.Cancelled -> return BatchOutcome.Cancelled
                    GateResult.Stopped -> {
                        // Remaining candidates → review queue
                        totals.needReview += pending.size
                        reviewAcc += pending
                        publishQueue(reviewAcc)
                        return BatchOutcome.Stopped
                    }
                    GateResult.Ok -> Unit
                }
                val item = pending.removeAt(0)
                val done = found.size - pending.size
                publishProgress(
                    ScanProgress(
                        phase = ScanProgress.Phase.Saving,
                        current = done,
                        total = found.size,
                        overallCurrent = overallBase + batchSize,
                        overallTotal = overallTotal,
                        saved = totals.saved,
                        skipped = totals.skipped(),
                        needReview = totals.needReview,
                        failed = totals.failed,
                    ),
                )
                when (autoPersister.persist(item)) {
                    AutoSaveItemResult.Saved -> totals.saved++
                    AutoSaveItemResult.Duplicate -> totals.duplicates++
                    AutoSaveItemResult.NeedsReview -> {
                        totals.needReview++
                        reviewAcc += item
                    }
                    AutoSaveItemResult.Failed -> {
                        totals.failed++
                        reviewAcc += item
                    }
                }
                publishQueue(reviewAcc)
            }
        } else {
            if (!store.isAutoSaveEnabled()) {
                totals.needReview += found.size
            }
            reviewAcc += found
            publishQueue(reviewAcc)
        }

        if (advanceCursorOnComplete) {
            store.setLastScanCursorEpochSec(
                AutoScanCursor.advanceAfterBatch(
                    inspectedDateAddedSecs = images.map { it.dateAddedSec },
                    scanStartedAtEpochSec = scanStartedAt,
                ),
            )
        }
        return BatchOutcome.Completed
    }

    private fun flushUnreadFound(
        found: List<QueuedSlip>,
        reviewAcc: MutableList<QueuedSlip>,
        totals: AccTotals,
    ) {
        if (found.isEmpty()) return
        totals.needReview += found.size
        reviewAcc += found
    }

    private fun publishQueue(reviewAcc: List<QueuedSlip>) {
        _queue.value = reviewAcc.toList()
        _bannerDismissed.value = reviewAcc.isEmpty()
    }

    private suspend fun awaitRunning(
        generation: Int,
        totals: AccTotals,
        overallCurrent: Int,
        overallTotal: Int,
    ): GateResult {
        while (true) {
            if (scanGeneration.get() != generation) return GateResult.Cancelled
            if (stopRequested.get()) return GateResult.Stopped
            if (!pauseRequested.value) return GateResult.Ok
            publishProgress(
                ScanProgress(
                    phase = ScanProgress.Phase.Paused,
                    current = overallCurrent,
                    total = if (overallTotal > 0) overallTotal else totals.inspected,
                    overallCurrent = overallCurrent,
                    overallTotal = overallTotal,
                    saved = totals.saved,
                    skipped = totals.skipped(),
                    needReview = totals.needReview,
                    failed = totals.failed,
                ),
            )
            pauseRequested.first { !it || stopRequested.get() || scanGeneration.get() != generation }
        }
    }

    private fun publishProgress(progress: ScanProgress) {
        _scanProgress.value = progress
    }
}
