package com.d4vram.pillmate2.ui.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class DailyRhythmTimelineTest {
    @get:Rule val compose = createComposeRule()

    private val day = LocalDate.of(2026, 9, 23)
    private fun entry(id: String, offset: Long, hour: Int) =
        HistoryEntry(id, "med", "Ejemplo", "", "", Color.Blue, "", timestamp = day.plusDays(offset).atTime(hour, 0), dose = "5mg")

    @Test fun singleDayDescribesRealEventsAndInvitesToTapADot() {
        compose.setContent { MaterialTheme { RhythmClock(listOf(entry("id", 0, 6))) } }
        compose.onNodeWithContentDescription("Reloj de 24 horas. 1 registros. 06:00 Ejemplo, 1 unidad").assertExists()
        compose.onNodeWithText("Toca un punto para ver la toma.").assertExists()
    }

    @Test fun severalDaysSwitchToHourlyBars() {
        compose.setContent { MaterialTheme { RhythmClock(listOf(entry("a", 0, 6), entry("b", -1, 6))) } }
        compose.onNodeWithText("Toca una barra para ver qué se tomó a esa hora.").assertExists()
    }

    @Test fun nonInteractiveClockShowsNoHint() {
        compose.setContent { MaterialTheme { RhythmClock(listOf(entry("id", 0, 6)), interactive = false) } }
        compose.onNodeWithText("Toca un punto para ver la toma.").assertDoesNotExist()
    }
}
