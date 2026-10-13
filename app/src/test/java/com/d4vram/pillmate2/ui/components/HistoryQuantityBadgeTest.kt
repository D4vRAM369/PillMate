package com.d4vram.pillmate2.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class HistoryQuantityBadgeTest {
    @Test
    fun dropsShowQuantityAndDropEmoji() {
        assertEquals("9 💧", historyQuantityBadgeText(9, isDrops = true))
    }

    @Test
    fun aSingleDropStillShowsItsAmount() {
        assertEquals("1 💧", historyQuantityBadgeText(1, isDrops = true))
    }

    @Test
    fun nonDropQuantityKeepsTheExistingMultiplierLabel() {
        assertEquals("x2", historyQuantityBadgeText(2, isDrops = false))
    }
}
