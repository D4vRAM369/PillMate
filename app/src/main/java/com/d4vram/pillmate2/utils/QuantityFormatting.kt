package com.d4vram.pillmate2.utils

import java.text.NumberFormat
import java.util.Locale

/** Displays whole and fractional medication units using the device's decimal separator. */
fun formatQuantity(quantity: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = 6
    }.format(quantity)
