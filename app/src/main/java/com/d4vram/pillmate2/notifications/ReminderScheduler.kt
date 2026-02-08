package com.d4vram.pillmate2.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import com.d4vram.pillmate2.data.local.PillMateDatabase
import com.d4vram.pillmate2.model.Medication
import java.util.Calendar

/**
 * Gestiona la programación de alarmas para recordatorios de medicamentos.
 *
 * CONCEPTOS CLAVE:
 *
 * AlarmManager:
 *   Servicio del sistema que "despierta" tu app en momentos específicos.
 *   Aunque el teléfono esté en modo ahorro de batería, la alarma se dispara.
 *
 * PendingIntent:
 *   Una "promesa" de ejecutar un Intent en el futuro.
 *   Se guarda en el sistema y se ejecuta cuando llega la hora.
 *
 * setExactAndAllowWhileIdle:
 *   Método para alarmas precisas que funcionan incluso en modo Doze.
 *   Android puede retrasar alarmas normales para ahorrar batería,
 *   pero las "exactas" se disparan siempre a tiempo.
 *
 * FLUJO DE PROGRAMACIÓN:
 * 1. Usuario guarda medicamento con reminderEnabled=true y hora="08:30"
 * 2. Llamamos a scheduleReminder() con esos datos
 * 3. Calculamos el próximo timestamp (hoy 08:30 o mañana 08:30 si ya pasó)
 * 4. Creamos PendingIntent con los datos del medicamento
 * 5. Programamos alarma con AlarmManager
 * 6. A las 08:30, ReminderReceiver recibe el Intent y muestra notificación
 */
object ReminderScheduler {

    private const val TAG = "ReminderScheduler"

