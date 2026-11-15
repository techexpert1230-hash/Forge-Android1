package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val ForgeDarkColorScheme = darkColorScheme(
    primary = ForgeNeonLime,
    onPrimary = ForgeBackground,
    secondary = ForgeNeonLimeSoft,
    onSecondary = ForgeBackground,
    background = ForgeBackground,
    onBackground = ForgeTextPrimary,
    surface = ForgeSurface,
    onSurface = ForgeTextPrimary,
    surfaceVariant = ForgeSurface2,
    onSurfaceVariant = ForgeTextSecondary,
    outline = ForgeBorder,
    error = ForgeError,
    onError = ForgeTextPrimary
)

@Composable
fun ForgeTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ForgeDarkColorScheme,
        typography = Typography,
        content = content
    )
}
