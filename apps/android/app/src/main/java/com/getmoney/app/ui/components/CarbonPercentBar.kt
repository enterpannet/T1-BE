package com.getmoney.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.getmoney.app.ui.theme.ErrorRed
import com.getmoney.app.ui.theme.Hairline

@Composable
fun CarbonPercentBar(
    percent: Double,
    modifier: Modifier = Modifier,
) {
    val overBudget = percent > 100.0
    val fillFraction = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val fillColor = if (overBudget) ErrorRed else MaterialTheme.colorScheme.primary

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(Hairline),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fillFraction)
                .background(fillColor),
        )
    }
}

fun parsePercent(value: String): Double =
    value.toDoubleOrNull() ?: 0.0
