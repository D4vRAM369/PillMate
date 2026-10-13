package com.d4vram.pillmate2.ui.settings

import androidx.annotation.StringRes
import com.d4vram.pillmate2.R
import java.util.Locale

/** Idiomas que el selector de Ajustes permite fijar para toda la aplicación. */
internal enum class AppLanguageOption(
    val localeTag: String,
    @StringRes val labelRes: Int
) {
    SYSTEM("", R.string.settings_language_device),
    ENGLISH("en", R.string.settings_language_english),
    SPANISH("es", R.string.settings_language_spanish)
}

/**
 * Reconoce el idioma base incluso cuando Android devuelve un tag regional como `es-MX`.
 * Los tags que no ofrece la app se presentan como idioma del dispositivo.
 */
internal fun appLanguageOptionForTag(tag: String): AppLanguageOption {
    val language = tag.substringBefore('-').lowercase(Locale.ROOT)
    return AppLanguageOption.entries.firstOrNull {
        it != AppLanguageOption.SYSTEM && it.localeTag == language
    } ?: AppLanguageOption.SYSTEM
}
