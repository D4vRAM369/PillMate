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

    @Test fun clockIsOptionalAndDescribesRealEvents() {
        val day = LocalDate.of(2026, 9, 23)
        val entry = HistoryEntry("id", "med", "Ejemplo", "", "", Color.Blue, "", timestamp = day.atTime(6, 0))
        compose.setContent { MaterialTheme { DailyRhythmTimeline(listOf(entry), day) } }
        compose.onNodeWithText("1 registros · 23/9").assertExists()
        compose.onNodeWithText("24 horas").assertDoesNotExist()
        compose.onNodeWithText("Ver reloj de 24 horas").performClick()
        compose.onNodeWithContentDescription("Reloj de 24 horas. 1 registros. 06:00 Ejemplo, 1 unidades").assertExists()
        compose.onNodeWithText("Ocultar reloj").performClick()
        compose.onNodeWithText("24 horas").assertDoesNotExist()
    }
}
