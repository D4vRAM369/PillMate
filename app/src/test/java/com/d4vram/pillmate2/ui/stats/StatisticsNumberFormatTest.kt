package com.d4vram.pillmate2.ui.stats

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class StatisticsNumberFormatTest {
    @Test
    fun `uses decimal separator from the app locale`() {
        assertEquals("1.7", formatStatisticsNumber(1.7f, Locale.US))
        assertEquals("1,7", formatStatisticsNumber(1.7f, Locale.forLanguageTag("es-ES")))
    }

    @Test
    fun `keeps whole values free of a decimal part`() {
        assertEquals("12", formatStatisticsNumber(12f, Locale.US))
        assertEquals("12", formatStatisticsNumber(12f, Locale.forLanguageTag("es-ES")))
    }
}
