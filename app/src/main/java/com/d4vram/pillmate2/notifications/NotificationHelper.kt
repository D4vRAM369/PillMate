package com.d4vram.pillmate2.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.d4vram.pillmate2.MainActivity
import com.d4vram.pillmate2.R

/**
 * Helper para gestionar notificaciones de recordatorios de medicamentos.
 *
 * CONCEPTOS CLAVE:
 * - NotificationChannel: Categoría de notificaciones (obligatorio en Android 8+)
 * - NotificationCompat: Builder compatible con versiones antiguas de Android
 * - PendingIntent: "Promesa" de ejecutar algo cuando el usuario toque la notificación
 */
object NotificationHelper {

    private const val CHANNEL_ID = "pillmate_reminders"
    private const val CHANNEL_NAME = "Recordatorios de Medicamentos"
    private const val CHANNEL_DESCRIPTION = "Notificaciones para recordarte tomar tus medicamentos"

    /**
     * Crea el canal de notificaciones.
     * Debe llamarse una vez al iniciar la app (normalmente en Application.onCreate).
     *
     * ¿Por qué un canal?
     * Android 8+ agrupa notificaciones en canales para que el usuario pueda
     * silenciar/configurar tipos específicos sin afectar otros.
     */
    fun createNotificationChannel(context: Context) {
        // Solo necesario en Android 8 (Oreo) y superior
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, importance).apply {
                description = CHANNEL_DESCRIPTION
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Muestra una notificación de recordatorio.
     *
     * @param context Contexto de Android
     * @param medicationId ID único del medicamento (usado como notificationId)
     * @param medicationName Nombre del medicamento a mostrar
     * @param dose Dosis del medicamento
     * @param emoji Emoji para mostrar en el título
     */
    fun showReminderNotification(
        context: Context,
        medicationId: String,
        medicationName: String,
        dose: String,
        emoji: String
    ) {
        // Verificar permiso en Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // Sin permiso, no podemos mostrar la notificación
                return
            }
        }

        // Intent que abre la app al tocar la notificación
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra("medicationId", medicationId)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            medicationId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Acción "Tomar ahora": registra la toma sin abrir la app.
        // getBroadcast -> MarkTakenReceiver. requestCode distinto (offset) para no
        // colisionar con el PendingIntent de contenido del mismo medicamento.
        val markTakenIntent = Intent(context, MarkTakenReceiver::class.java).apply {
            action = MarkTakenReceiver.ACTION_MARK_TAKEN
            putExtra(MarkTakenReceiver.EXTRA_MEDICATION_ID, medicationId)
            putExtra(MarkTakenReceiver.EXTRA_MEDICATION_NAME, medicationName)
        }
        val markTakenPendingIntent = PendingIntent.getBroadcast(
            context,
            medicationId.hashCode() + 1,
            markTakenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Construir la notificación
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // TODO: Usar icono personalizado
            .setContentTitle("$emoji Es hora de tomar $medicationName")
            .setContentText("Dosis: $dose")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true) // Se cierra al tocarla
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_launcher_foreground, "Tomar ahora", markTakenPendingIntent)
            .build()

        // Mostrar la notificación
        NotificationManagerCompat.from(context).notify(
            medicationId.hashCode(), // ID único para esta notificación
            notification
        )
    }

    /**
     * Cancela una notificación específica.
     */
    fun cancelNotification(context: Context, medicationId: String) {
        NotificationManagerCompat.from(context).cancel(medicationId.hashCode())
    }

    /**
     * Verifica si la app tiene permiso para mostrar notificaciones.
     */
    fun hasNotificationPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Antes de Android 13, el permiso se otorga automáticamente
        }
    }

    /**
     * Verifica permiso/capacidad de alarmas exactas en Android 12+.
     */
    fun hasExactAlarmPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    /**
     * Abre pantalla del sistema para conceder alarmas exactas.
     */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }
}
