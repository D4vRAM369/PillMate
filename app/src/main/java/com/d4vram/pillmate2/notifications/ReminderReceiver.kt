package com.d4vram.pillmate2.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver que recibe las alarmas programadas y muestra notificaciones.
 *
 * ¿Qué es un BroadcastReceiver?
 * Es como un "buzón" que recibe mensajes (Intents) del sistema o de otras apps.
 * En este caso, recibe mensajes del AlarmManager cuando llega la hora programada.
 *
 * FLUJO:
 * 1. AlarmManager programa una alarma para las 08:00
 * 2. A las 08:00, AlarmManager envía un Intent
 * 3. Este Receiver lo recibe en onReceive()
 * 4. Extraemos los datos del medicamento del Intent
 * 5. Llamamos a NotificationHelper para mostrar la notificación
 */
class ReminderReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
        const val EXTRA_MEDICATION_DOSE = "medication_dose"
        const val EXTRA_MEDICATION_EMOJI = "medication_emoji"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Extraer datos del medicamento del Intent
                val medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID) ?: return@launch
                val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME) ?: "Medicamento"
                val dose = intent.getStringExtra(EXTRA_MEDICATION_DOSE) ?: ""
                val emoji = intent.getStringExtra(EXTRA_MEDICATION_EMOJI) ?: "💊"

                // Mostrar la notificación
                NotificationHelper.showReminderNotification(
                    context = context,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    dose = dose,
                    emoji = emoji
                )

                // Reprogramar la próxima alarma
                ReminderScheduler.scheduleNextReminder(context, medicationId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}
