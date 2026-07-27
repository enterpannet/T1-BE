package com.getmoney.app.autoscan

import android.net.Uri
import com.getmoney.app.ocr.SlipIntake
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicInteger

interface AutoScanStoreReader {
    suspend fun isEnabled(): Boolean

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
}

fun interface SlipIntakeReader {
    suspend fun process(uri: Uri): SlipIntake.Outcome
}

class AutoScanCoordinator(
    private val store: AutoScanStoreReader,
    private val scanner: GallerySlipScannerReader,
    private val intake: SlipIntakeReader,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    private val _queue = MutableStateFlow<List<QueuedSlip>>(emptyList())
    val queue: StateFlow<List<QueuedSlip>> = _queue.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    private var scannedThisSession = false

    private val scanGeneration = AtomicInteger(0)

    suspend fun runScanIfNeeded(hasPhotoPermission: Boolean) {
        if (!store.isEnabled() || !hasPhotoPermission) return
        if (scannedThisSession) return

        val now = clock()
        if (store.getLastScanCursorEpochSec() == null) {
            store.setLastScanCursorEpochSec(AutoScanCursor.initialCursor())
        }

        performScan(now)
        scannedThisSession = true
    }

    suspend fun runScanNow(hasPhotoPermission: Boolean) {
        if (!store.isEnabled() || !hasPhotoPermission) return

        val now = clock()
        if (store.getLastScanCursorEpochSec() == null) {
            store.setLastScanCursorEpochSec(AutoScanCursor.initialCursor())
        }

        performScan(now)
        scannedThisSession = true
    }

    /** Reset cursor to the start of gallery history and scan the next batch. */
    suspend fun resetAndScanAllHistory(hasPhotoPermission: Boolean) {
        if (!store.isEnabled() || !hasPhotoPermission) return
        store.setLastScanCursorEpochSec(AutoScanCursor.BEGINNING_OF_HISTORY)
        performScan(clock())
        scannedThisSession = true
    }

    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    fun clearQueue() {
        scanGeneration.incrementAndGet()
        _queue.value = emptyList()
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

    private suspend fun performScan(now: Long) {
        val generation = scanGeneration.get()
        val scanStartedAt = now
        val cursor = store.getLastScanCursorEpochSec() ?: return
        val images = scanner.listNewImages(
            afterEpochSec = cursor,
            extraBucketIds = store.getExtraBucketIds(),
            limit = AutoScanCursor.BATCH_LIMIT,
        )
        val found = mutableListOf<QueuedSlip>()
        for (image in images) {
            val outcome = runCatching { intake.process(image.uri) }.getOrNull() ?: continue
            if (!SlipCandidateFilter.isCandidate(outcome)) continue
            found += QueuedSlip(image.uri, outcome.draft, outcome.source)
        }
        if (scanGeneration.get() != generation) return
        _queue.value = found
        _bannerDismissed.value = false
        store.setLastScanCursorEpochSec(
            AutoScanCursor.advanceAfterBatch(
                inspectedDateAddedSecs = images.map { it.dateAddedSec },
                scanStartedAtEpochSec = scanStartedAt,
            ),
        )
    }
}
