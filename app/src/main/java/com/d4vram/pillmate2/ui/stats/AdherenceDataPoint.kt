package com.d4vram.pillmate2.ui.stats

data class AdherenceDataPoint(
    val dayName: String,
    val percentage: Float,
    val taken: Int,
    val goal: Int,
    val totalValue: Float = 0f,
    val unit: String = ""
)
