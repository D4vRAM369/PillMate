package com.d4vram.pillmate2.ui.settings

import java.util.Locale

/** Idiomas que el selector de Ajustes permite fijar para toda la aplicación. */
internal enum class AppLanguageOption(val localeTag: String) {
    SYSTEM(""),
    ENGLISH("en"),
    SPANISH("es")
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
