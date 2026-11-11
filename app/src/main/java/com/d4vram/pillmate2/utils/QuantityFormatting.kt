package com.d4vram.pillmate2.utils

import java.text.NumberFormat
import java.util.Locale

/** Displays whole and fractional medication units using the device's decimal separator. */
fun formatQuantity(quantity: Double, locale: Locale = Locale.getDefault()): String =
    NumberFormat.getNumberInstance(locale).apply {
        isGroupingUsed = false
        maximumFractionDigits = 6
    }.format(quantity)

/**
 * Reads what the user typed in the quantity field. Accepts "," or "." as decimal separator
 * (some decimal keyboards only offer "."). Returns null when it isn't a usable dose (0 < q ≤ 999).
 */
fun parseQuantity(text: String): Double? =
    text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it <= 999.0 }
