package com.d4vram.pillmate2.model

import com.d4vram.pillmate2.R
import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationTypeTest {

    private val english = mapOf(
        R.string.med_type_tablet to "Tablet",
        R.string.med_type_pill to "Pill",
        R.string.med_type_capsule to "Capsule",
        R.string.med_type_syrup to "Syrup",
        R.string.med_type_drops to "Drops"
    )
    private val spanish = mapOf(
        R.string.med_type_tablet to "Comprimido",
        R.string.med_type_pill to "Pastilla",
        R.string.med_type_capsule to "Cápsula",
        R.string.med_type_syrup to "Jarabe",
        R.string.med_type_drops to "Gotas"
    )
    private val en: (Int) -> String = { english.getValue(it) }
    private val es: (Int) -> String = { spanish.getValue(it) }

    @Test
    fun `in English a known type is shown translated`() {
        assertEquals("Tablet", MedicationType.label("Comprimido", en))
        assertEquals("Pill", MedicationType.label("Pastilla", en))
        assertEquals("Drops", MedicationType.label("gota", en))
    }

    @Test
    fun `a type the user made up is shown as written`() {
        assertEquals("Parche", MedicationType.label("Parche", en))
        assertEquals("Parche", MedicationType.label("Parche", es))
    }

    @Test
    fun `in Spanish nothing changes on screen`() {
        assertEquals("Gota", MedicationType.label("Gota", es))
        assertEquals("comprimido", MedicationType.label("comprimido", es))
    }

    @Test
    fun `what is typed in English is stored with the name backups already use`() {
        assertEquals("Comprimido", MedicationType.toStored("Tablet", null, en))
        assertEquals("Pastilla", MedicationType.toStored(" pill ", null, en))
        assertEquals("Gotas", MedicationType.toStored("Drops", "Pastilla", en))
    }

    @Test
    fun `editing in English without changing the type keeps the stored text`() {
        assertEquals("Gota", MedicationType.toStored("Drops", "Gota", en))
    }

    @Test
    fun `in Spanish and for made-up types the text is stored as typed`() {
        assertEquals("gotas", MedicationType.toStored("gotas", null, es))
        assertEquals("Patch", MedicationType.toStored("Patch", null, en))
    }
}
