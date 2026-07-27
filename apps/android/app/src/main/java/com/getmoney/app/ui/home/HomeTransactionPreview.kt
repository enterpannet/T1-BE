package com.getmoney.app.ui.home

fun <T> takeLatestTransactions(items: List<T>, limit: Int = 5): List<T> = items.take(limit)
