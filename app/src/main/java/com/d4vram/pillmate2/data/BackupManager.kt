package com.d4vram.pillmate2.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

// DataStore dedicado al horario de copias automáticas
val Context.backupDataStore by preferencesDataStore(name = "backup_preferences")

class BackupManager(
    private val context: Context,
    private val repository: MedicationRepository
) {
    companion object {
        private val LAST_BACKUP_KEY = longPreferencesKey("last_backup_timestamp")
    }

    val lastBackupFlow: Flow<Long?> = context.backupDataStore.data
        .map { preferences -> preferences[LAST_BACKUP_KEY] }

    suspend fun createManualBackup(): File {
        val file = createBackupFile(prefix = "manual")
        context.backupDataStore.edit { prefs ->
            prefs[LAST_BACKUP_KEY] = System.currentTimeMillis()
        }
        return file
    }

    suspend fun createAutomaticBackupIfNeeded(): File? {
        val now = Instant.now()
        val lastBackup = context.backupDataStore.data
            .map { preferences -> preferences[LAST_BACKUP_KEY] }
            .first()
            ?.let { Instant.ofEpochMilli(it) }

        val shouldBackup = lastBackup == null || ChronoUnit.HOURS.between(lastBackup, now) >= 24
        if (!shouldBackup) return null

        val file = createBackupFile(prefix = "auto")
        context.backupDataStore.edit { prefs ->
            prefs[LAST_BACKUP_KEY] = now.toEpochMilli()
        }
        return file
    }

    private suspend fun createBackupFile(prefix: String): File = withContext(Dispatchers.IO) {
        val medications = repository.getMedicationsSnapshot()
        val history = repository.getHistorySnapshot()
        val csv = CsvUtils.buildCsv(medications, history)

        val directory = context.getExternalFilesDir("backups") ?: context.filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val timestamp = System.currentTimeMillis()
        val file = File(directory, "pillmate_${prefix}_$timestamp.csv")
        file.writeText(csv)
        file
    }
}
