package com.d4vram.pillmate2.ui.stats

import com.d4vram.pillmate2.ui.stats.viewmodel.trendPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrendPercentTest {

    @Test
    fun `compares against the previous period`() {
        assertEquals(50, trendPercent(current = 15, previous = 10))
        assertEquals(-50, trendPercent(current = 5, previous = 10))
        assertEquals(0, trendPercent(current = 10, previous = 10))
        assertEquals(-100, trendPercent(current = 0, previous = 4))
    }

    @Test
    fun `no previous doses means nothing to compare`() {
        assertNull(trendPercent(current = 7, previous = 0))
    }
}
