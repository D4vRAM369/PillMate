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

    @Test
    fun `long names shrink instead of breaking a word`() {
        // Cabe: no se toca
        assertEquals(12, fittedNameSp("Aspirina", 12, 88f, 2, 1f))
        // No cabe: baja lo justo
        assertEquals(11, fittedNameSp("Melatonina", 12, 60f, 2, 1f))
        // Caso real: celda de 87 dp, preset Normal (13 sp) y letra del sistema al 130 %
        assertEquals(12, fittedNameSp("Melatonina", 13, 87f, 2, 1.3f))
        assertEquals(13, fittedNameSp("Omeprazol", 13, 87f, 2, 1.3f))
        // Dos líneas: manda la palabra más larga, no el nombre entero
        assertEquals(12, fittedNameSp("Vitamina D", 12, 55f, 2, 1f))
        assertEquals(10, fittedNameSp("Vitamina D", 12, 55f, 1, 1f))
        // Nunca por debajo del 70 %
        assertEquals(8, fittedNameSp("Paracetamol/Codeína", 12, 40f, 1, 1f))
    }
}
