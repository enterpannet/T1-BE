package com.getmoney.app.autoscan

import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSaveSummaryTest {
    @Test
    fun snackbarIncludesFolderTotalAndBreakdown() {
        val message = AutoSaveSummary(
            folderFileCount = 45,
            saved = 3,
            duplicates = 2,
            needReview = 1,
        ).snackbarMessage()
        assertEquals("ในโฟลเดอร์มี 45 ไฟล์ · สำเร็จ 3 · ข้าม 2 · ต้องตรวจ 1", message)
    }

    @Test
    fun snackbarFolderOnlyWhenNoSlips() {
        val message = AutoSaveSummary(folderFileCount = 12).snackbarMessage()
        assertEquals("ในโฟลเดอร์มี 12 ไฟล์ · ไม่พบสลิปใหม่", message)
    }

    @Test
    fun snackbarWithoutFolderFallsBack() {
        val message = AutoSaveSummary().snackbarMessage()
        assertEquals("ไม่พบสลิปใหม่", message)
    }
}
