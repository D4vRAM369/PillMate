package com.d4vram.pillmate2.ui.timeline

import androidx.compose.ui.graphics.Color
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Test

class DailyRhythmTest {
    @Test fun selectsCalendarDayAndSortsNewestFirstWithoutCountingUnitsAsEvents() {
        val date = LocalDate.of(2026, 9, 23)
        fun entry(id: String, offset: Long, hour: Int) = HistoryEntry(
            id, "med", "Med", "", "", Color.Green, "", 2,
            timestamp = date.plusDays(offset).atTime(hour, 0)
        )
        val entries = listOf(entry("yesterday", -1, 23), entry("midnight", 0, 0),
            entry("tomorrow", 1, 0), entry("evening", 0, 23))
        assertEquals(listOf("evening", "midnight"), dailyRhythmEntries(entries, date).map { it.id })
        assertEquals(emptyList<HistoryEntry>(), dailyRhythmEntries(entries, date.plusDays(2)))
    }
}
