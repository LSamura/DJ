package com.djassistant.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DjDarkColorScheme = darkColorScheme(
    primary = DjPurple,
    onPrimary = BackgroundDark,
    primaryContainer = DjPurpleDark,
    secondary = DjTeal,
    background = BackgroundDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    error = DjRed
)

@Composable
fun DjTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DjDarkColorScheme,
        typography = DjTypography,
        content = content
    )
}
