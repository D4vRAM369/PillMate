package com.d4vram.pillmate2.data

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.data.local.MedicationEntity
import com.d4vram.pillmate2.data.local.PillMateDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class MedicationUndoTest {
    @Test fun undoRemovesOnlyItsOwnEntryAndRestoresMetadata() = runBlocking {
        val database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            PillMateDatabase::class.java
        ).build()
        val repositoryJob = SupervisorJob()
        val repositoryScope = CoroutineScope(repositoryJob + Dispatchers.IO)
        try {
            val medications = database.medicationDao()
            val history = database.historyDao()
            // Seed before constructing the repository, so sample initialization cannot run.
            medications.upsertMedication(MedicationEntity(
                id = "test", name = "Test", dose = "10 mg", type = "Pastilla",
                colorArgb = 0, emoji = "", takenToday = 0.0, dailyGoal = 0,
                lastTaken = null, sortOrder = 0
            ))
            val repository = MedicationRepository(database, medications, history, repositoryScope)
            val time = LocalDate.now().atTime(10, 30)
            val firstId = requireNotNull(repository.recordMedicationTaken("test", 2.0, time))
            val secondId = requireNotNull(repository.recordMedicationTaken("test", 2.0, time))
            assertNotEquals(firstId, secondId)
            assertEquals(4.0, medications.getMedicationById("test")!!.takenToday, 0.0)

            assertTrue(repository.undoMedicationTaken(firstId))
            assertEquals(listOf(secondId), history.getAllHistory().map { it.id })
            assertEquals(2.0, medications.getMedicationById("test")!!.takenToday, 0.0)
            assertEquals("Hoy 10:30", medications.getMedicationById("test")!!.lastTaken)
            assertFalse(repository.undoMedicationTaken(firstId))
            assertEquals(2.0, medications.getMedicationById("test")!!.takenToday, 0.0)

            assertTrue(repository.undoMedicationTaken(secondId))
            assertTrue(history.getAllHistory().isEmpty())
            assertEquals(0.0, medications.getMedicationById("test")!!.takenToday, 0.0)
            assertNull(medications.getMedicationById("test")!!.lastTaken)
            val restored = withTimeout(5_000) {
                repository.medications.first { list ->
                    list.singleOrNull()?.let { it.takenToday == 0.0 && it.lastTaken == null } == true
                }.single()
            }
            assertNull(restored.lastTakenTimestamp)
            assertNull(repository.recordMedicationTaken("missing"))
            assertTrue(history.getAllHistory().isEmpty())

            // Insertion order differs from event time: undoing an older record must
            // retain the actual latest dose, not whichever row was inserted last.
            val currentId = requireNotNull(repository.recordMedicationTaken("test", 1.0, time))
            val olderId = requireNotNull(repository.recordMedicationTaken("test", 3.0, time.minusDays(2)))
            assertTrue(repository.undoMedicationTaken(olderId))
            assertEquals(listOf(currentId), history.getAllHistory().map { it.id })
            assertEquals(1.0, medications.getMedicationById("test")!!.takenToday, 0.0)
            assertEquals("Hoy 10:30", medications.getMedicationById("test")!!.lastTaken)
            withTimeout(5_000) {
                repository.medications.first { list ->
                    list.singleOrNull()?.let {
                        it.takenToday == 1.0 && it.lastTakenTimestamp ==
                            time.atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                    } == true
                }
            }

            // Removing the medication while its snackbar is visible must not stop
            // undo from deleting the exact history entry or resurrect the medicine.
            val orphanId = requireNotNull(repository.recordMedicationTaken("test", 1.0, time))
            medications.deleteById("test")
            assertTrue(repository.undoMedicationTaken(orphanId))
            assertEquals(listOf(currentId), history.getAllHistory().map { it.id })
            assertNull(medications.getMedicationById("test"))
            assertNull(repository.recordMedicationTaken("test"))
            assertTrue(repository.undoMedicationTaken(currentId))
            assertTrue(history.getAllHistory().isEmpty())
            assertFalse(repository.undoMedicationTaken(orphanId))
        } finally {
            repositoryJob.cancelAndJoin()
            database.close()
        }
    }
}
