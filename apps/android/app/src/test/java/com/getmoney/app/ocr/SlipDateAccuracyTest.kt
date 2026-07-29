package com.getmoney.app.ocr

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.time.Duration
import java.time.OffsetDateTime
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Field-level accuracy of [SlipParser]'s spentAt extraction over the real slip
 * corpus, scored against timestamps decoded from gallery filenames.
 *
 * The fixture is produced by `python tools/dump_slip_ocr.py`, which runs the
 * same PP-OCRv5 ONNX models the app ships and mirrors PaddleSlipOcr's line
 * geometry. Regenerate it whenever the models or the corpus change.
 *
 * Two modes are scored because they answer different questions:
 *  - OCR_ONLY   — what the parser can do from pixels alone. This is the number
 *                 to watch when judging whether a better OCR engine is needed.
 *  - PRODUCTION — pixels plus the filename hint, as SlipIntake actually runs.
 *                 This is what users experience.
 */
class SlipDateAccuracyTest {

    private data class Truth(
        @SerializedName("iso") val iso: String,
        @SerializedName("source") val source: String,
        @SerializedName("yearIsExact") val yearIsExact: Boolean,
        @SerializedName("hourAmbiguous") val hourAmbiguous: Boolean,
    )

    private data class DumpLine(
        @SerializedName("text") val text: String,
        @SerializedName("yCenter") val yCenter: Float,
        @SerializedName("xLeft") val xLeft: Float,
        @SerializedName("confidence") val confidence: Float,
    )

    private data class DumpSlip(
        @SerializedName("bank") val bank: String,
        @SerializedName("file") val file: String,
        @SerializedName("truth") val truth: Truth?,
        /** Transaction id read out of the slip's verification QR, when present. */
        @SerializedName("truthReference") val truthReference: String?,
        @SerializedName("lines") val lines: List<DumpLine>,
        @SerializedName("text") val text: String,
    )

    private data class Dump(@SerializedName("slips") val slips: List<DumpSlip>)

    private enum class Mode { OCR_ONLY, PRODUCTION }

    private class Tally {
        var total = 0
        var hit = 0 // date and time both agree
        var hitDate = 0 // calendar day agrees, time may not
        var missing = 0 // parser returned no date at all
        var wrong = 0 // parser returned a date that disagrees with the filename
        val wrongExamples = mutableListOf<String>()

        fun rate(): Double = if (total == 0) 0.0 else 100.0 * hit / total

        fun dateRate(): Double = if (total == 0) 0.0 else 100.0 * hitDate / total
    }

    /**
     * The filename records save time, which trails the on-slip transaction time
     * by a few seconds. Krungsri additionally writes a 12-hour clock with no
     * AM/PM marker, so both readings count as correct for those.
     */
    private fun matches(truth: Truth, parsed: OffsetDateTime): Boolean {
        val base = OffsetDateTime.parse(truth.iso)
        val candidates =
            if (truth.hourAmbiguous) listOf(base, base.plusHours(12)) else listOf(base)
        return candidates.any {
            Math.abs(Duration.between(it, parsed).seconds) <= TOLERANCE_SECONDS
        }
    }

    /**
     * Day-level agreement. A slip filed on the right day with a midnight
     * timestamp still lands in the right budget period, so this is the metric
     * that tracks user-visible correctness; [matches] is the stricter one.
     */
    private fun matchesDate(truth: Truth, parsed: OffsetDateTime): Boolean {
        val base = OffsetDateTime.parse(truth.iso)
        val candidates =
            if (truth.hourAmbiguous) listOf(base, base.plusHours(12)) else listOf(base)
        return candidates.any { it.toLocalDate() == parsed.toLocalDate() }
    }

    private fun document(slip: DumpSlip): OcrDocument =
        OcrDocument(
            text = slip.text,
            lines = slip.lines.map {
                OcrLine(
                    text = it.text,
                    yCenter = it.yCenter,
                    xLeft = it.xLeft,
                    confidence = it.confidence,
                )
            },
            engine = OcrDocument.Engine.Paddle,
        )

