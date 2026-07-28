package com.getmoney.app.ui.tx

import com.getmoney.app.data.api.TransactionResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionCsvExporterTest {
    @Test
    fun buildsExcelFriendlyCsvWithBomAndEscaping() {
        val rows = listOf(
            TransactionResponse(
                id = "1",
                amount = "109.00",
                spentAt = "2026-07-04T19:24:00+07:00",
                source = "slip",
                bank = "KBank",
                note = "โอนออก\nจาก: Alice → ถึง: Oishi\nmemo, with comma",
                createdAt = "2026-07-04T19:30:00+07:00",
            ),
            TransactionResponse(
                id = "2",
                amount = "10.00",
                spentAt = "2026-07-28T02:32:00+07:00",
                source = "manual",
                bank = null,
                note = null,
                createdAt = "2026-07-28T03:00:00+07:00",
            ),
        )

        val csv = TransactionCsvExporter.toCsv(rows)
        assertTrue("missing BOM", csv.startsWith("\uFEFF"))
        val body = csv.removePrefix("\uFEFF")
        assertTrue(body.startsWith("วันที่โอน,จำนวน,ธนาคาร,ทิศทาง,จาก,ถึง,บันทึกช่วยจำ,แหล่งที่มา,บันทึกเมื่อ"))
        assertTrue(body.contains("109.00"))
        assertTrue(body.contains("KBank"))
        assertTrue(body.contains("โอนออก"))
        assertTrue(body.contains("Alice"))
        assertTrue(body.contains("Oishi"))
        assertTrue(body.contains("\"memo, with comma\""))
        assertTrue(body.contains("slip"))
        assertTrue(body.contains("10.00"))
        assertTrue(body.contains("manual"))
    }

    @Test
    fun escapeQuotesAndNewlines() {
        assertEquals("\"a\"\"b\"", TransactionCsvExporter.escapeField("a\"b"))
        assertEquals("\"a\nb\"", TransactionCsvExporter.escapeField("a\nb"))
        assertEquals("plain", TransactionCsvExporter.escapeField("plain"))
    }
}
