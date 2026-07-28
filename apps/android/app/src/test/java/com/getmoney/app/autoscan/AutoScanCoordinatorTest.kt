package com.getmoney.app.autoscan

import android.net.Uri
import com.getmoney.app.ocr.SlipDraft
import com.getmoney.app.ocr.SlipIntake
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class AutoScanCoordinatorTest {
    private val nowEpochSec = 1_700_000_000L
    private val scanEpochSec = 1_700_000_100L

    private val uri1 = Uri.parse("content://media/external/images/media/1")
    private val uri2 = Uri.parse("content://media/external/images/media/2")
    private val uri3 = Uri.parse("content://media/external/images/media/3")
    private val uri4 = Uri.parse("content://media/external/images/media/4")

    @Test
    fun emptyBucketsSkipsScanAndDoesNotAdvanceCursor() = runBlocking {
        val store = FakeAutoScanStore(enabled = true, cursor = 1_000L, extraBucketIds = emptySet())
        val scanner = FakeGallerySlipScanner(
            images = listOf(scannedImage(uri1, dateAddedSec = 1_500L)),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(candidateOutcome("100.00", SlipIntake.Source.Qr)),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        coordinator.runScanIfNeeded(hasPhotoPermission = true)

        assertFalse(scanner.listCalled)
        assertTrue(coordinator.queue.value.isEmpty())
        assertEquals(1_000L, store.cursor)
    }

    @Test
    fun firstRunUsesLookbackFloorAndScans() = runBlocking {
        val floor = AutoScanCursor.lookbackFloorEpochSec(nowEpochSec)
        val recent = floor + 1_000L
        val store = FakeAutoScanStore(enabled = true, cursor = null)
        val scanner = FakeGallerySlipScanner(
            images = listOf(scannedImage(uri1, dateAddedSec = recent)),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(candidateOutcome("100.00", SlipIntake.Source.Qr)),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { nowEpochSec },
        )

        coordinator.runScanIfNeeded(hasPhotoPermission = true)

        assertTrue(scanner.listCalled)
        assertEquals(floor, scanner.lastAfterEpochSec)
        assertEquals(recent, store.cursor)
        assertEquals(1, coordinator.queue.value.size)
    }

    @Test
    fun ancientCursorIsFlooredToLookback() = runBlocking {
        val floor = AutoScanCursor.lookbackFloorEpochSec(scanEpochSec)
        val recent = floor + 500L
        val store = FakeAutoScanStore(enabled = true, cursor = 0L)
        val scanner = FakeGallerySlipScanner(
            images = listOf(
                scannedImage(uri1, dateAddedSec = 1_500L),
                scannedImage(uri2, dateAddedSec = recent),
            ),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(candidateOutcome("100.00", SlipIntake.Source.Qr)),
                uri2 to FakeOutcome.Success(candidateOutcome("200.00", SlipIntake.Source.Qr)),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        coordinator.runScanNow(hasPhotoPermission = true)

        assertEquals(floor, scanner.lastAfterEpochSec)
        assertEquals(1, coordinator.queue.value.size)
        assertEquals(uri2, coordinator.queue.value.single().uri)
        assertEquals(recent, store.cursor)
    }

    @Test
    fun secondRunScansFiltersQueueAndAdvancesCursor() = runBlocking {
        val store = FakeAutoScanStore(enabled = true, cursor = 1_699_999_900L)
        val scanner = FakeGallerySlipScanner(
            images = listOf(
                scannedImage(uri1, dateAddedSec = 1_699_999_950L),
                scannedImage(uri2, dateAddedSec = 1_699_999_980L),
            ),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(candidateOutcome("100.00", SlipIntake.Source.Qr)),
                uri2 to FakeOutcome.Success(candidateOutcome("250.50", SlipIntake.Source.Ocr, bank = "SCB")),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        coordinator.runScanIfNeeded(hasPhotoPermission = true)

        assertTrue(scanner.listCalled)
        assertEquals(1_699_999_900L, scanner.lastAfterEpochSec)
        assertEquals(2, coordinator.queue.value.size)
        assertEquals(uri1, coordinator.queue.value[0].uri)
        assertEquals(uri2, coordinator.queue.value[1].uri)
        assertEquals(1_699_999_980L, store.cursor)
        assertFalse(coordinator.bannerDismissed.value)
    }

    @Test
    fun emptyBatchAdvancesCursorToNow() = runBlocking {
        val recentCursor = scanEpochSec - 10_000L
        val store = FakeAutoScanStore(enabled = true, cursor = recentCursor)
        val scanner = FakeGallerySlipScanner(images = emptyList())
        val intake = FakeSlipIntake()
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        coordinator.runScanIfNeeded(hasPhotoPermission = true)

        assertEquals(scanEpochSec, store.cursor)
        assertTrue(coordinator.queue.value.isEmpty())
    }

    @Test
    fun resetAndScanAllHistoryRestartsFromBeginning() = runBlocking {
        val store = FakeAutoScanStore(enabled = true, cursor = scanEpochSec)
        val scanner = FakeGallerySlipScanner(
            images = listOf(scannedImage(uri1, dateAddedSec = 2_000L)),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(candidateOutcome("100.00", SlipIntake.Source.Qr)),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        coordinator.resetAndScanAllHistory(hasPhotoPermission = true)

        assertEquals(0L, scanner.lastAfterEpochSec)
        assertEquals(2_000L, store.cursor)
        assertEquals(1, coordinator.queue.value.size)
    }

    @Test
    fun intakeFailureAndNonCandidatesAreSkipped() = runBlocking {
        val base = scanEpochSec - 50_000L
        val store = FakeAutoScanStore(enabled = true, cursor = base)
        val scanner = FakeGallerySlipScanner(
            images = listOf(
                scannedImage(uri1, dateAddedSec = base + 1),
                scannedImage(uri2, dateAddedSec = base + 2),
                scannedImage(uri3, dateAddedSec = base + 3),
            ),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Failure,
                uri2 to FakeOutcome.Success(
                    candidateOutcome("42.00", SlipIntake.Source.Ocr, rawText = "Happy birthday"),
                ),
                uri3 to FakeOutcome.Success(candidateOutcome("500.00", SlipIntake.Source.Qr, reference = "REF")),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        coordinator.runScanIfNeeded(hasPhotoPermission = true)

        assertEquals(1, coordinator.queue.value.size)
        assertEquals(uri3, coordinator.queue.value.single().uri)
    }

    @Test
    fun runScanIfNeededScansOnlyOncePerSession() = runBlocking {
        val base = scanEpochSec - 50_000L
        val store = FakeAutoScanStore(enabled = true, cursor = base)
        val scanner = FakeGallerySlipScanner(
            images = listOf(scannedImage(uri1, dateAddedSec = base + 1)),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(candidateOutcome("100.00", SlipIntake.Source.Qr)),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        coordinator.runScanIfNeeded(hasPhotoPermission = true)
        coordinator.runScanIfNeeded(hasPhotoPermission = true)

        assertEquals(1, scanner.listCallCount)
    }

    @Test
    fun runScanNowAlwaysScansEvenAfterSessionScan() = runBlocking {
        val base = scanEpochSec - 50_000L
        val store = FakeAutoScanStore(enabled = true, cursor = base)
        val scanner = FakeGallerySlipScanner(
            images = listOf(scannedImage(uri1, dateAddedSec = base + 1)),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(candidateOutcome("100.00", SlipIntake.Source.Qr)),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        coordinator.runScanIfNeeded(hasPhotoPermission = true)
        coordinator.runScanNow(hasPhotoPermission = true)

        assertEquals(2, scanner.listCallCount)
    }

    @Test
    fun clearQueueDuringScanPreventsStaleQueueRefill() = runBlocking {
        val base = scanEpochSec - 50_000L
        val store = FakeAutoScanStore(enabled = true, cursor = base)
        val scanner = FakeGallerySlipScanner(
            images = listOf(scannedImage(uri1, dateAddedSec = base + 1)),
        )
        val intake = SlowFakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(candidateOutcome("100.00", SlipIntake.Source.Qr)),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        val scanJob = async {
            coordinator.runScanNow(hasPhotoPermission = true)
        }
        intake.awaitProcessingStarted()
        coordinator.clearQueue()
        intake.releaseProcessing()
        scanJob.await()

        assertTrue(coordinator.queue.value.isEmpty())
    }

    @Test
    fun queueOperationsWork() = runBlocking {
        val base = scanEpochSec - 50_000L
        val store = FakeAutoScanStore(enabled = true, cursor = base)
        val scanner = FakeGallerySlipScanner(
            images = listOf(
                scannedImage(uri1, dateAddedSec = base + 1),
                scannedImage(uri2, dateAddedSec = base + 2),
            ),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(candidateOutcome("100.00", SlipIntake.Source.Qr)),
                uri2 to FakeOutcome.Success(candidateOutcome("200.00", SlipIntake.Source.Qr)),
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        coordinator.runScanIfNeeded(hasPhotoPermission = true)
        assertEquals(uri1, coordinator.peekCurrent()?.uri)

        val skipped = coordinator.skipCurrent()
        assertEquals(uri1, skipped?.uri)
        assertEquals(uri2, coordinator.peekCurrent()?.uri)

        coordinator.removeCurrentAfterSave()
        assertNull(coordinator.peekCurrent())

        coordinator.runScanIfNeeded(hasPhotoPermission = true)
        coordinator.clearQueue()
        assertTrue(coordinator.queue.value.isEmpty())

        coordinator.dismissBanner()
        assertTrue(coordinator.bannerDismissed.value)
    }

    @Test
    fun autoSaveSavesAndQueuesOnlyNeedsReview() = runBlocking {
        val base = scanEpochSec - 50_000L
        val store = FakeAutoScanStore(enabled = true, cursor = base, autoSaveEnabled = true)
        val reviewUri = uri2
        val scanner = FakeGallerySlipScanner(
            images = listOf(
                scannedImage(uri1, dateAddedSec = base + 1),
                scannedImage(reviewUri, dateAddedSec = base + 2),
                scannedImage(uri3, dateAddedSec = base + 3),
            ),
        )
        val intake = FakeSlipIntake(
            outcomes = mapOf(
                uri1 to FakeOutcome.Success(
                    candidateOutcome(
                        amount = "100.00",
                        source = SlipIntake.Source.Ocr,
                        reference = "REF-A",
                        spentAtIso = "2026-06-07T07:03:00+07:00",
                    ),
                ),
                reviewUri to FakeOutcome.Success(
                    candidateOutcome(
                        amount = "200.00",
                        source = SlipIntake.Source.Ocr,
                        reference = "REF-B",
                        // missing spentAt → needs review
                    ),
                ),
                uri3 to FakeOutcome.Success(
                    candidateOutcome(
                        amount = "50.00",
                        source = SlipIntake.Source.Ocr,
                        reference = "REF-C",
                        spentAtIso = "2026-06-08T01:00:00+07:00",
                    ),
                ),
            ),
        )
        val persister = FakePersister(
            mapOf(
                uri1 to AutoSaveItemResult.Saved,
                reviewUri to AutoSaveItemResult.NeedsReview,
                uri3 to AutoSaveItemResult.Duplicate,
            ),
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            autoPersister = persister,
            clock = { scanEpochSec },
        )

        coordinator.runScanNow(hasPhotoPermission = true)

        assertEquals(1, coordinator.queue.value.size)
        assertEquals(reviewUri, coordinator.queue.value.single().uri)
        val summary = coordinator.consumeAutoSaveSummary()
        assertEquals(1, summary!!.saved)
        assertEquals(1, summary.duplicates)
        assertEquals(1, summary.needReview)
        assertEquals(3, summary.folderFileCount)
        assertEquals(2, summary.alreadySavedCount())
        assertEquals(
            "ในโฟลเดอร์มี 3 ไฟล์ · สำเร็จ 1 · ข้าม 1 · ต้องตรวจ 1",
            summary.snackbarMessage(),
        )
    }

    @Test
    fun fullHistoryScansAllBatchesAndAggregatesSummary() = runBlocking {
        val images = (1..250).map { i ->
            scannedImage(Uri.parse("content://media/external/images/media/$i"), dateAddedSec = i.toLong())
        }
        val store = FakeAutoScanStore(enabled = true, cursor = scanEpochSec, autoSaveEnabled = true)
        val scanner = FakeGallerySlipScanner(images = images)
        val outcomes = images.associate { img ->
            img.uri to FakeOutcome.Success(
                candidateOutcome(
                    amount = "10.00",
                    source = SlipIntake.Source.Qr,
                    reference = "R-${img.dateAddedSec}",
                    spentAtIso = "2026-06-01T12:00:00+07:00",
                ),
            )
        }
        val persister = FakePersister(images.associate { it.uri to AutoSaveItemResult.Saved })
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = FakeSlipIntake(outcomes),
            autoPersister = persister,
            clock = { scanEpochSec },
        )

        coordinator.resetAndScanAllHistory(hasPhotoPermission = true)

        assertEquals(3, scanner.listCallCount)
        assertEquals(250L, store.cursor)
        assertTrue(coordinator.queue.value.isEmpty())
        val summary = coordinator.consumeAutoSaveSummary()
        assertEquals(250, summary!!.saved)
        assertEquals(250, summary.folderFileCount)
        assertEquals(ScanProgress.Phase.Done, coordinator.scanProgress.value.phase)
        assertEquals(250, coordinator.scanProgress.value.overallTotal)
    }

    @Test
    fun runScanNowLoopsAllBatchesWithinLookback() = runBlocking {
        val base = scanEpochSec - 50_000L
        val images = (1..150).map { i ->
            scannedImage(
                Uri.parse("content://media/external/images/media/$i"),
                dateAddedSec = base + i.toLong(),
            )
        }
        val store = FakeAutoScanStore(enabled = true, cursor = base)
        val scanner = FakeGallerySlipScanner(images = images)
        val outcomes = images.associate { img ->
            img.uri to FakeOutcome.Success(candidateOutcome("10.00", SlipIntake.Source.Qr))
        }
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = FakeSlipIntake(outcomes),
            clock = { scanEpochSec },
        )

        coordinator.runScanNow(hasPhotoPermission = true)

        assertEquals(2, scanner.listCallCount)
        assertEquals(150, coordinator.queue.value.size)
        assertEquals(base + 150L, store.cursor)
    }

    @Test
    fun pauseAndResumeContinuesFullHistory() = runBlocking {
        val images = (1..5).map { i ->
            scannedImage(Uri.parse("content://media/external/images/media/$i"), dateAddedSec = i.toLong())
        }
        val store = FakeAutoScanStore(enabled = true, cursor = 0L)
        val scanner = FakeGallerySlipScanner(images = images)
        val intake = SlowFakeSlipIntake(
            images.associate { img ->
                img.uri to FakeOutcome.Success(candidateOutcome("10.00", SlipIntake.Source.Qr))
            },
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        val job = async { coordinator.resetAndScanAllHistory(hasPhotoPermission = true) }
        intake.awaitProcessingStarted()
        coordinator.pauseScan()
        intake.releaseProcessing()
        // After first image finishes, coordinator should park on pause before image 2
        var paused = false
        repeat(40) {
            if (coordinator.scanProgress.value.phase == ScanProgress.Phase.Paused) {
                paused = true
                return@repeat
            }
            kotlinx.coroutines.delay(25)
        }
        assertTrue(paused)
        coordinator.resumeScan()
        job.await()

        assertEquals(5, coordinator.queue.value.size)
        assertEquals(ScanProgress.Phase.Done, coordinator.scanProgress.value.phase)
    }

    @Test
    fun stopMidScanKeepsPartialQueueAndSummary() = runBlocking {
        val images = (1..5).map { i ->
            scannedImage(Uri.parse("content://media/external/images/media/$i"), dateAddedSec = i.toLong())
        }
        val store = FakeAutoScanStore(enabled = true, cursor = 0L)
        val scanner = FakeGallerySlipScanner(images = images)
        val intake = SlowFakeSlipIntake(
            images.associate { img ->
                img.uri to FakeOutcome.Success(candidateOutcome("10.00", SlipIntake.Source.Qr))
            },
        )
        val coordinator = AutoScanCoordinator(
            store = store,
            scanner = scanner,
            intake = intake,
            clock = { scanEpochSec },
        )

        val job = async { coordinator.resetAndScanAllHistory(hasPhotoPermission = true) }
        intake.awaitProcessingStarted()
        coordinator.stopScan()
        intake.releaseProcessing()
        job.await()

        assertEquals(ScanProgress.Phase.Done, coordinator.scanProgress.value.phase)
        assertTrue(coordinator.queue.value.isNotEmpty())
        val summary = coordinator.consumeAutoSaveSummary()
        assertTrue(summary!!.needReview >= 1)
        // Partial batch not committed — cursor still at beginning
        assertEquals(0L, store.cursor)
    }

    private fun scannedImage(uri: Uri, dateAddedSec: Long = 1_500L) =
        ScannedImage(uri = uri, dateAddedSec = dateAddedSec, bucketId = "bucket")

    private fun candidateOutcome(
        amount: String,
        source: SlipIntake.Source,
        bank: String? = null,
        reference: String? = null,
        rawText: String? = null,
        spentAtIso: String? = null,
    ) = SlipIntake.Outcome(
        draft = SlipDraft(
            amount = amount,
            bank = bank,
            reference = reference,
            spentAtIso = spentAtIso,
        ),
        source = source,
        rawText = rawText,
    )

    private class FakePersister(
        private val results: Map<Uri, AutoSaveItemResult>,
    ) : SlipAutoPersister {
        override suspend fun persist(item: QueuedSlip): AutoSaveItemResult =
            results[item.uri] ?: AutoSaveItemResult.Failed
    }

    private class FakeAutoScanStore(
        private val enabled: Boolean,
        cursor: Long?,
        private val extraBucketIds: Set<String> = setOf("bucket"),
        private val autoSaveEnabled: Boolean = false,
    ) : AutoScanStoreReader {
        var cursor: Long? = cursor
            private set

        override suspend fun isEnabled(): Boolean = enabled

        override suspend fun isAutoSaveEnabled(): Boolean = autoSaveEnabled

        override suspend fun getLastScanCursorEpochSec(): Long? = cursor

        override suspend fun setLastScanCursorEpochSec(value: Long) {
            cursor = value
        }

        override suspend fun getExtraBucketIds(): Set<String> = extraBucketIds
    }

    private class FakeGallerySlipScanner(
        private val images: List<ScannedImage> = emptyList(),
        private val folderFileCount: Int = images.size,
    ) : GallerySlipScannerReader {
        var listCalled = false
            private set
        var listCallCount = 0
            private set
        var lastAfterEpochSec: Long? = null
            private set

        override suspend fun listNewImages(
            afterEpochSec: Long,
            extraBucketIds: Set<String>,
            limit: Int,
        ): List<ScannedImage> {
            listCalled = true
            listCallCount++
            lastAfterEpochSec = afterEpochSec
            return images
                .filter { it.dateAddedSec > afterEpochSec }
                .sortedBy { it.dateAddedSec }
                .take(limit)
        }

        override suspend fun countImages(bucketIds: Set<String>): Int =
            if (bucketIds.isEmpty()) 0 else folderFileCount

        override suspend fun countImagesAfter(bucketIds: Set<String>, afterEpochSec: Long): Int {
            if (bucketIds.isEmpty()) return 0
            return images.count { it.dateAddedSec > afterEpochSec }
        }
    }

    private sealed class FakeOutcome {
        data class Success(val outcome: SlipIntake.Outcome) : FakeOutcome()

        data object Failure : FakeOutcome()
    }

    private class FakeSlipIntake(
        private val outcomes: Map<Uri, FakeOutcome> = emptyMap(),
    ) : SlipIntakeReader {
        override suspend fun process(uri: Uri): SlipIntake.Outcome {
            return when (val result = outcomes[uri]) {
                is FakeOutcome.Success -> result.outcome
                FakeOutcome.Failure -> throw IllegalStateException("intake failed")
                null -> throw IllegalStateException("unexpected uri $uri")
            }
        }
    }

    private class SlowFakeSlipIntake(
        private val outcomes: Map<Uri, FakeOutcome> = emptyMap(),
    ) : SlipIntakeReader {
        private val processingStarted = kotlinx.coroutines.CompletableDeferred<Unit>()
        private val releaseProcessing = kotlinx.coroutines.CompletableDeferred<Unit>()

        suspend fun awaitProcessingStarted() {
            processingStarted.await()
        }

        fun releaseProcessing() {
            releaseProcessing.complete(Unit)
        }

        override suspend fun process(uri: Uri): SlipIntake.Outcome {
            processingStarted.complete(Unit)
            releaseProcessing.await()
            return when (val result = outcomes[uri]) {
                is FakeOutcome.Success -> result.outcome
                FakeOutcome.Failure -> throw IllegalStateException("intake failed")
                null -> throw IllegalStateException("unexpected uri $uri")
            }
        }
    }
}
