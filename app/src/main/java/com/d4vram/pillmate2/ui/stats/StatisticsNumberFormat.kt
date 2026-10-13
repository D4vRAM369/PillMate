package com.d4vram.pillmate2.ui.stats

import java.util.Locale

internal fun formatStatisticsNumber(value: Float, locale: Locale): String =
    if (value == value.toInt().toFloat()) value.toInt().toString()
    else String.format(locale, "%.1f", value)
