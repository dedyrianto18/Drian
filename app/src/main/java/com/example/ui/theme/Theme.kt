package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val TerminalColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = ObsidianBg,
    primaryContainer = NeonCyanContainer,
    onPrimaryContainer = NeonCyan,
    secondary = BullGreen,
    onSecondary = ObsidianBg,
    secondaryContainer = BullGreenContainer,
    onSecondaryContainer = BullGreen,
    tertiary = ElectricIndigo,
    onTertiary = Color.White,
    background = ObsidianBg,
    onBackground = TextPrimary,
    surface = SurfaceDark,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = OutlineDark,
    outlineVariant = SurfaceHighlight,
    error = BearRed,
    onError = Color.White,
    errorContainer = BearRedContainer,
    onErrorContainer = BearRed
)

@Composable
fun CryptoQuantTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = TerminalColorScheme,
        typography = Typography,
        content = content
    )
}
