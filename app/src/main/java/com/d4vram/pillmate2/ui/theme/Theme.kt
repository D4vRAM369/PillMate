package com.d4vram.pillmate2.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

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
    // Lila con cuerpo: sobre blanco, el F3E8FF anterior apenas distinguía un chip seleccionado
    secondaryContainer = Color(0xFFDDD6FE),
    onSecondaryContainer = Color(0xFF581C87),
    tertiaryContainer = Color(0xFFFEF3C7),
    onTertiaryContainer = Color(0xFF92400E),
    // Sin definir, Material pone un gris casi invisible sobre blanco (aro del reloj, divisores)
    outlineVariant = Color(0xFF94A3B8)
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

private val MaterialYouLightColorScheme = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    background = Color(0xFFFFFBFE),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFFFBFE),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E)
)

private val MaterialYouDarkColorScheme = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99)
)

private val ClassicShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(28.dp)
)

private val MaterialYouShapes = Shapes(
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(32.dp)
)

private val MaterialYouTypography = androidx.compose.material3.Typography()

@Composable
fun PillMateTheme(
    themeManager: ThemeManager? = null,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val isDarkMode by (themeManager?.isDarkMode?.collectAsState(initial = darkTheme)
        ?: androidx.compose.runtime.remember { mutableStateOf(darkTheme) })
    val uiStyle by (themeManager?.uiStyle?.collectAsState(initial = AppUiStyle.Default)
        ?: androidx.compose.runtime.remember { mutableStateOf(AppUiStyle.Default) })

    val appDesign by (themeManager?.appDesign?.collectAsState(initial = AppDesign.Astra)
        ?: androidx.compose.runtime.remember { mutableStateOf(AppDesign.Astra) })

    val context = LocalContext.current
    val useDynamicColors = uiStyle == AppUiStyle.MaterialYou && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val colorScheme = when {
        useDynamicColors && isDarkMode -> dynamicDarkColorScheme(context)
        useDynamicColors && !isDarkMode -> dynamicLightColorScheme(context)
        uiStyle == AppUiStyle.MaterialYou && isDarkMode -> MaterialYouDarkColorScheme
        uiStyle == AppUiStyle.MaterialYou -> MaterialYouLightColorScheme
        isDarkMode -> DarkColorScheme
        else -> LightColorScheme
    }

    val shapes = if (uiStyle == AppUiStyle.MaterialYou) MaterialYouShapes else ClassicShapes
    val typography = if (uiStyle == AppUiStyle.MaterialYou) MaterialYouTypography else Typography

    CompositionLocalProvider(LocalUiStyle provides uiStyle, LocalAppDesign provides appDesign) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = shapes,
            content = content
        )
    }
}

val LocalUiStyle = staticCompositionLocalOf { AppUiStyle.Default }
