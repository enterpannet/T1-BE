package com.getmoney.app.autoscan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanProgressTest {
    @Test
    fun readingStatusLine() {
        val line = ScanProgress(
            phase = ScanProgress.Phase.Reading,
            current = 3,
            total = 12,
            skipped = 1,
        ).statusLine()
        assertEquals("กำลังอ่าน 3/12 · ข้าม 1", line)
    }

    @Test
    fun readingUsesOverallWhenSet() {
        val line = ScanProgress(
            phase = ScanProgress.Phase.Reading,
            current = 3,
            total = 100,
            overallCurrent = 203,
            overallTotal = 513,
            saved = 10,
        ).statusLine()
        assertEquals("กำลังอ่าน 203/513 · สำเร็จ 10", line)
    }

    @Test
    fun pausedStatusLine() {
        val line = ScanProgress(
            phase = ScanProgress.Phase.Paused,
            overallCurrent = 237,
            overallTotal = 513,
            saved = 40,
            skipped = 10,
            needReview = 5,
        ).statusLine()
        assertTrue(line.startsWith("พักไว้"))
        assertTrue(line.contains("237/513"))
        assertTrue(line.contains("สำเร็จ 40"))
        assertTrue(ScanProgress(phase = ScanProgress.Phase.Paused).isActive)
    }

    @Test
    fun savingStatusLineWithBreakdown() {
        val line = ScanProgress(
            phase = ScanProgress.Phase.Saving,
            current = 2,
            total = 5,
            saved = 1,
            skipped = 0,
            needReview = 1,
            failed = 0,
        ).statusLine()
        assertEquals("กำลังบันทึก 2/5 · สำเร็จ 1 · ต้องตรวจ 1", line)
    }

    @Test
    fun doneStatusLine() {
        val line = ScanProgress(
            phase = ScanProgress.Phase.Done,
            current = 10,
            total = 10,
            saved = 4,
            skipped = 3,
            needReview = 2,
            failed = 1,
        ).statusLine()
        assertTrue(line.contains("สำเร็จ 4"))
        assertTrue(line.contains("ข้าม 3"))
        assertTrue(line.contains("ต้องตรวจ 2"))
        assertTrue(line.contains("error 1"))
        assertFalse(ScanProgress().isActive)
        assertTrue(
            ScanProgress(phase = ScanProgress.Phase.Reading, current = 1, total = 2).isActive,
        )
    }
}
