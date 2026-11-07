package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val HighContrastParkingColorScheme = darkColorScheme(
    primary = SafetyAmber,
    onPrimary = Color(0xFF0A0D10),
    primaryContainer = SafetyAmberDim,
    onPrimaryContainer = SafetyAmber,
    secondary = CashEmerald,
    onSecondary = Color(0xFF041A0E),
    secondaryContainer = CashEmeraldDim,
    onSecondaryContainer = CashEmerald,
    tertiary = UpiCyan,
    onTertiary = Color(0xFF001E2E),
    tertiaryContainer = UpiCyanDim,
    onTertiaryContainer = UpiCyan,
    error = DebtCrimson,
    onError = Color.White,
    errorContainer = DebtCrimsonDim,
    onErrorContainer = Color(0xFFFFB4A1),
    background = AsphaltObsidian,
    onBackground = CrispWhite,
    surface = ElevatedSlate,
    onSurface = CrispWhite,
    surfaceVariant = Color(0xFF1E2834),
    onSurfaceVariant = MutedSteel,
    outline = CardSlateBorder
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = HighContrastParkingColorScheme,
        typography = Typography,
        content = content
    )
}
