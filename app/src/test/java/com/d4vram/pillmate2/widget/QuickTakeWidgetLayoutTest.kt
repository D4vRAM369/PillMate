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
        // Casos reales: preset Normal (13 sp), letra del sistema al 130 %.
        // Rejilla 2x2, celda de 87 dp: los nombres largos bajan un punto, los cortos no
        assertEquals(12, fittedNameSp("Melatonina", 13, 87f, 1, 1.3f))
        assertEquals(12, fittedNameSp("Omeprazol", 13, 87f, 1, 1.3f))
        assertEquals(13, fittedNameSp("Aspirina", 13, 87f, 1, 1.3f))
        // Rejilla 4x1, celda de 75 dp: menos hueco, baja más
        assertEquals(10, fittedNameSp("Omeprazol", 13, 75f, 1, 1.3f))
        // Dos líneas: manda la palabra más ancha, no el nombre entero
        assertEquals(13, fittedNameSp("Vitamina D", 13, 75f, 2, 1.3f))
        assertEquals(10, fittedNameSp("Vitamina D", 13, 75f, 1, 1.3f))
        // Mismas letras, distinto ancho: las m pesan más que las i
        assertEquals(12, fittedNameSp("illilli", 12, 30f, 1, 1f))
        assertEquals(8, fittedNameSp("mammamm", 12, 30f, 1, 1f))
        // Nunca por debajo del 70 %
        assertEquals(8, fittedNameSp("Paracetamol/Codeína", 12, 40f, 1, 1f))
    }
}
