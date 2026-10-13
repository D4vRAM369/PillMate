package com.d4vram.pillmate2.widget

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class QuickTakeWidgetLayoutTest {
    @Test
    fun `grid follows the widget proportions`() {
        assertEquals(2, quickTakeColumns(160f, 160f))
        assertEquals(2, quickTakeColumns(250f, 160f))
        assertEquals(4, quickTakeColumns(330f, 60f))
        assertEquals(1, quickTakeColumns(70f, 330f))
    }

    @Test
    fun `text stays readable on light and dark fills`() {
        assertEquals(Color.White, readableOn(Color(0xFF1A1A2E)))
        assertEquals(Color(0xFF1B1B1F), readableOn(Color(0xFFFFEEA8)))
    }
}
