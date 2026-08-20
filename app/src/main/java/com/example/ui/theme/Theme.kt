package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = GVONEPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF312E81),
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = GVONESecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF083344),
    onSecondaryContainer = Color(0xFFCFFAFE),
    tertiary = GVONETertiary,
    background = GVONEBackgroundDark,
    onBackground = GVONETextPrimary,
    surface = GVONESurfaceDark,
    onSurface = GVONETextPrimary,
    surfaceVariant = GVONESurfaceVariantDark,
    onSurfaceVariant = GVONETextSecondary,
    outline = GVONEBorder
)

private val LightColorScheme = lightColorScheme(
    primary = GVONEPrimary,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEEF2FF),
    secondary = GVONESecondary,
    background = Color(0xFFF8FAFC),
    onBackground = Color(0xFF0F172A),
    surface = Color.White,
    onSurface = Color(0xFF0F172A),
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun GVONEBrowserTheme(
    darkTheme: Boolean = true, // Default to GVONE signature dark mode
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
