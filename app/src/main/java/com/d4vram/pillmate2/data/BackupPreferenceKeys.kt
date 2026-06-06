package com.d4vram.pillmate2.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.backupDataStore by preferencesDataStore(name = "backup_preferences")

internal object BackupPreferenceKeys {
    val LAST_BACKUP_TIMESTAMP = longPreferencesKey("last_backup_timestamp")
    val AUTO_BACKUP_FOLDER_URI = stringPreferencesKey("auto_backup_folder_uri")
    val AUTO_BACKUP_ENABLED = booleanPreferencesKey("auto_backup_enabled")
    val AUTO_BACKUP_INTERVAL_HOURS = intPreferencesKey("auto_backup_interval_hours")

    const val DEFAULT_INTERVAL_HOURS = 24
}
