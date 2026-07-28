package com.getmoney.app.ocr

import com.getmoney.app.data.identity.MyIdentity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MyIdentityDirectionTest {
    private val me = MyIdentity(
        nameAliases = listOf("นาย เกียรติศักดิ์ พ"),
        accountHints = listOf("xxx-x-x3523-x"),
    )

    @Test
    fun fromIsMeMeansOut() {
        val dir = MyIdentityDirection.infer(
            fromName = "นาย เกียรติศักดิ์ พ · ธ.กสิกรไทย · xxx-x-x3523-x",
            toName = "น.ส. น้ำทิพย์ ตาทอง · xxx-x-x5079-x",
            identity = me,
        )
        assertEquals(TransferDirection.OUT, dir)
    }

    @Test
    fun toIsMeMeansIn() {
        val dir = MyIdentityDirection.infer(
            fromName = "น.ส. น้ำทิพย์ ตาทอง · xxx-x-x5079-x",
            toName = "นาย เกียรติศักดิ์ พ · xxx-x-x3523-x",
            identity = me,
        )
        assertEquals(TransferDirection.IN, dir)
    }

    @Test
    fun accountLast4Matches() {
        assertTrue(
            MyIdentityDirection.matchesMe(
                "นาย ทดสอบ · Xxx-x-x3523-x",
                MyIdentity(accountHints = listOf("3523")),
            ),
        )
    }

    @Test
    fun unclearKeepsNull() {
        assertNull(
            MyIdentityDirection.infer(
                fromName = "บริษัท อื่น",
                toName = "STORYLOG",
                identity = me,
            ),
        )
    }

    @Test
    fun applyOverridesDirectionAndNote() {
        val draft = SlipDraft(
            amount = "200.00",
            direction = TransferDirection.OUT,
            fromName = "น.ส. คนอื่น",
            toName = "นาย เกียรติศักดิ์ พ · xxx-x-x3523-x",
            note = "โอนออก",
        )
        val updated = MyIdentityDirection.applyToDraft(draft, me)
        assertEquals(TransferDirection.IN, updated.direction)
        assertTrue(updated.note!!.startsWith("รับเข้า"))
    }
}
