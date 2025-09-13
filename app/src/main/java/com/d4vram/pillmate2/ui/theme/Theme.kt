package com.d4vram.pillmate2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = SurfaceWhite,
    secondary = SecondaryPurple,
    onSecondary = SurfaceWhite,
    background = SurfaceLight,
    onBackground = OnSurfaceGray,
    surface = SurfaceWhite,
    onSurface = OnSurfaceGray,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = Color(0xFF64748B),
    primaryContainer = Color(0xFFE0F2FE),
    onPrimaryContainer = Color(0xFF0C4A6E),
    secondaryContainer = Color(0xFFF3E8FF),
    onSecondaryContainer = Color(0xFF581C87),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E)
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF003912),
    secondary = Color(0xFFA855F7),
    onSecondary = Color(0xFF2E1065),
    background = Color(0xFF0F172A),
    onBackground = Color(0xFFE2E8F0),
    surface = Color(0xFF1E293B),
    onSurface = Color(0xFFE2E8F0),
    surfaceVariant = Color(0xFF334155),
    onSurfaceVariant = Color(0xFF94A3B8),
    primaryContainer = Color(0xFF15803D),
    onPrimaryContainer = Color(0xFFDCFCE7),
    secondaryContainer = Color(0xFF7C3AED),
    onSecondaryContainer = Color(0xFFF3E8FF),
    tertiaryContainer = Color(0xFFD97706),
    onTertiaryContainer = Color(0xFFFEF3C7),
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF64748B)
)

@Composable
fun PillMateTheme(
    themeManager: ThemeManager? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isDarkMode by (themeManager?.isDarkMode?.collectAsState(initial = darkTheme) ?: androidx.compose.runtime.mutableStateOf(darkTheme))
    
    val colorScheme = if (isDarkMode) {
        DarkColorScheme
    } else {
        LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}