    private fun score(slips: List<DumpSlip>, mode: Mode): Map<String, Tally> {
        val byBank = linkedMapOf<String, Tally>()
        for (slip in slips) {
            val truth = slip.truth ?: continue
            val tally = byBank.getOrPut(slip.bank) { Tally() }
            tally.total++

            val doc = document(slip).let {
                if (mode == Mode.PRODUCTION) withFileNameHint(it, slip.file) else it
            }
            val iso = SlipParser.parse(doc).spentAtIso

            if (iso.isNullOrBlank()) {
                tally.missing++
                continue
            }
            val parsed = runCatching { OffsetDateTime.parse(iso) }.getOrNull()
            if (parsed != null && matchesDate(truth, parsed)) tally.hitDate++

            if (parsed != null && matches(truth, parsed)) {
                tally.hit++
            } else {
                tally.wrong++
                val why = when {
                    parsed == null -> "unparseable"
                    matchesDate(truth, parsed) -> "time only"
                    else -> "date"
                }
                if (tally.wrongExamples.size < MAX_EXAMPLES) {
                    tally.wrongExamples += "[$why] ${slip.file}: got=$iso want=${truth.iso}"
                }
            }
        }
        return byBank
    }

    private fun report(title: String, byBank: Map<String, Tally>): Tally {
        val overall = Tally()
        val fmt = "%-18s %5d %6d %6d %6d   %6.1f%% %8.1f%%"
        println("\n=== $title ===")
        println(
            String.format(
                "%-18s %5s %6s %6s %6s   %7s %9s",
                "bank", "n", "ok", "miss", "wrong", "acc", "date-only",
            )
        )
        for ((bank, t) in byBank) {
            println(String.format(fmt, bank, t.total, t.hit, t.missing, t.wrong, t.rate(), t.dateRate()))
            overall.total += t.total
            overall.hit += t.hit
            overall.hitDate += t.hitDate
            overall.missing += t.missing
            overall.wrong += t.wrong
        }
        println(
            String.format(
                fmt, "TOTAL", overall.total, overall.hit, overall.missing, overall.wrong,
                overall.rate(), overall.dateRate(),
            )
        )
        for ((bank, t) in byBank) {
            if (t.wrongExamples.isEmpty()) continue
            println("  wrong in $bank:")
            t.wrongExamples.forEach { println("    $it") }
        }
        return overall
    }

    @Test
    fun spentAtAccuracyOverRealCorpus() {
        val stream = javaClass.classLoader?.getResourceAsStream(DUMP_RESOURCE)
        if (stream == null) {
            // The fixture is built from real slips, which stay out of the repo,
            // so a fresh clone has nothing to score. Regenerate it with
            // `python tools/dump_slip_ocr.py` once slip/ is populated.
            println("\nskipping: no $DUMP_RESOURCE — run python tools/dump_slip_ocr.py")
            return
        }
        val dump = stream.bufferedReader().use { Gson().fromJson(it, Dump::class.java) }
        assertTrue("dump is empty", dump.slips.isNotEmpty())

        val ocrOnly = report("spentAt — OCR only", score(dump.slips, Mode.OCR_ONLY))
        val production = report("spentAt — production (OCR + filename hint)", score(dump.slips, Mode.PRODUCTION))

        println(
            String.format(
                "\nfilename hint contributes: %+.1f pp exact (%.1f%% -> %.1f%%), " +
                    "%+.1f pp date-only (%.1f%% -> %.1f%%)",
                production.rate() - ocrOnly.rate(), ocrOnly.rate(), production.rate(),
                production.dateRate() - ocrOnly.dateRate(), ocrOnly.dateRate(), production.dateRate(),
            )
        )

        // SlipOcr.recognizeDocument runs up to three OCR passes, gated on
        // parserRecoversSpentAt over the first pass's text. Anything the first
        // pass already dates is a slip that never pays for the top-crop rescan
        // or the Tesseract merge — so this is the share of a full-gallery scan
        // that actually costs 2-3x.
        val retryShare = 100.0 * ocrOnly.missing / ocrOnly.total
        println(
            String.format(
                "\nwould trigger SlipOcr retry passes 2-3: %d/%d (%.1f%%)",
                ocrOnly.missing, ocrOnly.total, retryShare,
            )
        )

        assertTrue(
            "spentAt exact accuracy regressed: ${production.rate()}% < $MIN_EXACT_ACCURACY%",
            production.rate() >= MIN_EXACT_ACCURACY,
        )
        assertTrue(
            "spentAt date accuracy regressed: ${production.dateRate()}% < $MIN_DATE_ACCURACY%",
            production.dateRate() >= MIN_DATE_ACCURACY,
        )
    }

