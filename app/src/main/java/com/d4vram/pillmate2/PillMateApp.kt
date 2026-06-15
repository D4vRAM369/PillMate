package com.d4vram.pillmate2

import android.app.Application
import com.d4vram.pillmate2.data.BackupScheduler
import com.d4vram.pillmate2.notifications.NotificationHelper
import com.d4vram.pillmate2.ui.theme.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PillMateApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)

        // Crear canales de notificaciones (recordatorios + toma rápida)
        NotificationHelper.createNotificationChannel(this)
        NotificationHelper.createQuickTakeChannel(this)
        appScope.launch {
            BackupScheduler.refresh(applicationContext)
            // Re-postear la notificación persistente de toma rápida si el
            // usuario la tenía activada (OEMs agresivos pueden haberla matado).
            if (ThemeManager(applicationContext).quickTakeNotificationEnabled.first()) {
                NotificationHelper.showQuickTakeNotification(applicationContext)
            }
        }
    }
}
