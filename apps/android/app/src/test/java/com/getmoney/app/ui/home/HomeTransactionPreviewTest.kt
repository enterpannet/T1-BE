package com.getmoney.app.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeTransactionPreviewTest {
    @Test
    fun capsAtFive() {
        assertEquals(5, takeLatestTransactions((1..10).toList()).size)
    }
}
