package com.getmoney.app.ocr

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import org.junit.Test

/**
 * Scores the fields that have no free ground truth — amount, direction, and
 * party names — against labels read straight off the slip images.
 *
 * spentAt and reference can be checked against the filename and the slip QR,
 * so they're covered by [SlipDateAccuracyTest]. These three cannot: nothing in
 * the file metadata knows what the transfer was worth or who it went to. The
 * labels come from `tools/slip_labels.json`, merged into the fixture by
 * `python tools/merge_labels.py`.
 *
 * Reports rather than asserts while the labelled set is small — a handful of
 * slips can't support a meaningful regression threshold, and a failing build
 * would say more about the sample size than about the parser.
 */
class SlipFieldAccuracyTest {

    private data class Label(
        @SerializedName("amount") val amount: Double?,
        @SerializedName("direction") val direction: String?,
        @SerializedName("from_name") val fromName: String?,
        @SerializedName("to_name") val toName: String?,
        @SerializedName("kind") val kind: String?,
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
        @SerializedName("label") val label: Label?,
        @SerializedName("lines") val lines: List<DumpLine>,
        @SerializedName("text") val text: String,
    )

    private data class Dump(@SerializedName("slips") val slips: List<DumpSlip>)

    private fun document(slip: DumpSlip) =
        OcrDocument(
            text = slip.text,
            lines = slip.lines.map {
                OcrLine(it.text, it.yCenter, it.xLeft, it.confidence)
            },
            engine = OcrDocument.Engine.Paddle,
        )

    private fun normalizeName(value: String?): String =
        value.orEmpty().replace(Regex("""\s+"""), "").lowercase()

    /**
     * The parser deliberately returns the whole party block — name, bank, and
     * masked account joined by "·" — so a labelled name is a match when it
     * appears inside that block. Requiring equality would score the parser's
     * intended output as a failure.
     */
    private fun nameMatches(got: String?, want: String?): Boolean {
        val w = normalizeName(want)
        if (w.isEmpty()) return normalizeName(got).isEmpty()
        return normalizeName(got).contains(w)
    }

    @Test
    fun amountDirectionAndPartiesAgainstHandReadLabels() {
        val stream = javaClass.classLoader?.getResourceAsStream("slip_ocr_dump.json")
        if (stream == null) {
            println("\nskipping: no fixture — run python tools/dump_slip_ocr.py")
            return
        }
        val dump = stream.bufferedReader().use { Gson().fromJson(it, Dump::class.java) }
        val labelled = dump.slips.filter { it.label != null }
        if (labelled.isEmpty()) {
            println("\nskipping: fixture has no labels — run python tools/merge_labels.py")
            return
        }

        var amountOk = 0
        var directionOk = 0
        var directionScored = 0
        var fromOk = 0
        var toOk = 0
        val misses = mutableListOf<String>()

        for (slip in labelled) {
            val label = slip.label!!
            val draft = SlipParser.parse(document(slip))

            val wantAmount = label.amount
            val gotAmount = draft.amount.trim().replace(",", "").toBigDecimalOrNull()
            val amountMatches = wantAmount != null && gotAmount != null &&
                gotAmount.compareTo(BigDecimal.valueOf(wantAmount)) == 0
            if (amountMatches) {
                amountOk++
            } else {
                misses += "[amount] ${slip.file} (${label.kind}): " +
                    "got=${draft.amount} want=$wantAmount"
            }

            // SELF-transfers have no meaningful in/out for a spend ledger, so
            // they're excluded rather than scored as a failure either way.
            if (label.direction != null && label.direction != "SELF") {
                directionScored++
                val got = draft.direction?.name
                if (got == label.direction) {
                    directionOk++
                } else {
                    misses += "[direction] ${slip.file}: got=$got want=${label.direction}"
                }
            }

            if (nameMatches(draft.fromName, label.fromName)) {
                fromOk++
            } else {
                misses += "[from] ${slip.file}: got=${draft.fromName} want=${label.fromName}"
            }
            if (nameMatches(draft.toName, label.toName)) {
                toOk++
            } else {
                misses += "[to] ${slip.file}: got=${draft.toName} want=${label.toName}"
            }
        }

        val n = labelled.size
        println("\n=== fields vs hand-read labels (n=$n) ===")
        println(String.format("amount     %2d/%2d  %5.1f%%", amountOk, n, 100.0 * amountOk / n))
        if (directionScored > 0) {
            println(
                String.format(
                    "direction  %2d/%2d  %5.1f%%  (%d SELF excluded)",
                    directionOk, directionScored, 100.0 * directionOk / directionScored,
                    n - directionScored,
                )
            )
        }
        println(String.format("from_name  %2d/%2d  %5.1f%%", fromOk, n, 100.0 * fromOk / n))
        println(String.format("to_name    %2d/%2d  %5.1f%%", toOk, n, 100.0 * toOk / n))

        if (misses.isNotEmpty()) {
            println("\nmisses:")
            misses.forEach { println("  $it") }
        }
    }
}
