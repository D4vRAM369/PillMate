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

    // Canal aparte para la notificación persistente de toma rápida: baja
    // prioridad, silenciosa, sin vibrar. No debe competir con los recordatorios.
    private const val QUICK_TAKE_CHANNEL_ID = "pillmate_quick_take"
    // ID fijo: solo existe una notificación persistente a la vez.
    const val QUICK_TAKE_NOTIFICATION_ID = 0x4D454431 // "MED1"

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
            val channel = NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_channel_reminders), importance).apply {
                description = context.getString(R.string.notif_channel_reminders_desc)
                enableVibration(true)
                enableLights(true)
            }

            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * Crea el canal de la notificación persistente de toma rápida.
     * IMPORTANCE_LOW = visible pero silenciosa (sin sonido ni vibración),
     * apropiado para una notificación que está siempre presente.
     */
    fun createQuickTakeChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                QUICK_TAKE_CHANNEL_ID,
                context.getString(R.string.quick_take_title),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.settings_quick_notification_on)
                enableVibration(false)
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    /**
     * Postea (o refresca) la notificación persistente de toma rápida.
     * Al tocarla abre el bottom-sheet con los medicamentos más tomados.
     */
    fun showQuickTakeNotification(context: Context) {
        if (!hasNotificationPermission(context)) return
        createQuickTakeChannel(context)

        val intent = Intent(context, com.d4vram.pillmate2.ui.quicktake.QuickTakeSheetActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            QUICK_TAKE_NOTIFICATION_ID,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, QUICK_TAKE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("PillMate")
            .setContentText(context.getString(R.string.notif_quick_take_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setOngoing(true)       // No se puede deslizar para descartar
            .setAutoCancel(false)   // No se cierra al tocarla
            .setShowWhen(false)
            .setContentIntent(pendingIntent)
            .build()

        NotificationManagerCompat.from(context)
            .notify(QUICK_TAKE_NOTIFICATION_ID, notification)
    }

    /**
     * Retira la notificación persistente de toma rápida.
     */
    fun cancelQuickTakeNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(QUICK_TAKE_NOTIFICATION_ID)
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
            .setContentTitle(context.getString(R.string.notif_reminder_title, emoji, medicationName))
            .setContentText(context.getString(R.string.stats_entry_dose, dose))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true) // Se cierra al tocarla
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_check, context.getString(R.string.notif_take_now), markTakenPendingIntent)
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
