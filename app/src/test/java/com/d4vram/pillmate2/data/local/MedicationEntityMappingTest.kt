package com.d4vram.pillmate2.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationEntityMappingTest {

    private val entity = MedicationEntity(
        id = "m1",
        name = "Ejemplo",
        dose = "10 mg",
        type = "Pastilla",
        colorArgb = 0xFF10B981.toInt(),
        emoji = "💊",
        takenToday = 0.5,
        dailyGoal = 1,
        lastTaken = "2026-01-02T08:30:00",
        sortOrder = 2,
        scheduleTime = "08:30",
        scheduleDays = "L, X,,V",
        reminderEnabled = true,
        parentMedicationId = "p1",
        archived = true
    )

    // Los recordatorios usaban una copia a mano que perdía estos campos
    @Test
    fun `keeps every field the reminder copy used to drop`() {
        val medication = entity.toDomain()

        assertEquals(0.5, medication.takenToday, 0.0)
        assertEquals("p1", medication.parentMedicationId)
        assertTrue(medication.archived)
        assertEquals("2026-01-02T08:30:00", medication.lastTaken)
    }

    @Test
    fun `cleans schedule days`() {
        assertEquals(listOf("L", "X", "V"), entity.toDomain().scheduleDays)
    }
}
