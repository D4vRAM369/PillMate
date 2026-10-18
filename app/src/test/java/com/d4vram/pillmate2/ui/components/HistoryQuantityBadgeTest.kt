package com.d4vram.pillmate2.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryQuantityBadgeTest {
    @Test
    fun dropsShowQuantityAndDropEmoji() {
        assertEquals("9 💧", historyQuantityBadgeText(9.0, isDrops = true))
    }

    @Test
    fun aSingleDropStillShowsItsAmount() {
        assertEquals("1 💧", historyQuantityBadgeText(1.0, isDrops = true))
    }

    @Test
    fun nonDropQuantityKeepsTheExistingMultiplierLabel() {
        assertEquals("x2", historyQuantityBadgeText(2.0, isDrops = false))
    }
    @Test
    fun halfUnitsUseTheLocalizedDecimalSeparator() {
        val previous = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("es-ES"))
            assertEquals("x0,5", historyQuantityBadgeText(0.5, isDrops = false))
            java.util.Locale.setDefault(java.util.Locale.ENGLISH)
            assertEquals("x0.5", historyQuantityBadgeText(0.5, isDrops = false))
        } finally {
            java.util.Locale.setDefault(previous)
        }
    }
}
