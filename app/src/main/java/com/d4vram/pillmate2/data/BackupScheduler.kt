package com.d4vram.pillmate2.data

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import kotlinx.coroutines.flow.first

object BackupScheduler {
    private const val REQUEST_CODE = 64_204

    suspend fun refresh(context: Context) {
        val preferences = context.backupDataStore.data.first()
        val enabled = preferences[BackupPreferenceKeys.AUTO_BACKUP_ENABLED] ?: false
        val intervalHours = preferences[BackupPreferenceKeys.AUTO_BACKUP_INTERVAL_HOURS]
            ?: BackupPreferenceKeys.DEFAULT_INTERVAL_HOURS

        if (enabled) {
            scheduleNext(context, intervalHours)
        } else {
            cancel(context)
        }
    }

    fun scheduleNext(context: Context, intervalHours: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAt = System.currentTimeMillis() + intervalHours.coerceAtLeast(1) * 60L * 60L * 1000L
        val pendingIntent = pendingIntent(context)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    fun cancel(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent(context))
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, BackupReceiver::class.java)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
