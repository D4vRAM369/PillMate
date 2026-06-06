package com.d4vram.pillmate2.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import java.io.File
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.Flow

class BackupManager(
    private val context: Context,
    private val repository: MedicationRepository
) {
    val lastBackupFlow: Flow<Long?> = context.backupDataStore.data
        .map { preferences -> preferences[BackupPreferenceKeys.LAST_BACKUP_TIMESTAMP] }

    val autoBackupEnabledFlow: Flow<Boolean> = context.backupDataStore.data
        .map { preferences -> preferences[BackupPreferenceKeys.AUTO_BACKUP_ENABLED] ?: false }

    val autoBackupIntervalHoursFlow: Flow<Int> = context.backupDataStore.data
        .map { preferences -> preferences[BackupPreferenceKeys.AUTO_BACKUP_INTERVAL_HOURS] ?: BackupPreferenceKeys.DEFAULT_INTERVAL_HOURS }

    suspend fun createManualBackup(): File {
        val file = createBackupFile(prefix = "manual")
        markBackupCreated()
        return file
    }

    suspend fun createAutomaticBackupIfNeeded(): File? {
        val now = Instant.now()
        val preferences = context.backupDataStore.data.first()
        val enabled = preferences[BackupPreferenceKeys.AUTO_BACKUP_ENABLED] ?: false
        if (!enabled) return null

        val intervalHours = preferences[BackupPreferenceKeys.AUTO_BACKUP_INTERVAL_HOURS]
            ?: BackupPreferenceKeys.DEFAULT_INTERVAL_HOURS
        val lastBackup = context.backupDataStore.data
            .map { it[BackupPreferenceKeys.LAST_BACKUP_TIMESTAMP] }
            .first()
            ?.let { Instant.ofEpochMilli(it) }

        val shouldBackup = lastBackup == null || ChronoUnit.HOURS.between(lastBackup, now) >= intervalHours
        if (!shouldBackup) return null

        val file = createBackupFile(prefix = "auto")
        markBackupCreated(now.toEpochMilli())
        return file
    }

    suspend fun shouldCreateAutomaticBackup(): Boolean {
        val preferences = context.backupDataStore.data.first()
        val enabled = preferences[BackupPreferenceKeys.AUTO_BACKUP_ENABLED] ?: false
        if (!enabled) return false

        val intervalHours = preferences[BackupPreferenceKeys.AUTO_BACKUP_INTERVAL_HOURS]
            ?: BackupPreferenceKeys.DEFAULT_INTERVAL_HOURS
        val lastBackup = preferences[BackupPreferenceKeys.LAST_BACKUP_TIMESTAMP]
            ?.let { Instant.ofEpochMilli(it) }
        return lastBackup == null || ChronoUnit.HOURS.between(lastBackup, Instant.now()) >= intervalHours
    }

    suspend fun setAutoBackupEnabled(enabled: Boolean) {
        context.backupDataStore.edit { prefs ->
            prefs[BackupPreferenceKeys.AUTO_BACKUP_ENABLED] = enabled
        }
    }

    suspend fun setAutoBackupIntervalHours(hours: Int) {
        context.backupDataStore.edit { prefs ->
            prefs[BackupPreferenceKeys.AUTO_BACKUP_INTERVAL_HOURS] = hours.coerceIn(1, 24 * 30)
        }
    }

    suspend fun markBackupCreated(timestampMillis: Long = System.currentTimeMillis()) {
        context.backupDataStore.edit { prefs ->
            prefs[BackupPreferenceKeys.LAST_BACKUP_TIMESTAMP] = timestampMillis
        }
    }

    private suspend fun createBackupFile(prefix: String): File = withContext(Dispatchers.IO) {
        val medications = repository.getMedicationsSnapshot()
        val history = repository.getHistorySnapshot()

        val directory = context.getExternalFilesDir("backups") ?: context.filesDir
        if (!directory.exists()) {
            directory.mkdirs()
        }
        val timestamp = System.currentTimeMillis()

        // 📦 Crear backup en formato ZIP (incluye CSV + fotos)
        val zipFile = File(directory, "pillmate_${prefix}_$timestamp.zip")
        BackupZipManager.createBackupZip(context, medications, history, zipFile)

        zipFile
    }
}
