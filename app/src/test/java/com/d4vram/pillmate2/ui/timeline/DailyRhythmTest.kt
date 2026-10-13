package com.d4vram.pillmate2.ui.timeline

import androidx.compose.ui.graphics.Color
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyRhythmTest {
    private val date = LocalDate.of(2026, 9, 23)

    private fun entry(id: String, offset: Long, hour: Int, minute: Int = 0, medication: String = "med") = HistoryEntry(
        id, medication, medication.uppercase(), "", "", Color.Green, "", 2,
        timestamp = date.plusDays(offset).atTime(hour, minute), dose = "5mg"
    )

    @Test fun selectsCalendarDayAndSortsNewestFirstWithoutCountingUnitsAsEvents() {
        val entries = listOf(entry("yesterday", -1, 23), entry("midnight", 0, 0),
            entry("tomorrow", 1, 0), entry("evening", 0, 23))
        assertEquals(listOf("evening", "midnight"), dailyRhythmEntries(entries, date).map { it.id })
        assertEquals(emptyList<HistoryEntry>(), dailyRhythmEntries(entries, date.plusDays(2)))
    }

    @Test fun dotsKeepExactMinuteAndStackInwardOnlyWithinTheSameTwentyMinutes() {
        val dots = clockDots(listOf(entry("c", 0, 0, 25), entry("a", 0, 0, 5), entry("b", 0, 0, 15)))
        assertEquals(listOf("a" to 0, "b" to 1, "c" to 0), dots.map { it.entry.id to it.ring })
        assertEquals(listOf(5, 15, 25), dots.map { it.minute })
    }

    @Test fun hourSlicesAlwaysCoverTwentyFourHoursAndCountTakesPerMedication() {
        val slices = hourSlices(listOf(
            entry("1", 0, 3), entry("2", -1, 3, 30), entry("3", -2, 3, 59, medication = "other"), entry("4", 0, 22)
        ))
        assertEquals((0..23).toList(), slices.map { it.hour })
        assertEquals(listOf(SlicePart("MED · 5mg", Color.Green, 2), SlicePart("OTHER · 5mg", Color.Green, 1)), slices[3].parts)
        assertEquals(1, slices[22].total)
        assertEquals(4, slices.sumOf { it.total })
    }

    @Test fun tapAngleMapsToHourWithMidnightOnTop() {
        fun hour(dx: Float, dy: Float) = (dayFraction(dx, dy) * 24).toInt()
        assertEquals(0, hour(1f, -100f))   // justo a la derecha de las 12 en punto
        assertEquals(6, hour(100f, 1f))    // derecha
        assertEquals(12, hour(-1f, 100f))  // abajo
        assertEquals(18, hour(-100f, -1f)) // izquierda
        assertEquals(23, hour(-1f, -100f)) // justo a la izquierda de arriba
    }

    @Test fun legendListsEachMedicationOnceMostTakenFirst() {
        val legend = clockLegend(listOf(
            entry("1", 0, 3, medication = "other"), entry("2", -1, 8), entry("3", -2, 21), entry("4", 0, 22)
        ))
        assertEquals(listOf(SlicePart("MED · 5mg", Color.Green, 3), SlicePart("OTHER · 5mg", Color.Green, 1)), legend)
    }
}
