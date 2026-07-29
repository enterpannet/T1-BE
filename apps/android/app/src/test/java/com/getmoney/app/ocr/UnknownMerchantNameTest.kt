package com.getmoney.app.ocr

import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

/**
 * A shop the app has never seen must still be read correctly.
 *
 * Every merchant name here is invented, so none of them appears in any pattern
 * in [SlipParser] — not the wallet-brand list, not the company prefixes, not
 * the biller hints. If the recipient still comes back right, then reading a new
 * shop needs no rule added for it, which is the property that matters: a
 * spending app meets an unfamiliar merchant on most trips to the shops, and
 * needing a code change for each one would make it useless.
 *
 * The names deliberately break the shape the party-name test was originally
 * written for — Thai personal and company names — because that is what real
 * shop names do.
 */
class UnknownMerchantNameTest {

    /**
     * Line positions traced from a real K+ merchant slip, so the geometry the
     * parser reasons about (row grouping, block gaps) is the same.
     */
    private fun kplusMerchantSlip(shopLine: String, secondLine: String): OcrDocument {
        val rows = listOf(
            "ชำระเงินสำเร็จ" to 65f,
            "23 ก.ค. 69 12:27 น." to 155f,
            "นาย เกียรติศักดิ์ พ" to 327f,
            "ธ.กสิกรไทย" to 400f,
            "xxx-x-x3523-x" to 482f,
            shopLine to 713f,
            secondLine to 790f,
            "014000006713531" to 868f,
            "เลขที่รายการ:" to 1031f,
            "016204122734CPM05063" to 1104f,
            "จำนวน:" to 1180f,
            "300.00 บาท" to 1251f,
            "ค่าธรรมเนียม:" to 1330f,
            "0.00 บาท" to 1404f,
        )
        val lines = rows.map { (text, y) -> OcrLine(text = text, yCenter = y, xLeft = 290f) }
        return OcrDocument(
            text = lines.joinToString("\n") { it.text },
            lines = lines,
            engine = OcrDocument.Engine.Paddle,
        )
    }

    private fun assertRecipientRead(shopLine: String, secondLine: String) {
        val draft = SlipParser.parse(kplusMerchantSlip(shopLine, secondLine))
        val got = draft.toName.orEmpty()
        assertTrue(
            "recipient for an unregistered shop should contain \"$shopLine\", got \"$got\"",
            got.replace(" ", "").contains(shopLine.replace(" ", "")),
        )
        assertTrue("amount should still read", draft.amount == "300.00")
    }

    /**
     * Open defect, not a quirk of the fixture.
     *
     * The recipient comes back null when the shop name and the line under it
     * are both Latin and the line below those is a bare numeric biller code.
     * The real TikTokShop slip has the same first two lines and is read
     * correctly, so the numeric third line is implicated rather than the Latin
     * name — plausibly it is taken for the block's leading content, then
     * rejected as a reference token with no masked account left to fall back
     * to. Confirm before fixing; the guess is not the finding.
     *
     * Live examples in the corpus all have an alphanumeric code there
     * ("683PJPF6ICX4UL6PEUL"), so nothing labelled reproduces it yet, which is
     * why the accuracy harness stays green. Kept as a test rather than a note
     * so it surfaces the moment someone works on this path.
     */
    @Ignore("open defect: all-Latin shop name above a bare numeric biller code")
    @Test
    fun latinOnlyShopName() {
        assertRecipientRead("Zyxwq Coffee Roasters", "ZYXWQCOFFEE")
    }

    @Test
    fun shopNameOpeningWithABranchNumber() {
        assertRecipientRead("9271 ร้านลุงหนวด สาขาบางนา", "บจก. ลุงหนวด กรุ๊ป")
    }

    @Test
    fun shopNameMixingScripts() {
        assertRecipientRead("QQ มาร์ท (สาขาทดลอง)", "บจก. คิวคิว รีเทล")
    }

    @Test
    fun shopNameWithPunctuationOcrOftenMangles() {
        assertRecipientRead("Fnord+ Powered by Vexpay+", "A719769437845929")
    }
}
