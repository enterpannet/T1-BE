package com.getmoney.app.ui.util

import java.math.BigDecimal
import java.math.RoundingMode

/** Format API money strings for display (exactly 2 decimal places). */
fun formatMoney(value: String): String {
    val normalized = value.trim().replace(",", "")
    if (normalized.isEmpty()) return value
    return try {
        normalized.toBigDecimal().setScale(2, RoundingMode.HALF_UP).toPlainString()
    } catch (_: NumberFormatException) {
        value
    }
}

fun formatPercentLabel(percent: String): String {
    return try {
        val plain = percent.trim().toBigDecimal()
            .setScale(2, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
        "$plain%"
    } catch (_: NumberFormatException) {
        "$percent%"
    }
}

fun formatPercentLabel(percent: Double): String {
    val plain = BigDecimal.valueOf(percent)
        .setScale(2, RoundingMode.HALF_UP)
        .stripTrailingZeros()
        .toPlainString()
    return "$plain%"
}
