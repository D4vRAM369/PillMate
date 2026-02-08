package com.d4vram.pillmate2

import android.app.Application
import com.d4vram.pillmate2.notifications.NotificationHelper

class PillMateApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)

        // Crear canal de notificaciones para recordatorios
        NotificationHelper.createNotificationChannel(this)
    }
}
