package com.d4vram.pillmate2.ui.stats

/**
 * Representa un punto de actividad en el tiempo.
 * Se centra en valores registrados puramente, sin metas de cumplimiento.
 */
data class ActivityDataPoint(
    val dayName: String,
    val totalValue: Float = 0f,
    val unit: String = "",
    val fullDate: String = "",
    val hasRecords: Boolean = false,
    val date: java.time.LocalDate? = null
)
