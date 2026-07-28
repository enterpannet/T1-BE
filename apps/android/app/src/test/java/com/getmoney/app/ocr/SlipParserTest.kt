package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlipParserTest {
    @Test
    fun extractsAmountAndRef_thaiLabels() {
        val raw = """
            โอนเงินสำเร็จ
            จำนวนเงิน 1,250.50 บาท
            รหัสอ้างอิง 20260727SCB001
            SCB
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("1250.50", draft.amount)
        assertEquals("20260727SCB001", draft.reference)
        assertEquals("SCB", draft.bank)
    }

    @Test
    fun prefersAmountNearEnglishKeyword_notAccountNumber() {
        val raw = """
            Transfer Successful
            From A/C 1234567890123
            Amount 350.00 THB
            To PromptPay
            Ref 9A8B7C6D5E
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("350.00", draft.amount)
        assertEquals("PromptPay", draft.bank)
    }

    @Test
    fun bilingualSlip_picksBahtAmountOverDateAndPhone() {
        val raw = """
            กสิกรไทย / KBank
            28/07/2026 21:15
            เบอร์ 0812345678
            จำนวนเงิน 2,499.00 บาท
            Amount 2,499.00 THB
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("2499.00", draft.amount)
        assertEquals("KBank", draft.bank)
        assertEquals("2026-07-28T21:15:00+07:00", draft.spentAtIso)
    }

    @Test
    fun convertsThaiDigitsAndBuddhistYear() {
        val raw = """
            จำนวนเงิน ๑,๒๕๐.๕๐ บาท
            15/07/2569 10:30
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("1250.50", draft.amount)
        assertEquals("2026-07-15T10:30:00+07:00", draft.spentAtIso)
    }

    @Test
    fun extractsSplitThaiDateAndTimeLines() {
        val raw = """
            โอนเงินสำเร็จ
            7 มิ.ย. 69
            07:03 น.
            นาย ทดสอบ
            จำนวน 200 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("2026-06-07T07:03:00+07:00", draft.spentAtIso)
        assertEquals("200.00", draft.amount)
    }

    @Test
    fun doesNotPreferBuddhistYearOrTimeOverAmount() {
        // OCR often keeps date "… 69 16:07" while amount is on its own lines.
        // Without date/time exclusion, 69 can win when amount cues are weak.
        val raw = """
            โอนเงินสำเร็จ
            K+
            16 ก.ค. 69 16:07 น.
            นาย ทดสอบ ใจดี
            ธ.กสิกรไทย
            Xxx-x-x3523-x
            น.ส. ผู้รับ เงิน
            ธ.กสิกรไทย
            Xxx-x-x5079-x
            เลขที่รายการ: 016152010501BPM17567
            จำนวน
            200
            บาท
            ค่าธรรมเนียม
            0.00
            บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("200.00", draft.amount)
    }

    @Test
    fun dateLineAloneDoesNotBecomeAmount() {
        val raw = """
            โอนเงินสำเร็จ
            16 ก.ค. 69 16:07 น.
            ธ.กสิกรไทย
            เลขที่รายการ: ABC123456789
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("0", draft.amount)
    }

    @Test
    fun extractsBankFromFullThaiBankName() {
        val raw = """
            ธนาคารกสิกรไทย
            จำนวนเงิน 100.00 บาท
        """.trimIndent()
        assertEquals("KBank", SlipParser.parse(raw).bank)
    }

    @Test
    fun extractsKrungsriAlias() {
        val raw = """
            Bank of Ayudhya
            Amount 50.00 THB
        """.trimIndent()
        assertEquals("Krungsri", SlipParser.parse(raw).bank)
    }

    @Test
    fun extractsOutgoingDirectionAndParties() {
        val raw = """
            โอนเงินสำเร็จ
            จาก: สมชาย ใจดี
            ถึง: สมหญิง รักดี
            จำนวนเงิน 500.00 บาท
            กสิกรไทย
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals(TransferDirection.OUT, draft.direction)
        assertEquals("สมชาย ใจดี", draft.fromName)
        assertEquals("สมหญิง รักดี", draft.toName)
        assertEquals("โอนออก · จาก: สมชาย ใจดี → ถึง: สมหญิง รักดี", draft.note)
    }

    @Test
    fun extractsIncomingDirection() {
        val raw = """
            รับเงินสำเร็จ
            จาก: นายเอ
            ถึง: นายบี
            จำนวนเงิน 100.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals(TransferDirection.IN, draft.direction)
        assertEquals("นายเอ", draft.fromName)
        assertEquals("นายบี", draft.toName)
        assertEquals("รับเข้า · จาก: นายเอ → ถึง: นายบี", draft.note)
    }

    @Test
    fun extractsLabeledMemoIntoNote() {
        val raw = """
            โอนเงินสำเร็จ
            จาก: A
            ถึง: B
            บันทึก: ค่าข้าวเที่ยง
            จำนวนเงิน 80.00 บาท
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("ค่าข้าวเที่ยง", draft.note)
        assertEquals("A", draft.fromName)
        assertEquals("B", draft.toName)
    }
}
