package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = AccentCyan,
    onPrimary = Color(0xFF03101C),
    primaryContainer = Color(0xFF0F324D),
    onPrimaryContainer = Color(0xFFCBE6FF),
    secondary = AccentViolet,
    onSecondary = Color(0xFF1F1147),
    secondaryContainer = Color(0xFF2E2368),
    onSecondaryContainer = Color(0xFFEADBFF),
    tertiary = AccentEmerald,
    onTertiary = Color(0xFF003822),
    background = DarkNavyBackground,
    onBackground = TextPrimary,
    surface = DarkNavyCard,
    onSurface = TextPrimary,
    surfaceVariant = DarkNavyCardElevated,
    onSurfaceVariant = TextSecondary,
    outline = DarkNavyBorder,
    error = AccentRose,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Smart Home IoT dashboard designed with premium dark aesthetic
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
