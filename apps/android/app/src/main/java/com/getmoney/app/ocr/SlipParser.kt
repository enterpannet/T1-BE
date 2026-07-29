package com.getmoney.app.ocr

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.DateTimeException
import java.time.LocalDate
import java.time.ZoneId

data class SlipDraft(
    val amount: String,
    val spentAtIso: String? = null,
    val bank: String? = null,
    val reference: String? = null,
    val note: String? = null,
    val direction: TransferDirection? = null,
    val fromName: String? = null,
    val toName: String? = null,
)

object SlipParser {
    private val thaiDigits = mapOf(
        '๐' to '0', '๑' to '1', '๒' to '2', '๓' to '3', '๔' to '4',
        '๕' to '5', '๖' to '6', '๗' to '7', '๘' to '8', '๙' to '9',
    )

    private val moneyPattern = Regex(
        """(?<![A-Za-z0-9])(\d{1,3}(?:,\d{3})+|\d{1,7})(?:\.(\d{1,2}))?(?![A-Za-z0-9])""",
    )

    private val amountKeyword = Regex(
        """จำนวน\s*เงิน|ยอด(?:โอน|เงิน|ชำระ)?|Amount|Transfer\s*Amount|Total\s*Amount|Paid\s*Amount|จำนวน|บาท|Baht|THB|฿""",
        RegexOption.IGNORE_CASE,
    )

    private val primaryAmountKeyword = Regex(
        """จำนวน\s*เงิน|Amount|Transfer\s*Amount|Total\s*Amount|Paid\s*Amount""",
        RegexOption.IGNORE_CASE,
    )

    private val feeKeyword = Regex(
        """ค่าธรรมเนียม|Fee|Service\s*charge""",
        RegexOption.IGNORE_CASE,
    )

    private val referencePattern = Regex(
        """(?:เลขที่รายการ|รหัสอ้างอิง|เลขที่อ้างอิง|ค่าอ้างอิง\s*[12]?|หมายเลข(?:ที่)?อ้างอิง\s*[12]?|Reference(?:\s*No\.?)?|Ref(?:erence)?\.?|Txn(?:\s*ID)?|Transaction\s*ID)\s*[:：#]?\s*([A-Za-z0-9\-]+)""",
        RegexOption.IGNORE_CASE,
    )

    /** KBank / K+ primary txn id */
    private val kbankReferencePattern = Regex(
        """เลขที่รายการ\s*[:：#]?\s*([A-Za-z0-9\-]+)""",
        RegexOption.IGNORE_CASE,
    )

    /** Krungthai (KTB) — ใช้รหัสอ้างอิงเช็คซ้ำ (ไม่ใช่ค่าอ้างอิงบิลเลอร์) */
    private val ktbReferencePattern = Regex(
        """รหัสอ้างอิง\s*[:：#]?\s*([A-Za-z0-9\-]+)""",
        RegexOption.IGNORE_CASE,
    )

    /** Krungsri — ใช้หมายเลขอ้างอิง / หมายเลขที่อ้างอิง เช็คซ้ำ */
    private val krungsriReferencePattern = Regex(
        """หมายเลข(?:ที่)?อ้างอิง\s*[:：#]?\s*([A-Za-z0-9\-]+)""",
        RegexOption.IGNORE_CASE,
    )

    private val standaloneRefLabel = Regex(
        """^(?:เลขที่รายการ|รหัสอ้างอิง|หมายเลข(?:ที่)?อ้างอิง)\s*[:：#]?\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private val isoDateTimePattern = Regex(
        """(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:[+-]\d{2}:\d{2}|Z)?)""",
    )

    private val thaiDateTimePattern = Regex(
        """(\d{1,2})[/-](\d{1,2})[/-](\d{2,4})\s+(\d{1,2})[:.](\d{2})(?::(\d{2}))?""",
    )

    /**
     * Thai month abbreviations (dots optional — OCR often drops them).
     * e.g. ก.ค. / ก.ค / กค / มิ.ย. / มิย
     */
    private val thaiMonthToken =
        """(?:ม\.?\s*ค\.?|ก\.?\s*พ\.?|มี\.?\s*ค\.?|เม\.?\s*ย\.?|พ\.?\s*ค\.?|มิ\.?\s*ย\.?|ก\.?\s*ค\.?|ส\.?\s*ค\.?|ก\.?\s*ย\.?|ต\.?\s*ค\.?|พ\.?\s*ย\.?|ธ\.?\s*ค\.?)"""

    /**
     * e.g. 27 ก.ค. 2569 - 22:14  |  7 มิ.ย. 69 07:03 น.  |  18 ก.ค. 2569 14:06:58
     * Also tolerates OCR junk digit before year: "16 มิ.ย. 2\n2569 15:23:56"
     */
    private val thaiMonthDateTimePattern = Regex(
        // Optional stray "." between month and year: "4 ก.ค..69 19:24" / "4 ก.ค.69 19:24"
        """(\d{1,2})\s*($thaiMonthToken)\s*\.?\s*(?:\d\s+)?(\d{2,4})\s*[-–]?\s*(\d{1,2})[:.](\d{2})(?::(\d{2}))?\s*(?:น\.?)?""",
        RegexOption.IGNORE_CASE,
    )

    private val thaiMonthToNumber = mapOf(
        "ม.ค." to "01", "ก.พ." to "02", "มี.ค." to "03", "เม.ย." to "04",
        "พ.ค." to "05", "มิ.ย." to "06", "ก.ค." to "07", "ส.ค." to "08",
        "ก.ย." to "09", "ต.ค." to "10", "พ.ย." to "11", "ธ.ค." to "12",
    )

    private val inKeywords = Regex(
        """รับเงิน|รับโอน|เงินเข้า|Received|Incoming|\bCredit\b""",
        RegexOption.IGNORE_CASE,
    )

    private val outKeywords = Regex(
        """โอนเงินสำเร็จ|โอนเงิน|โอนสำเร็จ|โอนออก|จ่ายบิลสำเร็จ|จ่ายบิล|เติมเงินสำเร็จ|เติมเงิน|ชำระเงินสำเร็จ|ชำระเงิน|Scan\s*to\s*Pay|Transfer\s*Successful|Transfer|Paid|Payment\s*successful""",
        RegexOption.IGNORE_CASE,
    )

    private val fromLabelPattern = Regex(
        """^(?:จากบัญชี|จาก|ผู้โอน|From|Sender|Payer)\s*[:：]?\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val toLabelPattern = Regex(
        """^(?:ไปยัง|ถึง|ผู้รับ|เข้าบัญชี|To|Recipient|Payee)\s*[:：]?\s*(.+)$""",
        RegexOption.IGNORE_CASE,
    )

    private val standaloneFromLabel = Regex("""^(?:จากบัญชี|จาก|ผู้โอน|From|Sender)$""", RegexOption.IGNORE_CASE)
    private val standaloneToLabel = Regex("""^(?:ไปยัง|ถึง|ผู้รับ|To|Recipient)$""", RegexOption.IGNORE_CASE)

    /**
     * Person / company prefixes (นามนำหน้า + นิติบุคคล).
     * OCR noise: น.ส / นส. / บมจ / บมจ.
     */
    private val nameLinePattern = Regex(
        """^(?:คุณ|ท่าน|ดร\.?|นพ\.?|พญ\.?|นาย|น\.?\s*ส\.?|นางสาว|นาง|ด\.?\s*ช\.?|ด\.?\s*ญ\.?|บมจ\.?|บจก\.?|หจก\.?|บริษัท|ห้างหุ้นส่วน)\s*.+$""",
    )

    private val payeeNameHints = listOf(
        "ทรูมันนี่", "ทรู มันนี่", "TrueMoney", "truemoney", "e-Wallet", "e-wallet",
        "ไลน์ เพย์", "LINE Pay", "Rabbit LINE Pay", "Rabbit Line Pay",
        "ถุงเงิน", "PromptPay", "พร้อมเพย์", "7-Eleven", "ELEVEN",
        "Advanced mPAY", "AdvancedmPAY", "mPAY", "บางจาก", "Bangchak",
        "BLUEPAY", "บลูเพย์", "ShopeePay", "ลาซาด้า",
        "เคาน์เตอร์เซอร์วิส", "Counter Service", "ระบบรับชำระ", "ปณท",
        "STORYLOG", "ELEMENT PAY", "ELEMENT PAYME",
    )

    /** Slip UI labels / refs that must never become ผู้รับ/ผู้โอน. */
    private val slipMetaLabelPattern = Regex(
        """(?i)^(?:Merchant\s*ID|MerchantID|Transaction\s*Reference|Transaction\s*ID|Txn\s*ID|Reference|Ref\.?|หมายเลข(?:ที่)?อ้างอิง|รหัสร้านค้า|รหัสธุรกรรม|เลขที่รายการ)$""",
    )

    private fun looksLikeTxnOrRefToken(line: String): Boolean {
        val t = line.trim()
        if (t.isEmpty()) return false
        if (slipMetaLabelPattern.matches(t)) return true
        // Krungsri / KBank style refs: KSA…, KS…, KGP…
        if (Regex("""(?i)^(?:KSA|KS|KGP|KB|EDC|KPS)\d{6,}$""").matches(t)) return true
        if (Regex("""(?i)^KSA\d+$""").matches(t)) return true
        // Long mixed alnum refs without spaces (merchant / EDC ids)
        if (t.length in 12..32 &&
            t.none { it.isWhitespace() } &&
            t.any { it.isDigit() } &&
            t.any { it.isLetter() } &&
            t.all { it.isLetterOrDigit() || it == '-' } &&
            t.count { it in '\u0E00'..'\u0E7F' } == 0
        ) {
            return true
        }
        return false
    }

    /** Wallet / bill-pay brands that must not be absorbed as the sender's "bank" line. */
    private val walletPayeeBrandPattern = Regex(
        """(?i)ทรู\s*มันนี่|truemoney|e-?\s*wallet|ไลน์\s*เพย์|line\s*pay|rabbit\s*line|ถุงเงิน|prompt\s*pay|พร้อมเพย์|shopee\s*pay|bluepay|บลูเพย์""",
    )

    private val companyPrefixPattern = Regex(
        """^(?:บมจ\.?|บจก\.?|หจก\.?|บริษัท|ห้างหุ้นส่วน)""",
    )

    /** OCR junk between name and bank/account on K+ slips (not long biller refs). */
    private val partyNoiseLine = Regex("""^(?:[%|•·\-_/=]+|\d{1,2})$""")

