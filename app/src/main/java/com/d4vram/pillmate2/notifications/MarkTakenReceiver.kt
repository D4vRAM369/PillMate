package com.d4vram.pillmate2.notifications

import com.d4vram.pillmate2.R
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.data.local.PillMateDatabase
import com.d4vram.pillmate2.widget.QuickTakeWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Recibe el toque del botón "Tomar ahora" de la notificación de recordatorio.
 *
 * FLUJO:
 * 1. La notificación trae una acción cuyo PendingIntent apunta a este receiver.
 * 2. Al tocar "Tomar ahora", Android envía el Intent aquí (sin abrir la app).
 * 3. Registramos la toma, cerramos la notificación, refrescamos el widget y
 *    mostramos un Toast de confirmación.
 *
 * Por qué un receiver y no abrir la app: el caso "reactivo" (la app ya te avisó
 * qué medicamento tocaba) no debería obligarte a navegar dentro de la app.
 */
class MarkTakenReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_MARK_TAKEN = "com.d4vram.pillmate2.ACTION_MARK_TAKEN"
        const val EXTRA_MEDICATION_ID = "medication_id"
        const val EXTRA_MEDICATION_NAME = "medication_name"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val medicationId = intent.getStringExtra(EXTRA_MEDICATION_ID) ?: return
        val medicationName = intent.getStringExtra(EXTRA_MEDICATION_NAME).orEmpty()

        // goAsync mantiene vivo el receiver mientras corre la corrutina de IO.
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val database = PillMateDatabase.getInstance(appContext)
                MedicationRepository(
                    database = database,
                    medicationDao = database.medicationDao(),
                    historyDao = database.historyDao()
                ).recordMedicationTaken(medicationId, quantity = 1, customTimestamp = null)

                // Cerrar la notificación que disparó esta acción.
                NotificationHelper.cancelNotification(appContext, medicationId)

                // Mantener el widget en sync con la nueva toma.
                QuickTakeWidget().updateAll(appContext)

                // Confirmación visible al usuario.
                ToastNotifier.showTakeConfirmation(appContext, medicationName)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * Muestra un Toast de "Toma registrada" desde contexto de fondo (receiver o
 * callback de widget), saltando siempre al hilo principal.
 */
object ToastNotifier {
    fun showTakeConfirmation(context: Context, medicationName: String) {
        val text = if (medicationName.isBlank()) {
            context.getString(R.string.notif_dose_logged)
        } else {
            context.getString(R.string.notif_dose_logged_named, medicationName)
        }
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context.applicationContext, text, Toast.LENGTH_SHORT).show()
        }
    }
}
