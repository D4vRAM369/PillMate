package com.d4vram.pillmate2.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CsvUtilsTest {
    @Test
    fun parseCsv_supportsHistoryRowsWithoutDoseColumn() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead
            med-1,Ibuprofeno,400mg,Pastilla,-1,💊,0,1,,0,,,

            [HISTORY]
            id,medicationId,medicationName,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
            hist-1,med-1,Ibuprofeno,08:00,06/06/2026,-1,💊,2,Con comida,2026-06-06T08:00:00,audio-1
        """.trimIndent()

        val parsed = CsvUtils.parseCsv(csv)

        assertEquals(1, parsed.history.size)
        val entry = parsed.history.first()
        assertNull(entry.dose)
        assertEquals("08:00", entry.time)
        assertEquals("06/06/2026", entry.date)
        assertEquals(2, entry.quantity)
        assertEquals("audio-1", entry.audioNoteUri)
    }

    @Test
    fun parseCsv_supportsHistoryRowsWithDoseColumn() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead

            [HISTORY]
            id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
            hist-1,med-1,Ibuprofeno,400mg,08:00,06/06/2026,-1,💊,2,Con comida,2026-06-06T08:00:00,audio-1
        """.trimIndent()

        val parsed = CsvUtils.parseCsv(csv)

        val entry = parsed.history.first()
        assertEquals("400mg", entry.dose)
        assertEquals("08:00", entry.time)
        assertEquals("audio-1", entry.audioNoteUri)
    }

    @Test
    fun parseCsv_newFormatParsesMedicationWithoutIsPrn() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead
            med-1,Omeprazol,20mg,Cápsula,-8355712,💊,2,1,08:15,0,08:00,L|M|X|J|V,,

            [HISTORY]
            id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
        """.trimIndent()

        val parsed = CsvUtils.parseCsv(csv)

        val med = parsed.medications.first()
        assertEquals("med-1", med.id)
        assertEquals("Omeprazol", med.name)
        assertEquals(2, med.takenToday)
        assertEquals("08:15", med.lastTaken)
        assertEquals(listOf("L", "M", "X", "J", "V"), med.scheduleDays)
    }

    @Test
    fun parseCsv_legacyIsPrnColumnIsIgnored() {
        val csv = """
            # PillMate CSV v1
            [MEDICATIONS]
            id,name,dose,type,colorArgb,emoji,totalTaken,dailyGoal,lastTaken,isPrn,sortOrder,scheduleTime,scheduleDays,photoPath,usePhotoInstead
            med-1,Omeprazol,20mg,Cápsula,-8355712,💊,2,1,08:15,false,0,08:00,L|M|X|J|V,,

            [HISTORY]
            id,medicationId,medicationName,dose,time,date,colorArgb,emoji,quantity,notes,timestampIso,audioNoteUri
        """.trimIndent()

        val parsed = CsvUtils.parseCsv(csv)

        val med = parsed.medications.first()
        assertEquals("med-1", med.id)
        assertEquals(2, med.takenToday)
        assertEquals(0, med.sortOrder)
        assertEquals(listOf("L", "M", "X", "J", "V"), med.scheduleDays)
    }
}