    /**
     * Reference accuracy, scored against the transaction id embedded in the
     * slip's own QR code.
     *
     * This is scored OCR-only on purpose. In production the filename hint feeds
     * the parser the very same txn id, so a production score would be measuring
     * the hint against itself; the QR is the one source the parser never sees.
     */
    @Test
    fun referenceAccuracyFromSlipQr() {
        val stream = javaClass.classLoader?.getResourceAsStream(DUMP_RESOURCE) ?: return
        val dump = stream.bufferedReader().use { Gson().fromJson(it, Dump::class.java) }

        fun norm(s: String?) = s?.uppercase()?.filter { it.isLetterOrDigit() }.orEmpty()

        var total = 0
        var hit = 0
        var missing = 0
        val wrong = mutableListOf<String>()
        for (slip in dump.slips) {
            val want = slip.truthReference?.takeIf { it.isNotBlank() } ?: continue
            total++
            val got = SlipParser.parse(document(slip)).reference
            when {
                got.isNullOrBlank() -> missing++
                norm(got) == norm(want) -> hit++
                // The parser may keep a longer printed form; a containment
                // match still identifies the same transaction.
                norm(got).contains(norm(want)) -> hit++
                else -> if (wrong.size < MAX_EXAMPLES) {
                    wrong += "${slip.file}: got=$got want=$want"
                }
            }
        }

        if (total == 0) {
            println("\n=== reference — no QR ground truth in fixture, skipping ===")
            return
        }
        println("\n=== reference vs slip QR (OCR only) ===")
        println(
            String.format(
                "n=%d  ok=%d  missing=%d  wrong=%d   acc=%.1f%%",
                total, hit, missing, total - hit - missing, 100.0 * hit / total,
            )
        )
        wrong.forEach { println("    $it") }
    }

    /**
     * A gallery holds more than transactions. Thai banking apps also save the
     * account holder's own PromptPay QR ("สแกน QR เพื่อโอนเข้าบัญชี") — an image
     * with a bank name, an account, and a reference number, but no transaction
     * and no amount.
     *
     * On a full-history scan of a large gallery these are the images most
     * likely to be mistaken for slips, so the no-amount gate that keeps them
     * out of the ledger is worth pinning down.
     */
    @Test
    fun promptPayAccountImagesAreNotBookedAsTransactions() {
        val stream = javaClass.classLoader?.getResourceAsStream(DUMP_RESOURCE) ?: return
        val dump = stream.bufferedReader().use { Gson().fromJson(it, Dump::class.java) }

        val accountQrImages = dump.slips.filter {
            it.text.contains("สแกน QR") || it.text.contains("เพื่อโอนเข้าบัญชี")
        }
        assertTrue("fixture has no account-QR images to check", accountQrImages.isNotEmpty())

        for (slip in accountQrImages) {
            val amount = SlipParser.parse(document(slip)).amount
                .trim().replace(",", "").toDoubleOrNull() ?: 0.0
            assertTrue(
                "${slip.file}: account-QR image produced amount=$amount, " +
                    "which SlipCandidateFilter would book as a transaction",
                amount <= 0.0,
            )
        }
        println("\naccount-QR images correctly rejected: ${accountQrImages.size}")
    }

    private companion object {
        const val DUMP_RESOURCE = "slip_ocr_dump.json"
        const val TOLERANCE_SECONDS = 90L
        const val MAX_EXAMPLES = 8

        /**
         * Measured baselines on the 310-slip corpus (2026-07-29). Raise these
         * as the parser improves so regressions surface here rather than in
         * users' transaction lists.
         */
        const val MIN_EXACT_ACCURACY = 99.0
        const val MIN_DATE_ACCURACY = 99.0
    }
}
