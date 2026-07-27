package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class SlipParserTest {
    @Test
    fun extractsAmountAndRef() {
        val raw = """
            โอนเงินสำเร็จ
            จำนวนเงิน 1,250.50 บาท
            รหัสอ้างอิง 20260727SCB001
            SCB
        """.trimIndent()
        val draft = SlipParser.parse(raw)
        assertEquals("1250.50", draft.amount)
        assertEquals("20260727SCB001", draft.reference)
    }
}