    /**
     * Programa un recordatorio para un medicamento.
     *
     * @param context Contexto de Android
     * @param medication Medicamento con los datos de horario
     */
    fun scheduleReminder(context: Context, medication: Medication) {
        // Validaciones
        if (!medication.reminderEnabled) {
            Log.d(TAG, "Recordatorio desactivado para ${medication.name}")
            cancelReminder(context, medication.id)
            return
        }

        val scheduleTime = medication.scheduleTime
        if (scheduleTime.isNullOrBlank()) {
            Log.d(TAG, "No hay hora programada para ${medication.name}")
            return
        }

        // Parsear hora (formato "HH:mm")
        val timeParts = scheduleTime.split(":")
        if (timeParts.size != 2) {
            Log.e(TAG, "Formato de hora inválido: $scheduleTime")
            return
        }

        val hour = timeParts[0].toIntOrNull() ?: return
        val minute = timeParts[1].toIntOrNull() ?: return

        // Calcular próximo disparo
        val triggerTime = calculateNextTriggerTime(hour, minute, medication.scheduleDays)
        if (triggerTime == null) {
            Log.d(TAG, "No hay próximo día válido para ${medication.name}")
            return
        }

        // Crear PendingIntent
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_MEDICATION_ID, medication.id)
            putExtra(ReminderReceiver.EXTRA_MEDICATION_NAME, medication.name)
            putExtra(ReminderReceiver.EXTRA_MEDICATION_DOSE, medication.dose)
            putExtra(ReminderReceiver.EXTRA_MEDICATION_EMOJI, medication.emoji)
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medication.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Programar alarma
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: Verificar si podemos programar alarmas exactas
                if (alarmManager.canScheduleExactAlarms()) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                } else {
                    // Fallback: usar alarma inexacta (puede retrasarse hasta 15 min)
                    alarmManager.setAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerTime,
                        pendingIntent
                    )
                    Log.w(TAG, "Usando alarma inexacta - sin permiso SCHEDULE_EXACT_ALARM")
                }
            } else {
                // Android 11 y anteriores
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerTime,
                    pendingIntent
                )
            }

            Log.d(TAG, "Alarma programada para ${medication.name} a las $scheduleTime (trigger: $triggerTime)")
        } catch (e: SecurityException) {
            Log.e(TAG, "No se pudo programar alarma exacta: ${e.message}")
            // Fallback a alarma inexacta
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerTime,
                pendingIntent
            )
        }
    }

    /**
     * Cancela el recordatorio de un medicamento.
     */
    fun cancelReminder(context: Context, medicationId: String) {
        val intent = Intent(context, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmManager.cancel(pendingIntent)

        Log.d(TAG, "Alarma cancelada para medicationId: $medicationId")
    }

    /**
     * Programa el próximo recordatorio (llamado después de mostrar notificación).
     */
    suspend fun scheduleNextReminder(context: Context, medicationId: String) {
        // Acceder a la BD para obtener los datos actualizados del medicamento
        val database = PillMateDatabase.getInstance(context)
        val medicationEntity = database.medicationDao().getMedicationById(medicationId)

        if (medicationEntity != null && medicationEntity.reminderEnabled) {
            // Convertir a domain model (simplificado)
            val medication = Medication(
                id = medicationEntity.id,
                name = medicationEntity.name,
                dose = medicationEntity.dose,
                type = medicationEntity.type,
                color = androidx.compose.ui.graphics.Color(medicationEntity.colorArgb),
                emoji = medicationEntity.emoji,
                scheduleTime = medicationEntity.scheduleTime,
                scheduleDays = medicationEntity.scheduleDays.split(",").filter { it.isNotBlank() },
                reminderEnabled = medicationEntity.reminderEnabled
            )
            scheduleReminder(context, medication)
        }
    }

    /**
     * Reprograma todos los recordatorios (llamado tras reinicio del dispositivo).
     */
    suspend fun rescheduleAllReminders(context: Context) {
        val database = PillMateDatabase.getInstance(context)
        val medications = database.medicationDao().getAllMedications()

        medications
            .filter { it.reminderEnabled && !it.scheduleTime.isNullOrBlank() }
            .forEach { entity ->
                val medication = Medication(
                    id = entity.id,
                    name = entity.name,
                    dose = entity.dose,
                    type = entity.type,
                    color = androidx.compose.ui.graphics.Color(entity.colorArgb),
                    emoji = entity.emoji,
                    scheduleTime = entity.scheduleTime,
                    scheduleDays = entity.scheduleDays.split(",").filter { it.isNotBlank() },
                    reminderEnabled = entity.reminderEnabled
                )
                scheduleReminder(context, medication)
            }

        Log.d(TAG, "Reprogramados ${medications.count { it.reminderEnabled }} recordatorios")
    }

    /**
     * Calcula el próximo momento en que debe dispararse la alarma.
     *
     * @param hour Hora del día (0-23)
     * @param minute Minuto (0-59)
     * @param scheduleDays Días de la semana ("L", "M", "X", "J", "V", "S", "D")
     * @return Timestamp en milisegundos, o null si no hay día válido
     */
    private fun calculateNextTriggerTime(
        hour: Int,
        minute: Int,
        scheduleDays: List<String>
    ): Long? {
        val now = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        // Si no hay días específicos, programar para hoy o mañana
        if (scheduleDays.isEmpty()) {
            if (targetTime.before(now)) {
                targetTime.add(Calendar.DAY_OF_YEAR, 1)
            }
            return targetTime.timeInMillis
        }

        // Buscar el próximo día válido
        val dayMapping = mapOf(
            "L" to Calendar.MONDAY,
            "M" to Calendar.TUESDAY,
            "X" to Calendar.WEDNESDAY,
            "J" to Calendar.THURSDAY,
            "V" to Calendar.FRIDAY,
            "S" to Calendar.SATURDAY,
            "D" to Calendar.SUNDAY
        )

        val validDays = scheduleDays.mapNotNull { dayMapping[it] }.toSet()
        if (validDays.isEmpty()) return null

        // Buscar hasta 7 días adelante
        for (daysAhead in 0..7) {
            val candidate = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, daysAhead)
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            val dayOfWeek = candidate.get(Calendar.DAY_OF_WEEK)
            val isValidDay = dayOfWeek in validDays
            val isInFuture = candidate.after(now)

            if (isValidDay && isInFuture) {
                return candidate.timeInMillis
            }
        }

        return null
    }
}
