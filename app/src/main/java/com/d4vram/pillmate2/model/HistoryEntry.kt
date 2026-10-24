package com.d4vram.pillmate2.model

import androidx.compose.ui.graphics.Color
import java.time.LocalDateTime

/**
 * 📈 Entrada del historial de medicamentos tomados
 */
data class HistoryEntry(
    val id: String,
    val medicationId: String,
    val medicationName: String,
    val time: String,
    val date: String,
    val color: Color,
    val emoji: String,
    val quantity: Double = 1.0,
    val notes: String = "",
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val dose: String? = null,
    val audioNoteUri: String? = null  // Ruta al archivo de audio encriptado
)
/**
 * Quita las tomas de medicamentos archivados cuando el ajuste "Mostrar en Historial" está apagado.
 * Solo para listas de historial: contadores, gráficas y backups siguen usando todas las tomas.
 */
fun List<HistoryEntry>.hideArchivedUnless(show: Boolean, medications: List<Medication>): List<HistoryEntry> {
    return if (show) this else withoutArchived(medications)
}

/** Tomas de medicamentos que no están archivados. Para estadísticas, gráficas y exportaciones. */
fun List<HistoryEntry>.withoutArchived(medications: List<Medication>): List<HistoryEntry> {
    val archivedIds = medications.filter { it.archived }.map { it.id }.toSet()
    return filterNot { it.medicationId in archivedIds }
}
