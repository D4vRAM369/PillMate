package com.d4vram.pillmate2.data

import android.content.Context
import androidx.room.withTransaction
import com.d4vram.pillmate2.data.local.HistoryEntryEntity
import com.d4vram.pillmate2.data.local.MedicationEntity
import com.d4vram.pillmate2.data.local.PillMateDatabase
import java.io.File
import java.util.zip.ZipFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal fun convertedMergeQuantity(quantity: Double, multiplier: Double): Double {
    require(quantity.isFinite() && quantity > 0.0)
    require(multiplier.isFinite() && multiplier > 0.0)
    return (quantity * multiplier).also { require(it.isFinite() && it > 0.0) }
}

internal fun mergedCumulativeQuantity(destination: Double, source: Double, multiplier: Double): Double {
    require(destination.isFinite() && destination >= 0.0)
    require(source.isFinite() && source >= 0.0)
    require(multiplier.isFinite() && multiplier > 0.0)
    return (destination + source * multiplier).also { require(it.isFinite()) }
}

internal fun expectedMergeMultiplier(sourceDose: String?, destinationDose: String?): Double? {
    fun strength(dose: String?): Double? = dose?.trim()?.let {
        Regex("^([0-9]+(?:[.,][0-9]+)?)\\s*mg$", RegexOption.IGNORE_CASE)
            .matchEntire(it)?.groupValues?.get(1)?.replace(',', '.')?.toDoubleOrNull()
            ?.takeIf { value -> value.isFinite() && value > 0.0 }
    }
    val source = strength(sourceDose) ?: return null
    val destination = strength(destinationDose) ?: return null
    return source / destination
}

internal fun mergeFactorPreservesStrength(sourceDose: String?, destinationDose: String?, multiplier: Double): Boolean {
    val expected = expectedMergeMultiplier(sourceDose, destinationDose) ?: return true
    return multiplier.isFinite() && kotlin.math.abs(multiplier - expected) <= expected * 1e-9
}

internal fun mergeHistoryDoseMatchesSource(historyDose: String?, sourceDose: String): Boolean {
    if (historyDose == null || historyDose.trim() == sourceDose.trim()) return true
    if (expectedMergeMultiplier(historyDose, sourceDose) == 1.0) return true
    // Older records stored just the strength number; inherit mg only from the selected source.
    val numeric = historyDose.trim().takeIf { Regex("^[0-9]+(?:[.,][0-9]+)?$").matches(it) } ?: return false
    return expectedMergeMultiplier("$numeric mg", sourceDose) == 1.0
}

internal fun mergedHistoryEntry(
    entry: HistoryEntryEntity,
    destination: MedicationEntity,
    multiplier: Double
): HistoryEntryEntity {
    require(mergeFactorPreservesStrength(entry.dose, destination.dose, multiplier))
    return entry.copy(
        medicationId = destination.id,
        medicationName = destination.name,
        dose = destination.dose,
        colorArgb = destination.colorArgb,
        emoji = destination.emoji,
        quantity = convertedMergeQuantity(entry.quantity, multiplier)
    )
}

/** Explicit, user-directed conversion; never infers equivalence from medication names. */
class MedicationMerge(private val context: Context, private val repository: MedicationRepository) {
    suspend fun merge(sourceId: String, destinationId: String, multiplier: Double): File =
        withContext(Dispatchers.IO) {
            require(sourceId != destinationId)
            require(multiplier.isFinite() && multiplier > 0.0)
            val database = PillMateDatabase.getInstance(context)
            database.withTransaction {
                // Hold the transaction while backing up so quick takes cannot outpace the restore point.
                val backup = BackupManager(context, repository).createManualBackup()
                check(backup.isFile && backup.length() > 0L)
                val medications = database.medicationDao().getAllMedications()
                val source = requireNotNull(medications.find { it.id == sourceId })
                val destination = requireNotNull(medications.find { it.id == destinationId })
                // Group links must be resolved explicitly before removing their medication.
                require(source.parentMedicationId == null && medications.none { it.parentMedicationId == sourceId })
                require(source.type == destination.type)
                require(mergeFactorPreservesStrength(source.dose, destination.dose, multiplier))
                val history = database.historyDao().getAllHistory()
                ZipFile(backup).use { zip ->
                    check(zip.getEntry("backup.csv") != null)
                    medications.mapNotNull { it.photoPath }.forEach { photo ->
                        check(zip.getEntry("photos/${File(photo).name}") != null)
                    }
                    if (history.any { it.audioNoteUri != null }) {
                        val secret = checkNotNull(zip.getEntry("audio_notes/install_secret"))
                        check(zip.getInputStream(secret).bufferedReader().use { it.readText() }.isNotBlank())
                    }
                    history.mapNotNull { it.audioNoteUri }.forEach { audioId ->
                        listOf("enc", "salt", "iv").forEach { extension ->
                            check(zip.getEntry("audio_notes/$audioId.$extension") != null)
                        }
                    }
                }
                val medicationIds = medications.map { it.id }.toSet()
                require(history.none { it.medicationId !in medicationIds && it.medicationName.equals(source.name, ignoreCase = true) })
                val converted = history
                    .filter { it.medicationId == sourceId }
                    .map {
                        require(mergeHistoryDoseMatchesSource(it.dose, source.dose))
                        mergedHistoryEntry(it, destination, multiplier)
                    }
                val destinationHistory = history.filter { it.medicationId == destinationId } + converted
                val latest = destinationHistory.maxByOrNull { it.timestampIso }
                val totalQuantity = mergedCumulativeQuantity(destination.takenToday, source.takenToday, multiplier)
                database.medicationDao().upsertMedication(destination.copy(
                    takenToday = totalQuantity,
                    lastTaken = latest?.timestampIso ?: destination.lastTaken
                ))
                database.historyDao().insertAll(converted)
                check(database.historyDao().getAllHistory().map { it.id }.toSet() == history.map { it.id }.toSet())
                database.medicationDao().deleteById(sourceId)
                backup
            }
        }
}
