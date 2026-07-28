package com.getmoney.app.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlipNoteCodecTest {
    @Test
    fun composeAndParseRoundTrip() {
        val composed = SlipNoteCodec.compose(
            direction = TransferDirection.OUT,
            fromName = "สมชาย",
            toName = "สมหญิง",
            memo = "ค่าข้าว\nเพิ่มเติม",
        )
        val parts = SlipNoteCodec.parse(composed)
        assertEquals(TransferDirection.OUT, parts.direction)
        assertEquals("สมชาย", parts.fromName)
        assertEquals("สมหญิง", parts.toName)
        assertEquals("ค่าข้าว\nเพิ่มเติม", parts.memo)
    }

    @Test
    fun legacyFreeTextIsMemoOnly() {
        val parts = SlipNoteCodec.parse("QR: Coffee Shop")
        assertNull(parts.direction)
        assertNull(parts.fromName)
        assertNull(parts.toName)
        assertEquals("QR: Coffee Shop", parts.memo)
    }

    @Test
    fun composeNullWhenEmpty() {
        assertNull(SlipNoteCodec.compose(null, null, null, "  "))
    }

    @Test
    fun displayTransferLine() {
        val line = SlipNoteCodec.displayTransferLine(
            SlipNoteParts(
                direction = TransferDirection.IN,
                fromName = "A",
                toName = "B",
            ),
        )
        assertEquals("รับเข้า · จาก: A → ถึง: B", line)
    }
}
