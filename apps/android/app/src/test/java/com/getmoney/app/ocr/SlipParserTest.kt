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
    fun doesNotTreatBareLargestIntegerAsAmount() {
        val raw = """
            Transaction ID 998877665544
            Paid Amount 89.00 Baht
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("89.00", draft.amount)
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
}
