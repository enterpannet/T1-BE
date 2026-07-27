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
    fun firstRunInitializesCursorAtBeginningAndScans() = runBlocking {
        val store = FakeAutoScanStore(enabled = true, cursor = null)
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
            clock = { nowEpochSec },
        )

        coordinator.runScanIfNeeded(hasPhotoPermission = true)

        assertTrue(scanner.listCalled)
        assertEquals(0L, scanner.lastAfterEpochSec)
        assertEquals(1_500L, store.cursor)
        assertEquals(1, coordinator.queue.value.size)
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
        val store = FakeAutoScanStore(enabled = true, cursor = 1_000L)
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
        val store = FakeAutoScanStore(enabled = true, cursor = 1_000L)
        val scanner = FakeGallerySlipScanner(
            images = listOf(
                scannedImage(uri1),
                scannedImage(uri2),
                scannedImage(uri3),
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
        val store = FakeAutoScanStore(enabled = true, cursor = 1_000L)
        val scanner = FakeGallerySlipScanner(images = listOf(scannedImage(uri1)))
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
        val store = FakeAutoScanStore(enabled = true, cursor = 1_000L)
        val scanner = FakeGallerySlipScanner(images = listOf(scannedImage(uri1)))
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
        val store = FakeAutoScanStore(enabled = true, cursor = 1_000L)
        val scanner = FakeGallerySlipScanner(images = listOf(scannedImage(uri1)))
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
        val store = FakeAutoScanStore(enabled = true, cursor = 1_000L)
        val scanner = FakeGallerySlipScanner(
            images = listOf(scannedImage(uri1), scannedImage(uri2)),
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

    private fun scannedImage(uri: Uri, dateAddedSec: Long = 1_500L) =
        ScannedImage(uri = uri, dateAddedSec = dateAddedSec, bucketId = "bucket")

    private fun candidateOutcome(
        amount: String,
        source: SlipIntake.Source,
        bank: String? = null,
        reference: String? = null,
        rawText: String? = null,
    ) = SlipIntake.Outcome(
        draft = SlipDraft(amount = amount, bank = bank, reference = reference),
        source = source,
        rawText = rawText,
    )

    private class FakeAutoScanStore(
        private val enabled: Boolean,
        cursor: Long?,
        private val extraBucketIds: Set<String> = emptySet(),
    ) : AutoScanStoreReader {
        var cursor: Long? = cursor
            private set

        override suspend fun isEnabled(): Boolean = enabled

        override suspend fun getLastScanCursorEpochSec(): Long? = cursor

        override suspend fun setLastScanCursorEpochSec(value: Long) {
            cursor = value
        }

        override suspend fun getExtraBucketIds(): Set<String> = extraBucketIds
    }

    private class FakeGallerySlipScanner(
        private val images: List<ScannedImage> = emptyList(),
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
            return images.take(limit)
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
