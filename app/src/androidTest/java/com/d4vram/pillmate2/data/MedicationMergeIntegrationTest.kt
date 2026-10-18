package com.d4vram.pillmate2.data

import android.graphics.Bitmap
import androidx.room.withTransaction
import androidx.test.platform.app.InstrumentationRegistry
import com.d4vram.pillmate2.data.local.HistoryEntryEntity
import com.d4vram.pillmate2.data.local.MedicationEntity
import com.d4vram.pillmate2.data.local.PillMateDatabase
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class MedicationMergeIntegrationTest {
    @Test fun serviceMergeAndCsvRoundTripPreserveHistoryAndBackup() = runBlocking {
        withFixture { database, repository ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val audioManager = AudioNoteManager(context)
            val audioBytes = "Synthetic audio fixture for backup recovery".toByteArray()
            val audioInput = File(context.cacheDir, "merge-test-audio.tmp").apply { writeBytes(audioBytes) }
            val audioId = audioManager.saveEncryptedAudio(audioInput)
            val photo = File(context.filesDir, "medication_photos/merge-test.png").apply {
                parentFile!!.mkdirs()
                outputStream().use { output ->
                    val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
                    try { check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
                    finally { bitmap.recycle() }
                }
            }
            val photoBytes = photo.readBytes()
            val source = database.medicationDao().getMedicationById("merge-source")!!
            database.medicationDao().upsertMedication(source.copy(
                photoPath = photo.absolutePath, usePhotoInstead = true,
                dailyGoal = 2, scheduleTime = "09:30", scheduleDays = "L,V",
                reminderEnabled = true, createdAt = 1_700_000_000_000L
            ))
            val sourceEntry = database.historyDao().getById("merge-source-entry")!!
            database.historyDao().insert(sourceEntry.copy(audioNoteUri = audioId))
            val originalMedications = repository.getMedicationsSnapshot()
            val originalHistory = repository.getHistorySnapshot().associateBy { it.id }
            var backup: File? = null
            try {
                backup = withTimeout(15_000) {
                    MedicationMerge(InstrumentationRegistry.getInstrumentation().targetContext, repository)
                        .merge("merge-source", "merge-destination", 0.5)
                }
                assertNull(database.medicationDao().getMedicationById("merge-source"))
                val destination = requireNotNull(database.medicationDao().getMedicationById("merge-destination"))
                assertEquals(5.0, destination.takenToday, 0.0)
                val moved = database.historyDao().getById("merge-source-entry")!!
                assertEquals("merge-destination", moved.medicationId)
                assertEquals("Example high", moved.medicationName)
                assertEquals("10 mg", moved.dose)
                assertEquals(0.5, moved.quantity, 0.0)
                assertEquals("2026-01-01T12:34:56", moved.timestampIso)
                assertEquals("Original note", moved.notes)
                assertEquals(audioId, moved.audioNoteUri)
                assertEquals(2, database.historyDao().getAllHistory().size)

                val mergedMedications = repository.getMedicationsSnapshot()
                val mergedHistory = repository.getHistorySnapshot().associateBy { it.id }
                assertEquals(originalHistory.keys, mergedHistory.keys)
                val exported = CsvUtils.buildCsv(mergedMedications, mergedHistory.values.toList())
                val imported = CsvUtils.parseCsv(exported)
                assertEquals(listOf("merge-destination"), imported.medications.map { it.id })
                assertEquals(5.0, imported.medications.single().takenToday, 0.0)
                val importedEntry = imported.history.single { it.id == "merge-source-entry" }
                assertEquals("merge-destination", importedEntry.medicationId)
                assertEquals("10 mg", importedEntry.dose)
                assertEquals(0.5, importedEntry.quantity, 0.0)
                assertEquals("Original note", importedEntry.notes)
                assertEquals("2026-01-01T12:34:56", importedEntry.timestamp.toString())

                // Exercise the same database replacement used by CSV import, not just its parser.
                repository.replaceAllData(imported.medications, imported.history)
                // This display-only timestamp is derived from history, not stored in CSV; history is compared below.
                assertEquals(
                    mergedMedications.map { it.copy(sortOrder = 0, lastTakenTimestamp = null) },
                    repository.getMedicationsSnapshot().map { it.copy(lastTakenTimestamp = null) }
                )
                assertEquals(mergedHistory, repository.getHistorySnapshot().associateBy { it.id })

                val original = ZipFile(backup).use { zip ->
                    CsvUtils.parseCsv(zip.getInputStream(zip.getEntry("backup.csv")).bufferedReader().use { it.readText() })
                }
                assertEquals(setOf("merge-source", "merge-destination"), original.medications.map { it.id }.toSet())
                assertEquals(4.0, original.medications.single { it.id == "merge-source" }.takenToday, 0.0)
                assertEquals(3.0, original.medications.single { it.id == "merge-destination" }.takenToday, 0.0)
                val originalEntry = original.history.single { it.id == "merge-source-entry" }
                assertEquals("merge-source", originalEntry.medicationId)
                assertEquals("5 mg", originalEntry.dose)
                assertEquals(1.0, originalEntry.quantity, 0.0)
                assertEquals("Original note", originalEntry.notes)

                // Remove live attachment files so recovery must read their bytes from the ZIP.
                assertTrue(photo.delete())
                audioManager.deleteAudio(audioId)
                assertNull(audioManager.getEncryptedFile(audioId))
                val restored = BackupZipManager.extractBackupZip(context, requireNotNull(backup))
                repository.replaceAllData(restored.medications, restored.history)
                assertEquals(
                    originalMedications.map { it.copy(lastTakenTimestamp = null) },
                    repository.getMedicationsSnapshot().map { it.copy(lastTakenTimestamp = null) }
                )
                assertEquals(originalHistory, repository.getHistorySnapshot().associateBy { it.id })
                assertArrayEquals(photoBytes, photo.readBytes())
                val recoveredAudio = AudioNoteManager(context).decryptAudioForPlayback(audioId)
                assertNotNull(recoveredAudio)
                assertArrayEquals(audioBytes, requireNotNull(recoveredAudio).readBytes())
            } finally {
                backup?.delete()
                photo.delete()
                audioManager.deleteAudio(audioId)
                audioInput.delete()
            }
        }
    }

    @Test fun legacyNumericDoseWithoutUnitKeepsQuarterQuantity() = runBlocking {
        withFixture { database, repository ->
            val source = database.medicationDao().getMedicationById("merge-source")!!
            database.medicationDao().upsertMedication(source.copy(dose = "2,5mg"))
            val entry = database.historyDao().getById("merge-source-entry")!!
            database.historyDao().insert(entry.copy(dose = "2,5"))
            var backup: File? = null
            try {
                backup = MedicationMerge(InstrumentationRegistry.getInstrumentation().targetContext, repository)
                    .merge("merge-source", "merge-destination", 0.25)
                val moved = database.historyDao().getById(entry.id)!!
                assertEquals(0.25, moved.quantity, 0.0)
                assertEquals("10 mg", moved.dose)
                assertEquals(entry.timestampIso, moved.timestampIso)
                assertEquals(entry.notes, moved.notes)
                assertEquals(2, database.historyDao().getAllHistory().size)
            } finally { backup?.delete() }
        }
    }

    @Test fun linkedSourceIsRejectedWithoutMutatingRecords() = runBlocking {
        withFixture { database, repository ->
            val source = database.medicationDao().getMedicationById("merge-source")!!
            database.medicationDao().upsertMedication(source.copy(parentMedicationId = "merge-destination"))
            val beforeMedications = database.medicationDao().getAllMedications()
            val beforeHistory = database.historyDao().getAllHistory()
            var rejected = false
            try {
                withTimeout(15_000) {
                    MedicationMerge(InstrumentationRegistry.getInstrumentation().targetContext, repository)
                        .merge("merge-source", "merge-destination", 0.5)
                }
            } catch (_: IllegalArgumentException) { rejected = true }
            assertTrue(rejected)
            assertEquals(beforeMedications, database.medicationDao().getAllMedications())
            assertEquals(beforeHistory, database.historyDao().getAllHistory())
        }
    }

    private suspend fun withFixture(block: suspend (PillMateDatabase, MedicationRepository) -> Unit) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // This test must never touch the personal or everyday Dev app database.
        check(context.packageName == "dev.d4vram.pillmate.uitest")
        val database = PillMateDatabase.getInstance(context)
        val medications = database.medicationDao()
        val history = database.historyDao()
        val savedMedications = medications.getAllMedications()
        val savedHistory = history.getAllHistory()
        val job = SupervisorJob()
        try {
            database.withTransaction {
                history.clear()
                medications.clear()
                medications.upsertMedications(listOf(
                    medication("merge-source", "Example low", "5 mg", 4.0, 0),
                    medication("merge-destination", "Example high", "10 mg", 3.0, 1)
                ))
                history.insertAll(listOf(entry("merge-source-entry", "merge-source", "Example low", "5 mg"),
                    entry("merge-destination-entry", "merge-destination", "Example high", "10 mg")))
            }
            val repository = MedicationRepository(database, medications, history, CoroutineScope(job + Dispatchers.IO))
            block(database, repository)
        } finally {
            job.cancelAndJoin()
            database.withTransaction {
                history.clear()
                medications.clear()
                medications.upsertMedications(savedMedications)
                history.insertAll(savedHistory)
            }
        }
    }

    private fun medication(id: String, name: String, dose: String, total: Double, order: Int) = MedicationEntity(
        id = id, name = name, dose = dose, type = "Pastilla", colorArgb = 0, emoji = "", takenToday = total,
        dailyGoal = 1, lastTaken = null, sortOrder = order
    )

    private fun entry(id: String, medicationId: String, name: String, dose: String) = HistoryEntryEntity(
        id = id, medicationId = medicationId, medicationName = name, time = "12:34", date = "01/01/2026",
        colorArgb = 0, emoji = "", quantity = 1.0, notes = "Original note", timestampIso = "2026-01-01T12:34:56", dose = dose
    )
}
