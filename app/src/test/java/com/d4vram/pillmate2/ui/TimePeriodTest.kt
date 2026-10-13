package com.d4vram.pillmate2.ui

import com.d4vram.pillmate2.ui.screens.TimePeriod
import com.d4vram.pillmate2.ui.screens.includes
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimePeriodTest {
    private val today = LocalDate.of(2026, 10, 13)

    private fun daysIn(period: TimePeriod) =
        (0L..60L).count { period.includes(today.minusDays(it), today) }

    @Test
    fun `windows hold exactly the days their label says`() {
        assertEquals(1, daysIn(TimePeriod.TODAY))
        assertEquals(1, daysIn(TimePeriod.YESTERDAY))
        assertEquals(7, daysIn(TimePeriod.WEEK))
        assertEquals(30, daysIn(TimePeriod.MONTH))
        assertEquals(61, daysIn(TimePeriod.ALL))
    }

    @Test
    fun `yesterday is not today and tomorrow is in no window`() {
        assertTrue(TimePeriod.YESTERDAY.includes(today.minusDays(1), today))
        assertFalse(TimePeriod.YESTERDAY.includes(today, today))
        assertFalse(TimePeriod.WEEK.includes(today.plusDays(1), today))
    }
}
