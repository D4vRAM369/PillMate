package com.d4vram.pillmate2.ui.stats

import com.d4vram.pillmate2.ui.stats.viewmodel.trendText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendPercentTest {

    @Test
    fun `trend is worded in doses, not percent`() {
        assertEquals("9 tomas más que los 7 días anteriores", trendText(current = 12, previous = 3, days = 7))
        assertEquals("1 toma menos que los 15 días anteriores", trendText(current = 2, previous = 3, days = 15))
        assertEquals("Igual que los 30 días anteriores", trendText(current = 4, previous = 4, days = 30))
        assertNull(trendText(current = 5, previous = 0, days = 7))
    }
}
