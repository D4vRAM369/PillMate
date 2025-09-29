package com.d4vram.pillmate2.model

import androidx.compose.ui.graphics.Color

/**
 * 📊 Modelo de datos para un medicamento
 * Representa cada pastilla con su información y estado
 */
data class Medication(
    val id: String,
    val name: String,
    val dose: String,
    val type: String,
    val color: Color,
    val emoji: String,
    val takenToday: Int = 0,
    val dailyGoal: Int = 1,
    val lastTaken: String? = null,
    val isPrn: Boolean = false,
    val sortOrder: Int = 0, // PRN = Pro Re Nata (según necesidad) + orden en la cuadrícula
    val scheduleTime: String? = null,
    val scheduleDays: List<String> = emptyList()
)
