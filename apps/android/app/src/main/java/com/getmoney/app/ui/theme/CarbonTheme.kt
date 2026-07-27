package com.getmoney.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val CarbonShapes = Shapes(
    extraSmall = RoundedCornerShape(0.dp),
    small = RoundedCornerShape(0.dp),
    medium = RoundedCornerShape(0.dp),
    large = RoundedCornerShape(0.dp),
    extraLarge = RoundedCornerShape(0.dp),
)

private val CarbonColorScheme = lightColorScheme(
    primary = IbmBlue,
    onPrimary = Canvas,
    primaryContainer = IbmBlue,
    onPrimaryContainer = Canvas,
    secondary = InkMuted,
    onSecondary = Canvas,
    background = Canvas,
    onBackground = Ink,
    surface = Canvas,
    onSurface = Ink,
    surfaceVariant = Surface1,
    onSurfaceVariant = InkMuted,
    outline = Hairline,
    error = ErrorRed,
    onError = Canvas,
)

@Composable
fun GetMoneyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CarbonColorScheme,
        typography = CarbonTypography,
        shapes = CarbonShapes,
        content = content,
    )
}

object CarbonButtonDefaults {
    @Composable
    fun primaryButtonColors() = ButtonDefaults.buttonColors(
        containerColor = IbmBlue,
        contentColor = Canvas,
    )

    @Composable
    fun primaryButtonElevation() = ButtonDefaults.buttonElevation(
        defaultElevation = 0.dp,
        pressedElevation = 0.dp,
        focusedElevation = 0.dp,
        hoveredElevation = 0.dp,
        disabledElevation = 0.dp,
    )
}
