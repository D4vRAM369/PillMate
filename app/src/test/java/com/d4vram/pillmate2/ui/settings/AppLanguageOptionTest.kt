package com.d4vram.pillmate2.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppLanguageOptionTest {
    @Test
    fun localeTagsMapToSupportedLanguageOptions() {
        assertEquals(AppLanguageOption.SYSTEM, appLanguageOptionForTag(""))
        assertEquals(AppLanguageOption.ENGLISH, appLanguageOptionForTag("en"))
        assertEquals(AppLanguageOption.ENGLISH, appLanguageOptionForTag("en-US"))
        assertEquals(AppLanguageOption.SPANISH, appLanguageOptionForTag("es"))
        assertEquals(AppLanguageOption.SPANISH, appLanguageOptionForTag("es-MX"))
        assertEquals(AppLanguageOption.SYSTEM, appLanguageOptionForTag("fr-FR"))
    }
}
