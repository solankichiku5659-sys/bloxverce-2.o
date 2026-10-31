package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BloxVerseDarkColorScheme = darkColorScheme(
    primary = NeonPurple,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF2E1065),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = NeonCyan,
    onSecondary = Color(0xFF042F2E),
    secondaryContainer = Color(0xFF164E63),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = NeonPink,
    onTertiary = Color.White,
    background = BloxBackground,
    onBackground = TextPrimary,
    surface = BloxSurface,
    onSurface = TextPrimary,
    surfaceVariant = BloxSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = BloxCardBorder,
    error = NeonRed,
    onError = Color.White
)

@Composable
fun BloxVerseTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = BloxVerseDarkColorScheme,
        typography = Typography,
        content = content
    )
}
