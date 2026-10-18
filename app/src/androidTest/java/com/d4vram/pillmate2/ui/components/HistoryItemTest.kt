package com.d4vram.pillmate2.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.R
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test

class HistoryItemTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun dropQuantityShowsAmountAndLocalizedAccessibleDescription() {
        compose.setContent {
            MaterialTheme {
                HistoryItem(historyEntry = entry(quantity = 9.0), isDrops = true, onShowStats = {})
            }
        }

        val expectedDescription = context.resources.getQuantityString(
            R.plurals.history_drops_accessibility,
            9,
            9
        )
        compose.onNodeWithText("9 💧").assertIsDisplayed()
        compose.onNodeWithContentDescription(expectedDescription).assertExists()
    }

    @Test
    fun oneDropStillShowsItsQuantity() {
        compose.setContent {
            MaterialTheme {
                HistoryItem(historyEntry = entry(quantity = 1.0), isDrops = true, onShowStats = {})
            }
        }

        compose.onNodeWithText("1 💧").assertIsDisplayed()
    }

    @Test
    fun nonDropQuantityKeepsTheMultiplierBadge() {
        compose.setContent {
            MaterialTheme {
                HistoryItem(historyEntry = entry(quantity = 2.0), onShowStats = {})
            }
        }

        compose.onNodeWithText("x2").assertIsDisplayed()
    }

    private fun entry(quantity: Double) = HistoryEntry(
        id = "history-$quantity",
        medicationId = "noctamid",
        medicationName = "Noctamid",
        time = "10:00",
        date = "2026-10-13",
        color = Color.Green,
        emoji = "💧",
        quantity = quantity,
        timestamp = LocalDateTime.of(2026, 10, 13, 10, 0)
    )
}