    /** Fragments that continue a company/biller name, not a new party */
    private val companyContinuationLine = Regex(
        """(?i)^(?:\(?มหาชน\)?|จำกัด|Prompt|Pay|LIMITED|PUBLIC|COMPANY|HEAD|CO\.?|LTD\.?|INC\.?)(?:\b.*)?$""",
    )

    private val memoLabelPattern = Regex(
        """^(?:บันทึกช่วยจำ|บันทึก|ข้อความ|หมายเหตุ|รายละเอียด|Memo|Note|Message|Description|Remark)\s*[:：]?\s*(.*)$""",
        RegexOption.IGNORE_CASE,
    )

    private val skipPartyLine = Regex(
        """^(?:โอน|จ่าย|เติม|ชำระ|รับ|จำนวน|ค่าธรรมเนียม|เลขที่|รหัส|ค่าอ้างอิง|หมายเลข|วันที่|สแกน|ธ\.|xxx|XXX|\d)""",
        RegexOption.IGNORE_CASE,
    )

    private val bankKeywords = listOf(
        "ธนาคารกสิกรไทย" to "KBank",
        "ธ.กสิกรไทย" to "KBank",
        "ธ.กสิกร" to "KBank",
        "Kasikornbank" to "KBank",
        "Kasikorn Bank" to "KBank",
        "Kasikorn" to "KBank",
        "MAKE by KBank" to "KBank",
        "KBank" to "KBank",
        "กสิกรไทย" to "KBank",
        "กสิกร" to "KBank",
        "ธนาคารกรุงไทย" to "KTB",
        "ธ.กรุงไทย" to "KTB",
        "Krung Thai" to "KTB",
        "Krungthai" to "KTB",
        "กรุงไทย" to "KTB",
        "KTB" to "KTB",
        "ธนาคารไทยพาณิชย์" to "SCB",
        "ธ.ไทยพาณิชย์" to "SCB",
        "Siam Commercial" to "SCB",
        "ไทยพาณิชย์" to "SCB",
        "SCB" to "SCB",
        "ธนาคารกรุงเทพ" to "BBL",
        "ธ.กรุงเทพ" to "BBL",
        "Bangkok Bank" to "BBL",
        "กรุงเทพ" to "BBL",
        "BBL" to "BBL",
        "ธนาคารกรุงศรีอยุธยา" to "Krungsri",
        "ธ.กรุงศรี" to "Krungsri",
        "Bank of Ayudhya" to "Krungsri",
        "Krungsri" to "Krungsri",
        "กรุงศรี" to "Krungsri",
        "BAY" to "Krungsri",
        "ธนาคารทหารไทยธนชาต" to "TTB",
        "ธนาคารทหารไทย" to "TTB",
        "ธนชาต" to "TTB",
        "TMBThanachart" to "TTB",
        "TTB" to "TTB",
        "TMB" to "TMB",
        "ธนาคารออมสิน" to "GSB",
        "ออมสิน" to "GSB",
        "GSB" to "GSB",
        "ธนาคารเพื่อการเกษตร" to "BAAC",
        "ธ.ก.ส." to "BAAC",
        "BAAC" to "BAAC",
        "ธนาคารซีไอเอ็มบี" to "CIMB",
        "CIMB" to "CIMB",
        "ธนาคารยูโอบี" to "UOB",
        "UOB" to "UOB",
        "ธนาคารแลนด์ แอนด์ เฮ้าส์" to "LH Bank",
        "LH Bank" to "LH Bank",
        "LHBANK" to "LH Bank",
        "PromptPay" to "PromptPay",
        "พร้อมเพย์" to "PromptPay",
        "TrueMoney" to "TrueMoney",
        "ทรูมันนี่" to "TrueMoney",
        "ShopeePay" to "ShopeePay",
        "Rabbit LINE Pay" to "Rabbit LINE Pay",
    )

    fun parse(raw: String, layoutLines: List<OcrLine>? = null): SlipDraft {
        val text = normalizeOcrText(raw)
        val direction = extractDirection(text)
        val (fromName, toName) = extractParties(text, layoutLines)
        val labeledMemo = extractMemo(text)
        val note = labeledMemo
            ?: SlipNoteCodec.displayTransferLine(
                SlipNoteParts(
                    direction = direction,
                    fromName = fromName,
                    toName = toName,
                ),
            )
        return SlipDraft(
            amount = extractAmount(text),
            spentAtIso = extractSpentAt(text),
            bank = extractBank(text),
            reference = extractReference(text),
            note = note,
            direction = direction,
            fromName = fromName,
            toName = toName,
        )
    }

    /**
     * OCR emits one box per visual run, so a single printed line arrives split:
     * a label and its value ("แปลงเป็นเงิน" / "4,000.06" / "บาท") land in three
     * boxes, and a name that straddles a gap ("นายเกียรติศักดิ์" / "พิมพ์อาภรณ์")
     * lands in two. Joining the text in box order then reads a label without
     * its value, and one person as two.
     *
     * Regrouping boxes by row restores the printed line, which is what the
     * field patterns are written against.
     */
    internal fun mergeRows(lines: List<OcrLine>): List<OcrLine> {
        if (lines.size < 2) return lines
        val sorted = lines.sortedWith(compareBy({ it.yCenter }, { it.xLeft }))

        // Slips vary in resolution, so the row tolerance scales with the page
        // rather than being a fixed pixel count. The bounds keep it sane on
        // very short crops and very tall screenshots alike.
        val span = sorted.last().yCenter - sorted.first().yCenter
        val tolerance = (span * 0.012f).coerceIn(4f, 24f)

        val rows = mutableListOf<MutableList<OcrLine>>()
        for (line in sorted) {
            val row = rows.lastOrNull()
            if (row != null && line.yCenter - row.first().yCenter <= tolerance) {
                row += line
            } else {
                rows += mutableListOf(line)
            }
        }

        // Slips put a bank or merchant logo in a narrow column to the left of
        // the party block, and OCR reads it as a stray "K+" or "1". Merged into
        // the row it prefixes the recipient's name.
        //
        // A wide gap alone can't identify it — a label and its value sit far
        // apart on the same row too ("แปลงเป็นเงิน" … "4,000.06") — so this also
        // requires the fragment to be a character or two, which no field label
        // ever is.
        val pageWidth = sorted.maxOf { it.xLeft }
        val logoGap = pageWidth * 0.10f

        return rows.map { row ->
            val ordered = row.sortedBy { it.xLeft }.toMutableList()
            while (ordered.size > 1 &&
                ordered[0].text.length <= 2 &&
                ordered[1].xLeft - ordered[0].xLeft > logoGap
            ) {
                ordered.removeAt(0)
            }
            OcrLine(
                text = ordered.joinToString(" ") { it.text },
                yCenter = ordered.first().yCenter,
                xLeft = ordered.first().xLeft,
                confidence = ordered.minOf { it.confidence },
            )
        }
    }

