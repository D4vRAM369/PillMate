package com.d4vram.pillmate2.ui.timeline

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class DailyRhythmTimelineTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val day = LocalDate.of(2026, 9, 23)
    private fun entry(id: String, offset: Long, hour: Int) =
        HistoryEntry(id, "med", "Ejemplo", "", "", Color.Blue, "", timestamp = day.plusDays(offset).atTime(hour, 0), dose = "5mg")

    @Test fun singleDayDescribesRealEventsAndInvitesToTapADot() {
        compose.setContent { MaterialTheme { RhythmClock(listOf(entry("id", 0, 6))) } }
        val dose = context.resources.getQuantityString(R.plurals.clock_units, 1, 1)
        val summary = "06:00 Ejemplo, $dose"
        val description = context.resources.getQuantityString(R.plurals.clock_description, 1, 1, summary)
        compose.onNodeWithContentDescription(description).assertExists()
        compose.onNodeWithText(context.getString(R.string.clock_hint_dot)).assertExists()
    }

    @Test fun severalDaysSwitchToHourlyBars() {
        compose.setContent { MaterialTheme { RhythmClock(listOf(entry("a", 0, 6), entry("b", -1, 6))) } }
        compose.onNodeWithText(context.getString(R.string.clock_hint_bar)).assertExists()
    }

    @Test fun nonInteractiveClockShowsNoHint() {
        compose.setContent { MaterialTheme { RhythmClock(listOf(entry("id", 0, 6)), interactive = false) } }
        compose.onNodeWithText(context.getString(R.string.clock_hint_dot)).assertDoesNotExist()
    }
}
