package com.d4vram.pillmate2.ai

import androidx.compose.ui.graphics.Color
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.LocalDateTime
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartInsightEngineTest {

    // Sustituto de los recursos de Android: las frases en español, tal como están en values-es.
    private val spanish = mapOf(
        R.string.insight_peak_title to "Hora más activa",
        R.string.insight_share to "%1\$d de %2\$d tomas (%3\$d %%)",
        R.string.insight_gap_title to "Intervalo habitual entre tomas",
        R.string.insight_gap_detail to "Mediana de %1\$d intervalos · el más corto: %2\$s",
        R.string.insight_top_title to "Más registrado",
        R.string.insight_day_title to "Día con más tomas",
        R.string.insight_day_detail to "%1\$d tomas · media de %2\$s en los %3\$d días con registro"
    )
    private val es = Locale.forLanguageTag("es")
    private fun insights(entries: List<HistoryEntry>) =
        SmartInsightEngine.insights(entries, es) { id, args -> spanish.getValue(id).format(*args) }

    private fun entry(medication: String, day: Int, hour: Int, minute: Int = 0) = HistoryEntry(
        id = "$medication-$day-$hour-$minute", medicationId = medication, medicationName = medication,
        time = "", date = "", color = Color.Green, emoji = "",
        timestamp = LocalDateTime.of(2026, 10, day, hour, minute)
    )

    @Test
    fun `needs at least two takes to say anything`() {
        assertTrue(insights(emptyList()).isEmpty())
        assertTrue(insights(listOf(entry("A", 12, 8))).isEmpty())
    }

    @Test
    fun `describes peak hour, median gap, top medication and busiest day`() {
        // Día 11: 08:00. Día 12: 08:10, 08:40, 12:40, 22:40 → huecos 1450, 30, 240, 600 min.
        val entries = listOf(
            entry("A", 11, 8), entry("A", 12, 8, 10), entry("A", 12, 8, 40),
            entry("B", 12, 12, 40), entry("A", 12, 22, 40)
        )
        val insights = insights(entries.shuffled()).associateBy { it.title }

        assertEquals("08:00–09:00", insights.getValue("Hora más activa").value)
        assertEquals("3 de 5 tomas (60 %)", insights.getValue("Hora más activa").detail)
        // Mediana de [30, 240, 600, 1450] tomando el elemento central superior.
        assertEquals("10 h 0 min", insights.getValue("Intervalo habitual entre tomas").value)
        assertTrue(insights.getValue("Intervalo habitual entre tomas").detail.endsWith("el más corto: 30 min"))
        assertEquals("A", insights.getValue("Más registrado").value)
        assertEquals("4 de 5 tomas (80 %)", insights.getValue("Más registrado").detail)
        assertTrue(insights.getValue("Día con más tomas").value.endsWith("12/10"))
    }

    @Test
    fun `single medication on a single day skips the comparisons that need more`() {
        val titles = insights(listOf(entry("A", 12, 8), entry("A", 12, 9))).map { it.title }
        assertEquals(listOf("Hora más activa", "Intervalo habitual entre tomas"), titles)
    }

    @Test
    fun `formats durations by magnitude`() {
        assertEquals("45 min", SmartInsightEngine.formatMinutes(45))
        assertEquals("3 h 10 min", SmartInsightEngine.formatMinutes(190))
        assertEquals("2 d 1 h", SmartInsightEngine.formatMinutes(2 * 1440 + 60))
    }
}
