package com.d4vram.pillmate2.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.d4vram.pillmate2.data.local.PillMateDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BackupReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = PillMateDatabase.getInstance(context)
                val repository = MedicationRepository(
                    database = database,
                    medicationDao = database.medicationDao(),
                    historyDao = database.historyDao()
                )
                val safManager = BackupSafManager(context.applicationContext, repository)
                val created = safManager.createAutoBackupToFolder()
                val preferences = context.backupDataStore.data.first()
                val enabled = preferences[BackupPreferenceKeys.AUTO_BACKUP_ENABLED] ?: false
                val intervalHours = preferences[BackupPreferenceKeys.AUTO_BACKUP_INTERVAL_HOURS]
                    ?: BackupPreferenceKeys.DEFAULT_INTERVAL_HOURS
                if (enabled) {
                    BackupScheduler.scheduleNext(context.applicationContext, intervalHours)
                }
                if (!created && !enabled) {
                    BackupScheduler.cancel(context.applicationContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