    fun parse(document: OcrDocument): SlipDraft {
        val rows = mergeRows(document.lines)
        if (rows.size != document.lines.size) {
            // document.text can carry content that never came from a box —
            // SlipIntake appends the gallery filename to it as a hint. Rebuild
            // from rows, then re-append whatever the boxes don't account for,
            // or that hint is silently dropped.
            val boxTexts = document.lines.mapTo(mutableSetOf()) { it.text.trim() }
            val extras = document.text.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && it !in boxTexts }
            val merged = (rows.map { it.text } + extras).joinToString("\n")
            val fromRows = parse(merged, rows)
            if (fromRows.amount.isNotBlank() || !fromRows.spentAtIso.isNullOrBlank()) {
                return fromRows
            }
        }
        val primary = parse(document.text, document.lines)
        if (!primary.spentAtIso.isNullOrBlank()) return primary
        // Paddle sometimes emits lines out of string-join order; recover date from Y-sorted lines.
        if (document.lines.size < 2) return primary
        val byY = document.lines
            .sortedWith(compareBy({ it.yCenter }, { it.xLeft }))
            .joinToString("\n") { it.text }
        if (byY == document.text) return primary
        val retry = parse(byY, document.lines)
        return if (!retry.spentAtIso.isNullOrBlank()) {
            primary.copy(
                spentAtIso = retry.spentAtIso,
                bank = primary.bank ?: retry.bank,
                fromName = primary.fromName ?: retry.fromName,
                toName = primary.toName ?: retry.toName,
                reference = primary.reference ?: retry.reference,
            )
        } else {
            primary
        }
    }

    internal fun normalizeOcrText(raw: String): String {
        val mapped = buildString(raw.length) {
            for (ch in raw) {
                append(thaiDigits[ch] ?: ch)
            }
        }
        return mapped
            .replace('\u00A0', ' ')
            .replace(Regex("""[|!]"""), "I")
            .replace(Regex("""[Oo](?=\d)|(?<=\d)[Oo]"""), "0")
            // K+ txn ids often OCR as O16… instead of 016…
            .replace(Regex("""(?i)(?<![0-9A-Za-z])O(?=16\d{9})"""), "0")
            .replace(Regex("""(?<=\d)[lI](?=\d)"""), "1")
            .replace(Regex("""[‐‑–—]"""), "-")
            .replace(Regex("""[：﹕]"""), ":")
            // OCR reads นาฬิกา "น." as H. / u.
            .replace(Regex("""(?<=\d)\s*[HhUu]\.\s*$""", RegexOption.MULTILINE), " น.")
            // Krungsri glued header — numeric month FIRST so "20.8.256909:18:27"
            // is not partially eaten by the letter/dot unglue (matching "8.2569…").
            .replace(
                Regex("""(\d{1,2})\.(\d{1,2})\.(25\d{2})(\d{2}:\d{2}(?::\d{2})?)"""),
                "$1 $2 $3 $4",
            )
            // 28U.J.256915:04:48 / 29..256921:27:31
            .replace(
                Regex("""(?i)(\d{1,2})\s*([A-Za-z](?:\.[A-Za-z]?)?|\.{1,3})\s*\.?(25\d{2})(\d{2}:\d{2}(?::\d{2})?)"""),
                "$1 $2 $3 $4",
            )
            // AdvancedmPAY → Advanced mPAY
            .replace(Regex("""(?i)Advanced\s*m\s*PAY"""), "Advanced mPAY")
            // Normalize common OCR breakage of Thai courtesy titles
            .replace(Regex("""น\s*\.\s*ส\s*\.?"""), "น.ส.")
            .replace(Regex("""(?m)^นส\.\s*"""), "น.ส. ")
            .replace(Regex("""(?m)^น\s+ส\.?\s+"""), "น.ส. ")
            .lines()
            .joinToString("\n") { it.trim() }
            .trim()
    }

    private fun extractAmount(text: String): String {
        val lines = text.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return "0"

        data class Candidate(val value: BigDecimal, val score: Int)

        val candidates = mutableListOf<Candidate>()

        lines.forEachIndexed { index, line ->
            val nearby = listOfNotNull(
                lines.getOrNull(index - 1),
                line,
                lines.getOrNull(index + 1),
            ).joinToString(" ")

            val keywordNear = amountKeyword.containsMatchIn(nearby)
            val keywordOnLine = amountKeyword.containsMatchIn(line)
            val primaryNear = primaryAmountKeyword.containsMatchIn(nearby)
            val feeNear = feeKeyword.containsMatchIn(nearby)

            moneyPattern.findAll(line).forEach { match ->
                val rawNumber = match.value
                // Buddhist year / clock fragments (e.g. "16 ก.ค. 69 16:07") must not win
                if (isDateOrTimeMoneyToken(line, match)) return@forEach

                val value = parseMoney(rawNumber) ?: return@forEach
                if (!isPlausibleMoney(value, rawNumber)) return@forEach

                var score = 0
                if (keywordOnLine) score += 120
                else if (keywordNear) score += 80
                if (primaryNear) score += 60
                if (feeNear) score -= 200

                // Account masks / parenthetical codes (e.g. TrueMoney (1022))
                if (Regex("""(?i)x{2,}""").containsMatchIn(line)) score -= 250
                if (Regex("""\(${Regex.escape(rawNumber)}\)""").containsMatchIn(line)) score -= 300
                if (rawNumber.startsWith("0") && !rawNumber.contains('.')) score -= 80

                if (rawNumber.contains('.')) {
                    score += 50
                    val decimals = rawNumber.substringAfter('.', "")
                    if (decimals.length == 2) score += 30
                }
                if (rawNumber.contains(',')) score += 20

                when {
                    value >= BigDecimal("10") && value <= BigDecimal("100000") -> score += 25
                    value < BigDecimal("1") -> score -= 40
                    value > BigDecimal("500000") -> score -= 60
                }

                if (Regex("""บาท|Baht|THB|฿""", RegexOption.IGNORE_CASE).containsMatchIn(line)) {
                    score += 40
                } else {
                    score -= 20
                }

                candidates += Candidate(value, score)
            }
        }

        val best = candidates.maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.value })
            ?: return "0"
        // Reject weak guesses (date leftovers with no amount keyword)
        if (best.score < 40) return "0"

        return best.value.setScale(2, RoundingMode.HALF_UP).toPlainString()
    }

    /**
     * True when a money-pattern hit is really a date/time fragment on Thai slips
     * (short BE year 69, day 16, clock 16:07, etc.).
     */
    private fun isDateOrTimeMoneyToken(line: String, match: MatchResult): Boolean {
        val start = match.range.first
        val endExclusive = match.range.last + 1
        val before = line.getOrNull(start - 1)
        val after = line.getOrNull(endExclusive)
        // 16:07 / 16:07:00 — digit group touching a colon
        if (before == ':' || after == ':') return true

        fun coveredBy(regex: Regex): Boolean =
            regex.findAll(line).any { dm ->
                match.range.first >= dm.range.first && match.range.last <= dm.range.last
            }

        if (coveredBy(thaiMonthDateTimePattern) || coveredBy(thaiDateTimePattern) || coveredBy(isoDateTimePattern)) {
            return true
        }
        // Date without time: "16 ก.ค. 69" or "16/07/69"
        val thaiDateOnly = Regex(
            """\d{1,2}\s*($thaiMonthToken)\s*(?:\d\s+)?\d{2,4}""",
            RegexOption.IGNORE_CASE,
        )
        val slashDate = Regex("""\d{1,2}[/-]\d{1,2}[/-]\d{2,4}""")
        if (coveredBy(thaiDateOnly) || coveredBy(slashDate)) return true

        return false
    }

    private fun parseMoney(raw: String): BigDecimal? =
        raw.replace(",", "").toBigDecimalOrNull()

    private fun isPlausibleMoney(value: BigDecimal, raw: String): Boolean {
        val digitsOnly = raw.replace(",", "").replace(".", "")
        if (!raw.contains('.') && digitsOnly.length >= 10) return false
        if (!raw.contains('.') && value in BigDecimal("1900")..BigDecimal("2100") && digitsOnly.length == 4) {
            return false
        }
        // Masked account fragments / leading-zero integers without decimals
        if (!raw.contains('.') && raw.startsWith("0") && digitsOnly.length >= 3) return false
        if (raw.startsWith("0") && !raw.contains('.') && digitsOnly.length in 9..10) return false
        if (value <= BigDecimal.ZERO) return false
        if (value > BigDecimal("2000000")) return false
        return true
    }

    private fun extractReference(text: String): String? {
        val bank = extractBank(text)
        when (bank) {
            "KTB" -> findLabeledReference(text, ktbReferencePattern)?.let { return it }
            "Krungsri" -> findLabeledReference(text, krungsriReferencePattern)?.let { return it }
            "KBank" -> findLabeledReference(text, kbankReferencePattern)?.let { return it }
        }
        // Prefer bank primary labels before generic / biller "ค่าอ้างอิง"
        findLabeledReference(text, kbankReferencePattern)?.let { return it }
        findLabeledReference(text, ktbReferencePattern)?.let { return it }
        findLabeledReference(text, krungsriReferencePattern)?.let { return it }
        referencePattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { return it }

        val fallback = Regex("""\b([A-Z]{2,}\d{6,}|\d{6,}[A-Z]{2,}[A-Z0-9]*)\b""")
            .find(text)
            ?.groupValues
            ?.get(1)
        return fallback?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Same-line label+value, or label alone then value on the next line. */
    private fun findLabeledReference(text: String, pattern: Regex): String? {
        pattern.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.let {
            return it
        }
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        for (i in lines.indices) {
            if (!standaloneRefLabel.matches(lines[i])) continue
            // Only accept next-line value when this label matches the bank-specific pattern family
            val labelOnly = lines[i]
            val labelMatches = when {
                pattern.pattern.contains("เลขที่รายการ") -> labelOnly.startsWith("เลขที่รายการ")
                pattern.pattern.contains("รหัสอ้างอิง") -> labelOnly.startsWith("รหัสอ้างอิง")
                pattern.pattern.contains("หมายเลข") ->
                    labelOnly.startsWith("หมายเลขอ้างอิง") || labelOnly.startsWith("หมายเลขที่อ้างอิง")
                else -> false
            }
            if (!labelMatches) continue
            val next = lines.getOrNull(i + 1)?.trim().orEmpty()
            if (next.matches(Regex("""[A-Za-z0-9\-]{6,40}"""))) return next
        }
        return null
    }

    private fun extractBank(text: String): String? {
        for ((keyword, bank) in bankKeywords) {
            if (text.contains(keyword, ignoreCase = true)) {
                return bank
            }
        }
        return null
    }

    private fun extractDirection(text: String): TransferDirection {
        val hasIn = inKeywords.containsMatchIn(text)
        val hasOut = outKeywords.containsMatchIn(text)
        return when {
            hasIn && !hasOut -> TransferDirection.IN
            else -> TransferDirection.OUT
        }
    }

    private fun extractParties(text: String, layoutLines: List<OcrLine>? = null): Pair<String?, String?> {
        val hasExplicitLabels = text.lines().any { line ->
            val t = line.trim()
            standaloneFromLabel.matches(t) || standaloneToLabel.matches(t) ||
                fromLabelPattern.containsMatchIn(t) || toLabelPattern.containsMatchIn(t)
        }

        // K+/KBank stack: use geometry (or ordered sections) before generic heuristics
        if (!hasExplicitLabels && isLikelyKbankSlip(text)) {
            val kbank = when {
                layoutLines != null && layoutLines.size >= 4 ->
                    extractKbankPartiesFromLayout(layoutLines)
                else -> null
            } ?: extractKbankPartiesFromText(text)
            if (kbank != null) {
                var (from, to) = kbank
                if (from != null && !from.contains(" · ")) from = attachNearbyIdentity(from, text)
                if (to != null && !to.contains(" · ")) to = attachNearbyIdentity(to, text)
                to = upgradePromptPayPayee(text, to)
                if (from != null || to != null) return from to to
            }
        }

        var from = extractPartyNameInline(text, fromLabelPattern)
        var to = extractPartyNameInline(text, toLabelPattern)

        if (from == null) from = extractPartyAfterStandaloneLabel(text, standaloneFromLabel)
        if (to == null) to = extractPartyAfterStandaloneLabel(text, standaloneToLabel)

        // KBank-style: name → bank → xxx account blocks (best for คนรับ)
        val blocks = extractTransferPartyBlocks(text)
        if (from == null && blocks.isNotEmpty()) from = blocks[0]
        if (to == null && blocks.size >= 2) to = blocks[1]

        val nameLines = extractNameLines(text)
        if (from == null && nameLines.isNotEmpty()) from = nameLines[0]
        if (to == null && nameLines.size >= 2) to = nameLines[1]

        if (to == null) {
            to = extractPayeeHintName(text)
        }

        // Prefer clearer wallet/biller labels when OCR split/garbled the Thai payee name.
        preferCanonicalPayeeLabel(text)?.let { canonical ->
            if (to.isNullOrBlank() ||
                to!!.startsWith("LINE", ignoreCase = true) ||
                to.equals("Prompt", ignoreCase = true) ||
                to.equals("Pay", ignoreCase = true) ||
                looksLikeTxnOrRefToken(to!!.split(" · ").first()) ||
                partyNameQuality(to!!) < partyNameQuality(canonical)
            ) {
                to = canonical
            }
        }

        // K+ OCR often splits PromptPay logo text across lines.
        to = upgradePromptPayPayee(text, to)

        // If block extractor already attached bank/account, skip re-attach
        if (from != null && !from.contains(" · ")) from = attachNearbyIdentity(from, text)
        if (to != null && !to.contains(" · ")) to = attachNearbyIdentity(to, text)

        // Fallback: masked accounts (2nd account = คนรับ on dual-party slips)
        if (from == null) from = extractMaskedAfterLabel(text, standaloneFromLabel)
        if (to == null) to = extractMaskedAfterLabel(text, standaloneToLabel)
        val accounts = extractAllMaskedAccounts(text)
        if (to == null || looksLikeTxnOrRefToken(to.split(" · ").first())) {
            when {
                accounts.size >= 2 -> to = accounts[1]
                accounts.size == 1 && from?.contains(accounts[0]) != true -> to = accounts[0]
            }
        }
        if (from == null) {
            if (accounts.isNotEmpty()) from = accounts[0]
        }

        return from to to
    }

    internal fun isLikelyKbankSlip(text: String): Boolean {
        if (Regex("""MAKE\s*by\s*KBank|Kasikorn|KBank|\bK\+""", RegexOption.IGNORE_CASE).containsMatchIn(text)) {
            return true
        }
        if (text.contains("ธ.กสิกร") || text.contains("กสิกรไทย") || text.contains("ธนาคารกสิกร")) {
            return true
        }
        val lines = text.lines().map { it.trim() }
        // OCR often splits logo into "K" + "+"
        if (lines.any { it.equals("K+", ignoreCase = true) }) return true
        if (lines.any { it == "K" } && lines.any { it == "+" }) return true
        return false
    }

    /**
     * K+ vertical layout: header → from (name/bank/xxx) → to → footer.
     * Prefer first masked account as the from/to boundary; else largest Y gap.
     */
    private fun extractKbankPartiesFromLayout(layoutLines: List<OcrLine>): Pair<String?, String?>? {
        val sorted = layoutLines
            .map { line ->
                val normalized = normalizeOcrText(line.text).lines().firstOrNull()?.trim().orEmpty()
                line.copy(text = normalized)
            }
            .filter { it.text.isNotEmpty() }
            .sortedWith(compareBy({ it.yCenter }, { it.xLeft }))
        if (sorted.size < 4) return null

        val texts = sorted.map { it.text }
        val footerIdx = texts.indexOfFirst { isKbankFooterLine(it) }.let { if (it < 0) texts.size else it }
        val headerEnd = texts.withIndex()
            .lastOrNull { (idx, line) -> idx < footerIdx && isKbankHeaderLine(line) }
            ?.index
            ?.plus(1)
            ?: 0
        if (headerEnd >= footerIdx) return null
        val body = sorted.subList(headerEnd, footerIdx)
        if (body.isEmpty()) return null

        val splitAt = findKbankFromToSplitIndex(body) ?: return null
        val fromText = body.subList(0, splitAt + 1).joinToString("\n") { it.text }
        val toText = body.subList(splitAt + 1, body.size).joinToString("\n") { it.text }
        return partiesFromKbankSections(fromText, toText)
    }

    /** Same section rules as layout path, using OCR line order only. */
    private fun extractKbankPartiesFromText(text: String): Pair<String?, String?>? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size < 4) return null
        val footerIdx = lines.indexOfFirst { isKbankFooterLine(it) }.let { if (it < 0) lines.size else it }
        val headerEnd = lines.withIndex()
            .lastOrNull { (idx, line) -> idx < footerIdx && isKbankHeaderLine(line) }
            ?.index
            ?.plus(1)
            ?: 0
        if (headerEnd >= footerIdx) return null
        val body = lines.subList(headerEnd, footerIdx)
        if (body.isEmpty()) return null

        val synthetic = body.mapIndexed { i, line -> OcrLine(line, yCenter = i * 10f) }
        val splitAt = findKbankFromToSplitIndex(synthetic) ?: return null
        val fromText = body.subList(0, splitAt + 1).joinToString("\n")
        val toText = body.subList(splitAt + 1, body.size).joinToString("\n")
        return partiesFromKbankSections(fromText, toText)
    }

    private fun partiesFromKbankSections(fromText: String, toText: String): Pair<String?, String?>? {
        val fromBlocks = extractTransferPartyBlocks(fromText)
        val toBlocks = extractTransferPartyBlocks(toText)
        var from = fromBlocks.firstOrNull()
        var to = toBlocks.firstOrNull()
        if (to == null && toText.isNotBlank()) {
            to = extractPayeeHintName(toText)
                ?: extractNameLines(toText).firstOrNull()
                ?: extractAllMaskedAccounts(toText).firstOrNull()
                ?: extractBillerOnlyDisplay(toText)
        }
        if (from == null && fromText.isNotBlank()) {
            from = extractNameLines(fromText).firstOrNull()
                ?: extractAllMaskedAccounts(fromText).firstOrNull()
        }
        if (from == null && to == null) return null
        return from to to
    }

    /** When to-block is only biller name + refs (no bank mask). */
    private fun extractBillerOnlyDisplay(text: String): String? {
        val blocks = extractTransferPartyBlocks(text)
        if (blocks.isNotEmpty()) return blocks[0]
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val name = lines.firstOrNull { looksLikePartyNameLine(it) } ?: return null
        val refs = lines.dropWhile { it != name }.drop(1)
            .mapNotNull { extractBillerRefToken(it) }
            .take(2)
        return listOfNotNull(cleanPartyName(name), *refs.toTypedArray()).joinToString(" · ")
    }

    /**
     * Index of last line belonging to the **from** block.
     * Prefer end of first masked account; else bank line before next party; else largest Y gap.
     */
    private fun findKbankFromToSplitIndex(body: List<OcrLine>): Int? {
        if (body.isEmpty()) return null
        val firstMask = body.indexOfFirst { extractMaskedAccountToken(it.text) != null }
        if (firstMask >= 0) return firstMask

        for (i in body.indices) {
            val line = body[i].text
            if (!isBankOnlyLine(line) && extractBank(line) == null) continue
            val nextParty = ((i + 1)..body.lastIndex).firstOrNull { j ->
                looksLikePartyNameLine(body[j].text)
            }
            if (nextParty != null) return i
        }

        if (body.size < 2) return null
        val gaps = (0 until body.lastIndex).map { i ->
            i to (body[i + 1].yCenter - body[i].yCenter)
        }
        val median = gaps.map { it.second }.sorted()[gaps.size / 2]
        val (bestIdx, bestGap) = gaps.maxBy { it.second }
        // Need a clear arrow-sized gap (and not splitting inside a tight name/bank cluster)
        if (bestGap > median * 1.75f && bestGap >= 12f && bestIdx in 0 until body.lastIndex) {
            return bestIdx
        }
        return null
    }

    private fun isKbankHeaderLine(line: String): Boolean {
        val t = line.trim()
        if (t.equals("K+", ignoreCase = true) || t == "K" || t == "+") return true
        if (outKeywords.containsMatchIn(t) || inKeywords.containsMatchIn(t)) return true
        if (thaiMonthDateTimePattern.containsMatchIn(t) || thaiDateTimePattern.containsMatchIn(t)) return true
        if (thaiMonthDateOnlyPattern.containsMatchIn(t) || thaiMonthDayOnlyPattern.containsMatchIn(t)) return true
        if (ocrGarbledMonthDateTimePattern.containsMatchIn(t)) return true
        if (looseDayYearTimePattern.containsMatchIn(t)) return true
        if (t.equals("MAKE by KBank", ignoreCase = true)) return true
        return false
    }

    private fun isKbankFooterLine(line: String): Boolean {
        val t = line.trim()
        if (amountKeyword.containsMatchIn(t) || feeKeyword.containsMatchIn(t)) return true
        if (memoLabelPattern.containsMatchIn(t) || referencePattern.containsMatchIn(t)) return true
        if (t.startsWith("เลขที่รายการ") || t.startsWith("รหัสอ้างอิง")) return true
        if (t.startsWith("หมายเลขอ้างอิง") || t.startsWith("หมายเลขที่อ้างอิง")) return true
        if (t.contains("สแกน") && t.length <= 24) return true
        return false
    }

    /**
     * Walk slip lines for party blocks.
     * Supports P2P (name+bank+xxx) and billers (company / service / refs, no bank mask).
     */
    private fun extractTransferPartyBlocks(text: String): List<String> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val parties = mutableListOf<String>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i]
            if (isPartyMetaLine(line)) {
                i++
                continue
            }
            if (!looksLikePartyNameLine(line)) {
                i++
                continue
            }
            val cleanedName = cleanPartyName(line)
            if (cleanedName == null) {
                i++
                continue
            }
            var displayName: String = cleanedName

            var bank: String? = null
            var account: String? = null
            var end = i
            for (j in (i + 1)..minOf(i + 5, lines.lastIndex)) {
                val next = lines[j]
                if (standaloneFromLabel.matches(next) || standaloneToLabel.matches(next)) break
                // OCR junk: "%", "6", "|" between name and bank
                if (partyNoiseLine.matches(next)) {
                    end = j
                    continue
                }
                // Next company/person line → new party (don't merge บริษัท… into ผู้โอน)
                if (companyPrefixPattern.containsMatchIn(next) || nameLinePattern.containsMatchIn(next)) {
                    if (account != null || bank != null || nameLinePattern.containsMatchIn(line)) {
                        break
                    }
                }
                // บริษัท … จำกัด + (มหาชน)  OR Prompt / Pay — only for org/biller blocks
                if (isCompanyContinuationLine(next) || (next.contains("มหาชน") && next.length <= 12)) {
                    if (!isOrgDisplayName(displayName)) {
                        break
                    }
                    val frag = next.trim().trim('(', ')').trim()
                    if (frag.isNotEmpty() && !displayName.contains(frag)) {
                        displayName = if (next.contains("มหาชน") && displayName.startsWith("บริษัท") && !displayName.contains("บมจ")) {
                            "บมจ. " + displayName.removePrefix("บริษัท").trim()
                        } else {
                            "$displayName · $frag"
                        }
                    }
                    end = j
                    continue
                }
                // Multi-line org without account yet: service + บมจ.
                if (companyPrefixPattern.containsMatchIn(next)) {
                    val company = cleanPartyName(next) ?: next
                    if (!displayName.contains(company)) {
                        displayName = "$displayName · $company"
                    }
                    end = j
                    continue
                }
                // Wallet / bill-pay brands are the next party, not the sender's bank.
                if (walletPayeeBrandPattern.containsMatchIn(next) ||
                    looksLikeBillerOrOrgName(next) && extractBank(next) != null
                ) {
                    break
                }
                if (looksLikePartyNameLine(next)) break
                val masked = extractMaskedAccountToken(next)
                if (masked != null) {
                    account = masked
                    end = j
                    continue
                }
                if (account == null) {
                    val billerRef = extractBillerRefToken(next)
                    if (billerRef != null) {
                        account = billerRef
                        end = j
                        continue
                    }
                }
                if (isBankOnlyLine(next) ||
                    (
                        extractBank(next) != null &&
                            next.length <= 40 &&
                            !amountKeyword.containsMatchIn(next) &&
                            !walletPayeeBrandPattern.containsMatchIn(next)
                        )
                ) {
                    bank = next
                    end = j
                    continue
                }
                if (isPartyMetaLine(next)) break
            }

            val titled = nameLinePattern.containsMatchIn(line)
            val billerOrOrg = looksLikeBillerOrOrgName(line) ||
                companyPrefixPattern.containsMatchIn(displayName) ||
                displayName.contains("บมจ") ||
                displayName.contains("มหาชน")
            // First party usually needs bank/account/title; later parties may be billers with only a name
            val accept = account != null || bank != null || titled || billerOrOrg ||
                (parties.isNotEmpty() && looksLikePartyNameLine(line))
            if (accept) {
                parties += listOfNotNull(displayName, bank, account).joinToString(" · ")
                i = end + 1
            } else {
                i++
            }
        }
        return parties
    }

    /** Bill-pay refs under payee (not masked xxx accounts). */
    private fun extractBillerRefToken(line: String): String? {
        val t = line.trim()
        if (t.isEmpty() || extractMaskedAccountToken(t) != null) return null
        if (amountKeyword.containsMatchIn(t) || feeKeyword.containsMatchIn(t)) return null
        if (t.matches(Regex("""\d{6,22}"""))) return t
        if (t.matches(Regex("""(?i)[A-Z0-9]{8,28}""")) &&
            t.any { it.isDigit() } &&
            t.any { it.isLetter() }
        ) {
            return t
        }
        return null
    }

    private fun looksLikeBillerOrOrgName(line: String): Boolean {
        val t = line.trim()
        if (looksLikeTxnOrRefToken(t) || slipMetaLabelPattern.matches(t)) return false
        if (companyPrefixPattern.containsMatchIn(t)) return true
        if (t.contains("บมจ") || t.contains("มหาชน")) return true
        if (payeeNameHints.any { t.contains(it, ignoreCase = true) }) return true
        if (t.contains("ระบบรับชำระ") || t.contains("คิวอาร์") || t.contains("เซอร์วิส")) return true
        // Latin biller labels: STORYLOG, ELEMENT PAYME, CP AXTRA…
        val letters = t.filter { it.isLetter() }
        if (letters.length in 3..40 &&
            letters.all { it.isUpperCase() || it.isWhitespace() } &&
            t.any { it.isLetter() } &&
            t.none { it.isDigit() } &&
            t.none { it in '\u0E00'..'\u0E7F' } &&
            !isCompanyContinuationLine(t)
        ) {
            return true
        }
        return false
    }

    private fun extractAllMaskedAccounts(text: String): List<String> =
        text.lines().mapNotNull { extractMaskedAccountToken(it.trim()) }.distinct()

    private fun isPartyMetaLine(line: String): Boolean {
        if (standaloneFromLabel.matches(line) || standaloneToLabel.matches(line)) return true
        if (amountKeyword.containsMatchIn(line) || feeKeyword.containsMatchIn(line)) return true
        if (memoLabelPattern.containsMatchIn(line) || referencePattern.containsMatchIn(line)) return true
        if (slipMetaLabelPattern.matches(line) || looksLikeTxnOrRefToken(line)) return true
        if (outKeywords.containsMatchIn(line) || inKeywords.containsMatchIn(line)) return true
        if (thaiMonthDateTimePattern.containsMatchIn(line) || thaiDateTimePattern.containsMatchIn(line)) return true
        return false
    }

    private fun looksLikePartyNameLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return false
        if (partyNoiseLine.matches(trimmed)) return false
        if (looksLikeTxnOrRefToken(trimmed) || slipMetaLabelPattern.matches(trimmed)) return false
        if (isCompanyContinuationLine(trimmed) || trimmed.contains("มหาชน") && trimmed.length <= 12) {
            return false
        }
        if (isMaskedAccount(trimmed) || isBankOnlyLine(trimmed)) return false
        if (extractBillerRefToken(trimmed) != null && trimmed.none { it.isLetter() || it in '\u0E00'..'\u0E7F' }) {
            return false
        }
        if (isPartyMetaLine(trimmed)) return false
        if (nameLinePattern.containsMatchIn(trimmed)) return true
        if (looksLikeBillerOrOrgName(trimmed)) return true
        if (walletPayeeBrandPattern.containsMatchIn(trimmed)) return true
        // Untitled Thai person/org/service (OCR may drop titles; billers can be long)
        val thai = trimmed.count { it in '\u0E00'..'\u0E7F' }
        val letters = trimmed.count { it.isLetter() }.coerceAtLeast(1)
        if (thai >= 4 && thai * 10 >= letters * 6 && trimmed.length in 4..80) {
            val words = trimmed.split(Regex("""\s+"""))
            if (words.size in 1..8 && !skipPartyLine.containsMatchIn(trimmed)) return true
        }
        // Latin person/company (ALL CAPS or Title Case short lines)
        if (trimmed.length in 3..48 &&
            trimmed.any { it.isLetter() } &&
            trimmed.none { it in '\u0E00'..'\u0E7F' } &&
            trimmed.all { it.isLetter() || it.isWhitespace() || it == '.' || it == ',' || it == '-' } &&
            !trimmed.matches(Regex("""(?i)x{2,}[-x0-9]+""")) &&
            !isCompanyContinuationLine(trimmed)
        ) {
            return true
        }
        return false
    }

    private fun isCompanyContinuationLine(line: String): Boolean =
        companyContinuationLine.matches(line.trim())

    private fun isOrgDisplayName(name: String): Boolean =
        companyPrefixPattern.containsMatchIn(name) ||
            name.contains("บมจ") ||
            name.contains("บริษัท") ||
            looksLikeBillerOrOrgName(name)

    /** Append bank line + masked account that typically follow a name on Thai slips. */
    private fun attachNearbyIdentity(name: String?, text: String): String? {
        if (name == null) return null
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        val idx = lines.indexOfFirst { line ->
            line == name || cleanPartyName(line) == name || line.contains(name)
        }
        if (idx < 0) return name

        var bankLine: String? = null
        var account: String? = null
        for (j in (idx + 1)..minOf(idx + 5, lines.lastIndex)) {
            val line = lines[j]
            // Skip OCR junk between name and bank (e.g. "%", "6")
            if (partyNoiseLine.matches(line)) continue
            if (isCompanyContinuationLine(line) || (line.contains("มหาชน") && line.length <= 12)) {
                continue
            }
            // Masked accounts must win before skipPartyLine (which also matches xxx…)
            val masked = if (account == null) extractMaskedAccountToken(line) else null
            if (masked != null) {
                account = masked
                continue
            }
            if (bankLine == null && isBankOnlyLine(line)) {
                bankLine = line
                continue
            }
            // PromptPay account lines like xxx-xxx-3455
            if (account == null && extractMaskedAccountToken(line.replace(" ", "")) != null) {
                account = extractMaskedAccountToken(line.replace(" ", ""))
                continue
            }
            if (shouldStopPartyIdentityScan(line)) break
        }
        if (bankLine == null && account == null) return name
        return listOfNotNull(name, bankLine, account).joinToString(" · ")
    }

    private fun extractMaskedAfterLabel(text: String, labelPattern: Regex): String? {
        val lines = text.lines().map { it.trim() }
        for (i in lines.indices) {
            if (!labelPattern.matches(lines[i])) continue
            for (j in (i + 1)..minOf(i + 4, lines.lastIndex)) {
                extractMaskedAccountToken(lines[j])?.let { return it }
            }
        }
        return null
    }

    private fun extractMaskedAccountToken(line: String): String? {
        val compact = line.replace(" ", "")
        Regex("""(?i)x{2,}[-x0-9]+""").find(compact)?.value?.let { return it }
        // Krungsri merchant / Thung Ngern: 010-753-7-XXXX820-5 / 006-990-0-XXXX256-2
        Regex("""(?i)\d{2,4}-\d{2,4}-\d-x{2,}[0-9x\-]*""").find(compact)?.value?.let { return it }
        // Krungsri alternate mask: X-XXXX-XXXX1-17-3
        Regex("""(?i)x(?:-x{2,})+(?:-?\d+)+[-x0-9]*""").find(compact)?.value?.let { return it }
        return null
    }

    private fun isMaskedAccount(value: String): Boolean {
        val compact = value.replace(" ", "")
        if (Regex("""(?i)^x{2,}[-x0-9]+$""").matches(compact)) return true
        if (Regex("""(?i)^\d{2,4}-\d{2,4}-\d-x{2,}[0-9x\-]*$""").matches(compact)) return true
        if (Regex("""(?i)^x(?:-x{2,})+(?:-?\d+)+[-x0-9]*$""").matches(compact)) return true
        return false
    }

    private fun shouldStopPartyIdentityScan(line: String): Boolean {
        if (standaloneFromLabel.matches(line) || standaloneToLabel.matches(line)) return true
        if (fromLabelPattern.containsMatchIn(line) || toLabelPattern.containsMatchIn(line)) return true
        if (nameLinePattern.containsMatchIn(line)) return true
        if (payeeNameHints.any { line.contains(it, ignoreCase = true) }) return true
        if (skipPartyLine.containsMatchIn(line)) return true
        if (amountKeyword.containsMatchIn(line) || feeKeyword.containsMatchIn(line)) return true
        if (memoLabelPattern.containsMatchIn(line)) return true
        return false
    }

    private fun extractPartyNameInline(text: String, pattern: Regex): String? {
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val match = pattern.find(trimmed) ?: continue
            val rest = match.groupValues[1].trim()
            if (rest.isEmpty()) continue
            val value = cleanPartyName(rest) ?: continue
            return value
        }
        return null
    }

    private fun extractPartyAfterStandaloneLabel(text: String, labelPattern: Regex): String? {
        val lines = text.lines()
        for (i in lines.indices) {
            if (!labelPattern.matches(lines[i].trim())) continue
            for (j in (i + 1) until lines.size) {
                val candidate = cleanPartyName(lines[j]) ?: continue
                if (skipPartyLine.containsMatchIn(candidate)) continue
                if (isBankOnlyLine(candidate)) continue
                if (isMaskedAccount(candidate)) continue
                return candidate
            }
        }
        return null
    }

    private fun extractNameLines(text: String): List<String> {
        val names = mutableListOf<String>()
        for (line in text.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (looksLikeTxnOrRefToken(trimmed) || slipMetaLabelPattern.matches(trimmed)) continue
            if (nameLinePattern.containsMatchIn(trimmed)) {
                cleanPartyName(trimmed)?.let { names += it }
                continue
            }
            // Latin ALL-CAPS person/company short lines (Krungsri style)
            if (trimmed.length in 5..40 &&
                trimmed.all { it.isUpperCase() || it.isWhitespace() || it == '.' || it == ',' } &&
                trimmed.any { it.isLetter() } &&
                trimmed.none { it.isDigit() } &&
                !isBankOnlyLine(trimmed)
            ) {
                cleanPartyName(trimmed)?.let { names += it }
            }
        }
        return names.distinct()
    }

    private fun extractPayeeHintName(text: String): String? {
        for (line in text.lines()) {
            val trimmed = line.trim()
            for (hint in payeeNameHints) {
                if (trimmed.contains(hint, ignoreCase = true)) {
                    return cleanPartyName(trimmed)
                }
            }
        }
        return null
    }

    /** Prefer English/canonical wallet labels when Thai OCR is garbled on Krungsri slips. */
    private fun preferCanonicalPayeeLabel(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        lines.firstOrNull {
            it.contains("Rabbit Line Pay", ignoreCase = true) ||
                it.contains("Rabbit LINE Pay", ignoreCase = true)
        }?.let { return cleanPartyName(it) ?: it }

        lines.firstOrNull {
            it.contains("TrueMoney", ignoreCase = true) ||
                it.equals("truemoney", ignoreCase = true)
        }?.let {
            val withWallet = lines.firstOrNull { line ->
                line.contains("e-Wallet", ignoreCase = true) || line.contains("e-wallet")
            }
            return cleanPartyName(withWallet ?: "ทรูมันนี่ (e-Wallet)") ?: "ทรูมันนี่ (e-Wallet)"
        }

        lines.firstOrNull {
            it.contains("Advanced mPAY", ignoreCase = true) ||
                it.contains("AdvancedmPAY", ignoreCase = true)
        }?.let { return "บริษัท แอดวานซ์ เอ็มเปย์ จำกัด" }

        lines.firstOrNull { it.contains("ถุงเงิน") }?.let { return cleanPartyName(it) ?: it }
        lines.firstOrNull { it.contains("บางจาก", ignoreCase = true) }?.let {
            return cleanPartyName(it) ?: it
        }
        lines.firstOrNull {
            it.equals("ELEVEN", ignoreCase = true) || it.contains("7-Eleven", ignoreCase = true)
        }?.let {
            // K+ True Money top-up via 7-Eleven when Thai company name is lost
            if (text.contains("truemoney", ignoreCase = true) ||
                text.contains("ทรู", ignoreCase = true) ||
                text.lines().any { line -> line.matches(Regex("""\d{18,22}""")) }
            ) {
                return "บริษัท ทรู มันนี่ จำกัด"
            }
            return "7-Eleven"
        }
        lines.firstOrNull {
            it.contains("e-Wallet", ignoreCase = true) || it.contains("e-wallet")
        }?.let { return cleanPartyName(it) ?: it }
        return null
    }

    /** K+ often OCR-splits the PromptPay badge into "Prompt" + "Pay". */
    private fun upgradePromptPayPayee(text: String, currentTo: String?): String? {
        val splitPromptPay =
            text.lines().any { it.equals("Prompt", ignoreCase = true) } &&
                text.lines().any { it.equals("Pay", ignoreCase = true) }
        if (!splitPromptPay && currentTo?.contains("PromptPay", ignoreCase = true) == true) {
            return currentTo
        }
        if (!splitPromptPay) return currentTo

        val primary = currentTo?.split(" · ")?.firstOrNull()?.trim().orEmpty()
        // Real person/org name already found — keep it (Prompt/Pay is just the method badge).
        val isPromptBadge = primary.equals("Prompt", ignoreCase = true) ||
            primary.equals("Pay", ignoreCase = true) ||
            (
                primary.startsWith("Prompt", ignoreCase = true) &&
                    !primary.contains("PromptPay", ignoreCase = true)
                )
        val primaryIsAccountOnly = primary.isNotEmpty() && extractMaskedAccountToken(primary) == primary.replace(" ", "")
        if (primary.isNotEmpty() && !isPromptBadge && !primaryIsAccountOnly &&
            (
                nameLinePattern.containsMatchIn(primary) ||
                    primary.count { it in '\u0E00'..'\u0E7F' } >= 4 ||
                    looksLikeBillerOrOrgName(primary)
                )
        ) {
            return currentTo
        }

        if (!isPromptBadge && !primaryIsAccountOnly && primary.isNotEmpty()) {
            return currentTo
        }

        val accounts = extractAllMaskedAccounts(text)
        val payeeAccount = when {
            primaryIsAccountOnly -> primary
            accounts.size >= 2 -> accounts.last()
            accounts.size == 1 -> accounts[0]
            else -> null
        }
        return listOfNotNull("PromptPay", payeeAccount).joinToString(" · ")
    }

    private fun extractMemo(text: String): String? {
        val lines = text.lines()
        for (i in lines.indices) {
            val trimmed = lines[i].trim()
            if (trimmed.isEmpty()) continue
            val match = memoLabelPattern.find(trimmed) ?: continue
            val sameLine = match.groupValues[1].trim().replace(Regex("""\s+"""), " ")
            if (sameLine.isNotEmpty()) return sameLine.take(200)
            // Label alone → next line
            val next = lines.getOrNull(i + 1)?.trim().orEmpty()
            if (next.isNotEmpty() && !skipPartyLine.containsMatchIn(next)) {
                return next.take(200)
            }
        }
        return null
    }

    private fun cleanPartyName(raw: String): String? {
        var value = raw.trim()
            .replace(Regex("""\s+"""), " ")
            .trim()
        // Strip trailing masked account on same line
        value = value.replace(Regex("""\s*xxx[-x0-9]*$""", RegexOption.IGNORE_CASE), "").trim()
        if (value.isEmpty()) return null
        val digitsOnly = value.filter { it.isDigit() }
        if (value.none { it.isLetter() } && digitsOnly.length >= 12) return null
        if (isBankOnlyLine(value) && value.length <= 24 && value.split(' ').size <= 2) {
            return null
        }
        if (isMaskedAccount(value)) return null
        return value.take(120)
    }

    private fun isBankOnlyLine(value: String): Boolean {
        val bank = extractBank(value) ?: return false
        val stripped = value
            .replace(Regex("""ธ\.?"""), "")
            .replace(Regex("""\s+"""), "")
        return bankKeywords.any { (keyword, code) ->
            code == bank && value.equals(keyword, ignoreCase = true)
        } || stripped.length <= bank.length + 4
    }

    private fun extractSpentAt(text: String): String? {
        isoDateTimePattern.find(text)?.groupValues?.get(1)?.let { iso ->
            return if (iso.endsWith("Z") || Regex("""[+-]\d{2}:\d{2}$""").containsMatchIn(iso)) {
                iso
            } else {
                "${iso}+07:00"
            }
        }

        thaiMonthDateTimePattern.find(text)?.let { match ->
            formatThaiMonthDateTime(match)?.let { return it }
        }

        // OCR often splits: "7 มิ.ย. 69" then "07:03 น."
        // Krungsri often splits: "08 มิ.ย." then "2569 22:02:26"
        extractThaiMonthDatePlusNearbyTime(text)?.let { return it }

        val match = thaiDateTimePattern.find(text)
        if (match != null) {
            val day = match.groupValues[1].padStart(2, '0')
            val month = match.groupValues[2].padStart(2, '0')
            val year = normalizeYear(match.groupValues[3])
            val hour = match.groupValues[4].padStart(2, '0')
            val minute = match.groupValues[5]
            val second = match.groupValues.getOrNull(6)?.ifBlank { null } ?: "00"
            slipTimestampOrNull(year, month, day, hour, minute, second)?.let { return it }
        }

        // K+ เลขที่รายการ encodes day-of-year + HHMMSS (016185192458BPM…)
        // Prefer this before Latin-garbled month guesses.
        extractSpentAtFromKbankTxnId(text)?.let { return it }

        // Latin OCR garble of Thai months: "18 n.A. 2569 14:06:58", "08 U.J. 2569 22:02:26"
        ocrGarbledMonthDateTimePattern.find(text)?.let { garbled ->
            formatOcrGarbledMonthDateTime(garbled)?.let { return it }
        }
        extractOcrGarbledMonthDatePlusNearbyTime(text)?.let { return it }

        // Numeric month after glue-split: "20 8 2569 09:18:27"
        numericMonthDateTimePattern.find(text)?.let { m ->
            val day = m.groupValues[1].padStart(2, '0')
            val monthNum = m.groupValues[2].toIntOrNull() ?: return@let
            if (monthNum !in 1..12) return@let
            val month = monthNum.toString().padStart(2, '0')
            val year = normalizeYear(m.groupValues[3])
            val hour = m.groupValues[4].padStart(2, '0')
            val minute = m.groupValues[5]
            val second = m.groupValues.getOrNull(6)?.ifBlank { null } ?: "00"
            slipTimestampOrNull(year, month, day, hour, minute, second)?.let { return it }
        }

        // Month OCR totally gone: "4 n.. 69 19:24" / "1 .. 69 01:05"
        extractSpentAtFromLooseDayYearTime(text)?.let { return it }

        // Gallery filename hints (appended by withFileNameHint) carry a full
        // timestamp, so they outrank the date-only strategies below.
        extractSpentAtFromYmdHmsToken(text)?.let { return it }
        extractSpentAtFromEpochMillis(text)?.let { return it }

        // Krungsri embeds YYYYMMDD in refs (e.g. 20260718160642407530) when month OCR fails.
        extractSpentAtFromEmbeddedYmd(text)?.let { return it }

        // Merchant refs often start with yyMMdd: 260618174054AINV…
        extractSpentAtFromShortYymmddRef(text)?.let { return it }

        return null
    }

    /** After normalize: "20 8 2569 09:18:27" from glued "20.8.256909:18:27". */
    private val numericMonthDateTimePattern = Regex(
        """(\d{1,2})\s+(\d{1,2})\s+(25\d{2}|6[0-9])\s+(\d{1,2})[:.](\d{2})(?::(\d{2}))?""",
    )

    /** yyMMddHHMMSS… in Krungsri merchant/txn refs. */
    private fun extractSpentAtFromShortYymmddRef(text: String): String? {
        val match = Regex(
            """(?<![0-9])(2[0-9])(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])(\d{2})(\d{2})(\d{2})[0-9A-Za-z]{2,}""",
        ).find(text) ?: return null
        val year = 2000 + match.groupValues[1].toInt()
        val month = match.groupValues[2]
        val day = match.groupValues[3]
        val hour = match.groupValues[4]
        val minute = match.groupValues[5]
        val second = match.groupValues[6]
        if (hour.toIntOrNull()?.let { it !in 0..23 } != false) return null
        if (minute.toIntOrNull()?.let { it !in 0..59 } != false) return null
        return "%04d-%s-%sT%s:%s:%s+07:00".format(year, month, day, hour, minute, second)
    }

    /**
     * KBank / K+ txn id: 016 + DDD (day of year) + HHMMSS + type/seq
     * e.g. 016185192458BPM19816 → 2026-07-04T19:24:58
     */
    private val kbankTxnIdPattern = Regex(
        // Top-up slips may omit the letter type code: 016183191043130636
        """(?i)(?<![0-9A-Z])0?16(\d{3})(\d{6})(?:[A-Z]{2,4}\d{3,}|\d{6,})""",
    )

    /** Day + junk month + BE year + time — K+ header when ก.ค. becomes n.. / n.A. / .. */
    private val looseDayYearTimePattern = Regex(
        """(\d{1,2})\s+\S{0,12}?\s*(69|6[0-9]|25\d{2})\s+(\d{1,2})[:.](\d{2})(?::(\d{2}))?\s*(?:น\.?|H\.?|u\.?)?""",
        RegexOption.IGNORE_CASE,
    )

    private fun extractSpentAtFromKbankTxnId(text: String): String? {
        // OCR frequently turns digits into O/I/l/| inside เลขที่รายการ.
        val candidates = Regex(
            """(?i)(?<![0-9A-Z])[O0]?16[0-9OIl|]{8,}[A-Z0-9OIl|]*""",
        ).findAll(text)
        for (candidate in candidates) {
            val cleaned = candidate.value
                .map { ch ->
                    when (ch) {
                        'O', 'o' -> '0'
                        'I', 'l', '|' -> '1'
                        else -> ch
                    }
                }
                .joinToString("")
            val match = kbankTxnIdPattern.find(cleaned) ?: continue
            val doy = match.groupValues[1].toIntOrNull() ?: continue
            val hhmmss = match.groupValues[2]
            if (doy !in 1..366 || hhmmss.length != 6) continue
            val hour = hhmmss.substring(0, 2).toIntOrNull() ?: continue
            val minute = hhmmss.substring(2, 4).toIntOrNull() ?: continue
            val second = hhmmss.substring(4, 6).toIntOrNull() ?: continue
            if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) continue
            val year = inferGregorianYearForSlip(text)
            val date = try {
                LocalDate.ofYearDay(year, doy)
            } catch (_: DateTimeException) {
                try {
                    LocalDate.ofYearDay(year - 1, doy)
                } catch (_: DateTimeException) {
                    continue
                }
            }
            return "%04d-%02d-%02dT%02d:%02d:%02d+07:00".format(
                date.year,
                date.monthValue,
                date.dayOfMonth,
                hour,
                minute,
                second,
            )
        }
        return null
    }

    private fun extractSpentAtFromLooseDayYearTime(text: String): String? {
        val match = looseDayYearTimePattern.find(text) ?: return null
        val dayNum = match.groupValues[1].toIntOrNull() ?: return null
        val year = normalizeYear(match.groupValues[2]).toIntOrNull() ?: return null
        val hour = match.groupValues[3].padStart(2, '0')
        val minute = match.groupValues[4]
        val second = match.groupValues.getOrNull(5)?.ifBlank { null } ?: "00"
        // Prefer month from KBank txn id when day-of-year agrees with this day.
        extractSpentAtFromKbankTxnId(text)?.let { fromTxn ->
            val txnDay = fromTxn.substring(8, 10).toIntOrNull()
            if (txnDay == dayNum) return fromTxn
        }
        // Month still unknown — try embedded YYYYMMDD that matches this day.
        extractSpentAtFromEmbeddedYmd(text)?.let { fromYmd ->
            val ymdDay = fromYmd.substring(8, 10).toIntOrNull()
            if (ymdDay == dayNum) {
                return fromYmd.substring(0, 11) + hour + ":" + minute + ":" +
                    second.padStart(2, '0') + "+07:00"
            }
        }
        // Merchant yyMMddHHMMSS… when header month OCR is only dots/junk.
        extractSpentAtFromShortYymmddRef(text)?.let { fromShort ->
            val shortDay = fromShort.substring(8, 10).toIntOrNull()
            if (shortDay == dayNum) return fromShort
        }
        return null
    }

    private fun inferGregorianYearForSlip(text: String): Int {
        Regex("""\b(25\d{2})\b""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
            return it - 543
        }
        Regex("""(?<!\d)(6[0-9])(?!\d)""").find(text)?.groupValues?.get(1)?.toIntOrNull()?.let {
            return 2500 + it - 543
        }
        Regex("""(?<!\d)(20[2-3]\d)(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])""").find(text)?.groupValues?.get(1)
            ?.toIntOrNull()
            ?.let { return it }
        return LocalDate.now(ZoneId.of("Asia/Bangkok")).year
    }

    /**
     * Common Latin OCR misreads of Thai month abbreviations (RapidOCR / weak Thai models).
     * Only used when year looks Buddhist (25xx) or short BE (69).
     */
    private val ocrGarbledMonthDateTimePattern = Regex(
        // "18 n.A. 2569 …", "4 n.. 69 …", "08 U.J. 2569 …"
        """(\d{1,2})\s*([A-Za-z](?:\s*\.\s*[A-Za-z]?)?|\.{1,3}|[A-Za-z]{2})\s*\.?\s*(\d{2,4})\s*[-–]?\s*(\d{1,2})[:.](\d{2})(?::(\d{2}))?\s*(?:น\.?|H\.?|u\.?)?""",
    )

    private val ocrGarbledDayMonthOnlyPattern = Regex(
        """^(\d{1,2})\s*([A-Za-z](?:\s*\.\s*[A-Za-z])?|[A-Za-z]{2})\.?$""",
    )

    private fun formatOcrGarbledMonthDateTime(match: MatchResult): String? {
        val monthKey = normalizeOcrGarbledThaiMonth(match.groupValues[2]) ?: return null
        val month = thaiMonthToNumber[monthKey] ?: return null
        val day = match.groupValues[1].padStart(2, '0')
        val year = normalizeYear(match.groupValues[3])
        val hour = match.groupValues[4].padStart(2, '0')
        val minute = match.groupValues[5]
        val second = match.groupValues.getOrNull(6)?.ifBlank { null } ?: "00"
        return slipTimestampOrNull(year, month, day, hour, minute, second)
    }

    private fun extractOcrGarbledMonthDatePlusNearbyTime(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        lines.forEachIndexed { index, line ->
            ocrGarbledDayMonthOnlyPattern.find(line)?.let { dayMonth ->
                val monthKey = normalizeOcrGarbledThaiMonth(dayMonth.groupValues[2]) ?: return@let
                val month = thaiMonthToNumber[monthKey] ?: return@let
                val next = lines.getOrNull(index + 1) ?: return@let
                val yt = yearAndTimePattern.find(next) ?: return@let
                val day = dayMonth.groupValues[1].padStart(2, '0')
                val year = normalizeYear(yt.groupValues[1])
                val hour = yt.groupValues[2].padStart(2, '0')
                val minute = yt.groupValues[3]
                val second = yt.groupValues.getOrNull(4)?.ifBlank { null } ?: "00"
                slipTimestampOrNull(year, month, day, hour, minute, second)?.let { return it }
            }
        }
        return null
    }

    private fun normalizeOcrGarbledThaiMonth(token: String): String? {
        val compact = token.lowercase().replace(" ", "").replace(".", "")
        return when (compact) {
            // ก.ค. → n.A. / n.. (common Latin OCR)
            "na", "ka", "kc", "n", "1a", "la" -> "ก.ค."
            // มิ.ย. → U.J. / uj
            "uj", "mj", "my", "u" -> "มิ.ย."
            // พ.ค. / เม.ย. / ส.ค. guesses seen in weak OCR
            "pc", "pk" -> "พ.ค."
            "my2", "ey" -> "เม.ย."
            "sc", "sk" -> "ส.ค."
            "ky", "kyy" -> "ก.ย."
            "tc", "tk" -> "ต.ค."
            "py", "pn" -> "พ.ย."
            "tc2", "dc", "th" -> "ธ.ค."
            "mc", "mk" -> "ม.ค."
            "kp", "kp2" -> "ก.พ."
            else -> null
        }
    }

    /**
     * `YYYYMMDD_HHMMSS` / `YYYYMMDDHHMMSS`, as K+ and Krungsri write into the
     * saved filename (`004999006314305_20260604_182602`). Unlike the plain
     * YYYYMMDD strategy this recovers the clock too, so the transaction lands
     * at the right time of day rather than at midnight.
     */
    private val ymdHmsTokenPattern = Regex(
        """(?<!\d)(20[2-3]\d)(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])[_\-]?""" +
            """([01]\d|2[0-3])([0-5]\d)([0-5]\d)(?!\d)""",
    )

    private fun extractSpentAtFromYmdHmsToken(text: String): String? {
        val m = ymdHmsTokenPattern.find(text) ?: return null
        return slipTimestampOrNull(
            m.groupValues[1],
            m.groupValues[2],
            m.groupValues[3],
            m.groupValues[4],
            m.groupValues[5],
            m.groupValues[6],
        )
    }

    /**
     * Krungthai NEXT names its saved slips with the epoch-millisecond
     * timestamp (`1780401364698.jpg`) and puts no clock on the slip itself.
     *
     * Only a line that is *nothing but* the number is accepted: withFileNameHint
     * appends the filename stem as its own line, so this keeps a stray 13-digit
     * account or reference number in the OCR body from being read as a date.
     */
    private val epochMillisLinePattern = Regex("""^(1[5-8]\d{11})$""")

    private fun extractSpentAtFromEpochMillis(text: String): String? {
        for (line in text.lines()) {
            val match = epochMillisLinePattern.find(line.trim()) ?: continue
            val millis = match.groupValues[1].toLongOrNull() ?: continue
            val local = try {
                java.time.Instant.ofEpochMilli(millis).atZone(BANGKOK)
            } catch (_: DateTimeException) {
                continue
            }
            if (local.year !in MIN_SLIP_YEAR..MAX_SLIP_YEAR) continue
            return "%04d-%02d-%02dT%02d:%02d:%02d+07:00".format(
                local.year,
                local.monthValue,
                local.dayOfMonth,
                local.hour,
                local.minute,
                local.second,
            )
        }
        return null
    }

    /** YYYYMMDD inside Krungsri reference numbers; time from nearby clock if present. */
    private fun extractSpentAtFromEmbeddedYmd(text: String): String? {
        val longRef = Regex("""(?<!\d)(20[2-3]\d)(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])\d{6,}(?!\d)""")
        val shortYmd = Regex("""(?<!\d)(20[2-3]\d)(0[1-9]|1[0-2])(0[1-9]|[12]\d|3[01])(?!\d)""")
        val ymd = longRef.find(text) ?: shortYmd.find(text) ?: return null
        val year = ymd.groupValues[1]
        val month = ymd.groupValues[2]
        val day = ymd.groupValues[3]
        val headerTime = Regex(
            """\d{1,2}\s+\S{1,12}\s+(?:25\d{2}|\d{2})\s+(\d{1,2})[:.](\d{2})(?::(\d{2}))?""",
        ).find(text)
        val hour = headerTime?.groupValues?.get(1)?.padStart(2, '0') ?: "00"
        val minute = headerTime?.groupValues?.get(2) ?: "00"
        val second = headerTime?.groupValues?.getOrNull(3)?.ifBlank { null } ?: "00"
        return slipTimestampOrNull(year, month, day, hour, minute, second)
    }

    private val thaiMonthDateOnlyPattern = Regex(
        """(\d{1,2})\s*($thaiMonthToken)\s*\.?\s*(?:\d\s+)?(\d{2,4})""",
        RegexOption.IGNORE_CASE,
    )

    /** Month + year + time without day (day on previous line) — K+ header OCR. */
    private val thaiMonthYearTimeNoDayPattern = Regex(
        """^($thaiMonthToken)\s*\.?\s*(\d{2,4})\s*[-–]?\s*(\d{1,2})[:.](\d{2})(?::(\d{2}))?\s*(?:น\.?)?$""",
        RegexOption.IGNORE_CASE,
    )

    /** Day + month only (year on next line) — Krungsri header OCR. */
    private val thaiMonthDayOnlyPattern = Regex(
        """^(\d{1,2})\s*($thaiMonthToken)\.?$""",
        RegexOption.IGNORE_CASE,
    )

    private val yearAndTimePattern = Regex(
        """^(\d{2,4})\s+(\d{1,2})[:.](\d{2})(?::(\d{2}))?\s*(?:น\.?)?$""",
    )

    private val timeOnlyPattern = Regex(
        """(\d{1,2})[:.](\d{2})(?::(\d{2}))?\s*(?:น\.?)?""",
    )

    private fun extractThaiMonthDatePlusNearbyTime(text: String): String? {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }
        lines.forEachIndexed { index, line ->
            // "08 มิ.ย." + "2569 22:02:26"
            thaiMonthDayOnlyPattern.find(line)?.let { dayMonth ->
                val next = lines.getOrNull(index + 1) ?: return@let
                val yt = yearAndTimePattern.find(next) ?: return@let
                val monthKey = normalizeThaiMonthToken(dayMonth.groupValues[2]) ?: return@let
                val month = thaiMonthToNumber[monthKey] ?: return@let
                val day = dayMonth.groupValues[1].padStart(2, '0')
                val year = normalizeYear(yt.groupValues[1])
                val hour = yt.groupValues[2].padStart(2, '0')
                val minute = yt.groupValues[3]
                val second = yt.groupValues.getOrNull(4)?.ifBlank { null } ?: "00"
                slipTimestampOrNull(year, month, day, hour, minute, second)?.let { return it }
            }

            // "4" + "ก.ค. 69 19:24 น."
            if (line.matches(Regex("""^\d{1,2}$"""))) {
                val next = lines.getOrNull(index + 1) ?: return@forEachIndexed
                thaiMonthYearTimeNoDayPattern.find(next)?.let { m ->
                    val monthKey = normalizeThaiMonthToken(m.groupValues[1]) ?: return@let
                    val month = thaiMonthToNumber[monthKey] ?: return@let
                    val day = line.padStart(2, '0')
                    val year = normalizeYear(m.groupValues[2])
                    val hour = m.groupValues[3].padStart(2, '0')
                    val minute = m.groupValues[4]
                    val second = m.groupValues.getOrNull(5)?.ifBlank { null } ?: "00"
                    slipTimestampOrNull(year, month, day, hour, minute, second)?.let { return it }
                }
            }

            val dateMatch = thaiMonthDateOnlyPattern.find(line) ?: return@forEachIndexed
            // Same line already handled by thaiMonthDateTimePattern when time present
            if (timeOnlyPattern.containsMatchIn(line.substring(dateMatch.range.last + 1))) {
                return@forEachIndexed
            }
            val timeLine = listOfNotNull(lines.getOrNull(index + 1), lines.getOrNull(index + 2))
                .firstOrNull { timeOnlyPattern.containsMatchIn(it) }
                ?: return@forEachIndexed
            val timeMatch = timeOnlyPattern.find(timeLine) ?: return@forEachIndexed
            val monthKey = normalizeThaiMonthToken(dateMatch.groupValues[2]) ?: return@forEachIndexed
            val month = thaiMonthToNumber[monthKey] ?: return@forEachIndexed
            val day = dateMatch.groupValues[1].padStart(2, '0')
            val year = normalizeYear(dateMatch.groupValues[3])
            val hour = timeMatch.groupValues[1].padStart(2, '0')
            val minute = timeMatch.groupValues[2]
            val second = timeMatch.groupValues.getOrNull(3)?.ifBlank { null } ?: "00"
            slipTimestampOrNull(year, month, day, hour, minute, second)?.let { return it }
        }
        return null
    }

    private fun formatThaiMonthDateTime(match: MatchResult): String? {
        val day = match.groupValues[1].padStart(2, '0')
        val monthKey = normalizeThaiMonthToken(match.groupValues[2]) ?: return null
        val month = thaiMonthToNumber[monthKey] ?: return null
        val year = normalizeYear(match.groupValues[3])
        val hour = match.groupValues[4].padStart(2, '0')
        val minute = match.groupValues[5]
        val second = match.groupValues.getOrNull(6)?.ifBlank { null } ?: "00"
        return slipTimestampOrNull(year, month, day, hour, minute, second)
    }

    private fun normalizeThaiMonthToken(token: String): String? {
        val compact = token.lowercase().replace(" ", "")
        return when {
            compact.startsWith("มค") || compact.startsWith("ม.ค") -> "ม.ค."
            compact.startsWith("กพ") || compact.startsWith("ก.พ") -> "ก.พ."
            compact.startsWith("มีค") || compact.startsWith("มี.ค") -> "มี.ค."
            compact.startsWith("เมย") || compact.startsWith("เม.ย") -> "เม.ย."
            compact.startsWith("พค") || compact.startsWith("พ.ค") -> "พ.ค."
            compact.startsWith("มิย") || compact.startsWith("มิ.ย") -> "มิ.ย."
            compact.startsWith("กค") || compact.startsWith("ก.ค") -> "ก.ค."
            compact.startsWith("สค") || compact.startsWith("ส.ค") -> "ส.ค."
            compact.startsWith("กย") || compact.startsWith("ก.ย") -> "ก.ย."
            compact.startsWith("ตค") || compact.startsWith("ต.ค") -> "ต.ค."
            compact.startsWith("พย") || compact.startsWith("พ.ย") -> "พ.ย."
            compact.startsWith("ธค") || compact.startsWith("ธ.ค") -> "ธ.ค."
            else -> null
        }
    }

    /**
     * Builds the slip timestamp, or null when the pieces don't form a real one.
     *
     * OCR routinely glues the year to the clock — "13 มิ.ย. 6917:15 น." — and a
     * greedy year group then reads that as year 691 at 07:15. Rejecting the
     * implausible year lets [extractSpentAt] fall through to a later strategy
     * (usually the K+ txn id, which carries the same timestamp unambiguously)
     * instead of writing a year-691 transaction.
     */
    /** Plausible Gregorian years for a bank slip; anything outside is OCR noise. */
    private const val MIN_SLIP_YEAR = 2000
    private const val MAX_SLIP_YEAR = 2100

    private val BANGKOK: ZoneId = ZoneId.of("Asia/Bangkok")

    private fun slipTimestampOrNull(
        year: String,
        month: String,
        day: String,
        hour: String,
        minute: String,
        second: String,
    ): String? {
        val y = year.toIntOrNull() ?: return null
        if (y !in MIN_SLIP_YEAR..MAX_SLIP_YEAR) return null
        val mo = month.toIntOrNull() ?: return null
        val d = day.toIntOrNull() ?: return null
        val h = hour.toIntOrNull() ?: return null
        val mi = minute.toIntOrNull() ?: return null
        val s = second.toIntOrNull() ?: return null
        if (h !in 0..23 || mi !in 0..59 || s !in 0..59) return null
        val date = try {
            LocalDate.of(y, mo, d)
        } catch (_: DateTimeException) {
            return null
        }
        return "%04d-%02d-%02dT%02d:%02d:%02d+07:00".format(
            date.year,
            date.monthValue,
            date.dayOfMonth,
            h,
            mi,
            s,
        )
    }

    private fun normalizeYear(raw: String): String {
        var year = raw
        if (year.length == 2) {
            // Thai slips: 69 → 2569 BE
            val short = year.toIntOrNull() ?: return year
            year = (2500 + short).toString()
        }
        val yearInt = year.toIntOrNull()
        if (yearInt != null && yearInt > 2400) {
            year = (yearInt - 543).toString()
        }
        return year
    }
}
