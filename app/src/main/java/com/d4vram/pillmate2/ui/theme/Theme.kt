package com.d4vram.pillmate2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = PrimaryGreen,
    onPrimary = SurfaceWhite,
    secondary = SecondaryPurple,
    onSecondary = SurfaceWhite,
    background = SurfaceLight,
    onBackground = OnSurfaceGray,
    surface = SurfaceWhite,
    onSurface = OnSurfaceGray
)

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryGreen,
    onPrimary = SurfaceWhite,
    secondary = SecondaryPurple,
    onSecondary = SurfaceWhite
)

@Composable
fun PillMateTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) {
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