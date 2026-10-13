package com.d4vram.pillmate2.ui.stats

import com.d4vram.pillmate2.ui.stats.viewmodel.Trend
import com.d4vram.pillmate2.ui.stats.viewmodel.trendOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendTest {

    @Test
    fun `trend is a difference in doses, not a percentage`() {
        assertEquals(Trend(diff = 9, days = 7), trendOf(current = 12, previous = 3, days = 7))
        assertEquals(Trend(diff = -1, days = 15), trendOf(current = 2, previous = 3, days = 15))
        assertEquals(Trend(diff = 0, days = 30), trendOf(current = 4, previous = 4, days = 30))
    }

    @Test
    fun `no previous doses means nothing to compare`() {
        assertNull(trendOf(current = 5, previous = 0, days = 7))
    }
}
