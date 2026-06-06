package com.d4vram.pillmate2

import android.app.Application
import com.d4vram.pillmate2.data.BackupScheduler
import com.d4vram.pillmate2.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class PillMateApp : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)

        // Crear canal de notificaciones para recordatorios
        NotificationHelper.createNotificationChannel(this)
        appScope.launch {
            BackupScheduler.refresh(applicationContext)
        }
    }
}
