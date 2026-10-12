package com.d4vram.pillmate2.ai

/**
 * Frecuencia de registro: proporción de días del periodo con al menos una toma.
 * Única fuente de las etiquetas; StatisticsViewModel las genera desde aquí.
 */
enum class LoggingConsistency(val label: String, val minDayRatio: Float) {
    VERY_FREQUENT("Muy frecuente", 0.8f),
    FREQUENT("Frecuente", 0.5f),
    INFREQUENT("Poco frecuente", 0.2f),
    SPARSE("Espaciado", 0f);

    val isLow: Boolean get() = this == INFREQUENT || this == SPARSE

    companion object {
        fun fromDayRatio(ratio: Float): LoggingConsistency = entries.first { ratio >= it.minDayRatio }
        fun fromLabel(label: String): LoggingConsistency? = entries.firstOrNull { it.label == label }
    }
}
