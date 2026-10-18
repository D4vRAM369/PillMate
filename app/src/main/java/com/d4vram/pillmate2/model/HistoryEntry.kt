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