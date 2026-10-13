package com.d4vram.pillmate2.utils

import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import com.d4vram.pillmate2.R
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Formato de fecha con las piezas de [skeleton] ("EEEdM" = día de la semana, día y mes) puestas en el
 * orden y con los separadores de [locale]: "lun, 13/10" en español, "Mon, 10/13" en inglés de EE. UU.
 */
fun localizedDateFormatter(skeleton: String, locale: Locale): DateTimeFormatter =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale)

/** "Hoy", "Ayer" o la fecha completa, en el idioma del móvil. */
@Composable
fun relativeDayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> stringResource(R.string.period_today)
        today.minusDays(1) -> stringResource(R.string.period_yesterday)
        else -> date.format(localizedDateFormatter("ddMMyyyy", LocalConfiguration.current.locales[0]))
    }
}
