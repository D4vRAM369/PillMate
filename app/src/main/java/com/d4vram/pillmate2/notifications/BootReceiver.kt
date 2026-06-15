package com.d4vram.pillmate2.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.d4vram.pillmate2.data.BackupScheduler
import com.d4vram.pillmate2.ui.theme.ThemeManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver que se activa cuando el dispositivo termina de arrancar.
 *
 * ¿Por qué es necesario?
 * Las alarmas programadas con AlarmManager se BORRAN cuando el dispositivo se reinicia.
 * Este receiver nos permite reprogramarlas automáticamente.
 *
 * FLUJO:
 * 1. Usuario reinicia el teléfono
 * 2. Android envía BOOT_COMPLETED a todas las apps que lo escuchan
 * 3. Este Receiver lo recibe
 * 4. Reprogramamos todas las alarmas de medicamentos con reminderEnabled=true
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val pendingResult = goAsync()
            // Usar coroutine porque acceder a la BD es operación de IO
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    ReminderScheduler.rescheduleAllReminders(context)
                    BackupScheduler.refresh(context.applicationContext)
                    // Re-postear la notificación persistente de toma rápida: las
                    // notificaciones también se borran al reiniciar el dispositivo.
                    if (ThemeManager(context.applicationContext).quickTakeNotificationEnabled.first()) {
                        NotificationHelper.showQuickTakeNotification(context.applicationContext)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
