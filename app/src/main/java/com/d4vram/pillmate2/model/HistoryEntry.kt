package com.d4vram.pillmate2.model

import androidx.compose.ui.graphics.Color

/**
 * 📈 Entrada del historial de medicamentos tomados
 */
data class HistoryEntry(
    val id: String,
    val medicationName: String,
    val time: String,
    val date: String,
    val color: Color,
    val emoji: String
)