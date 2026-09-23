package com.d4vram.pillmate2.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.LocalDate
import kotlin.math.cos
import kotlin.math.sin

internal fun dailyRhythmEntries(entries: List<HistoryEntry>, date: LocalDate): List<HistoryEntry> =
    entries.filter { it.timestamp.toLocalDate() == date }.sortedByDescending { it.timestamp }

/** A clock of recorded events, never a progress ring or a suggested consumption target. */
@Composable
fun DailyRhythmTimeline(historyEntries: List<HistoryEntry>, date: LocalDate) {
    val entries = remember(historyEntries, date) { dailyRhythmEntries(historyEntries, date) }
    var showClock by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Text("Tu ritmo de hoy", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("${entries.size} registros · ${date.dayOfMonth}/${date.monthValue}",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = { showClock = !showClock }) {
            Text(if (showClock) "Ocultar reloj" else "Ver reloj de 24 horas")
        }
        if (showClock) {
            val track = MaterialTheme.colorScheme.outlineVariant
            val marker = MaterialTheme.colorScheme.primary
            val summary = entries.joinToString("; ") {
                "${it.timestamp.toLocalTime()} ${it.medicationName}, ${it.quantity} unidades"
            }
            Box(Modifier.size(232.dp).align(Alignment.CenterHorizontally)) {
                Canvas(Modifier.fillMaxSize().padding(28.dp).semantics {
                    contentDescription = "Reloj de 24 horas. ${entries.size} registros. $summary"
                }) {
                    val radius = size.minDimension / 2 - 8.dp.toPx()
                    drawCircle(track, radius, style = Stroke(5.dp.toPx()))
                    entries.forEach { entry ->
                        val minute = entry.timestamp.hour * 60 + entry.timestamp.minute
                        val angle = minute / 1440.0 * 2 * Math.PI - Math.PI / 2
                        drawCircle(marker, 5.dp.toPx(), Offset(
                            center.x + radius * cos(angle).toFloat(),
                            center.y + radius * sin(angle).toFloat()
                        ))
                    }
                }
                Text("00", Modifier.align(Alignment.TopCenter))
                Text("06", Modifier.align(Alignment.CenterEnd))
                Text("12", Modifier.align(Alignment.BottomCenter))
                Text("18", Modifier.align(Alignment.CenterStart))
                Text("24 horas", Modifier.align(Alignment.Center), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Cada punto es una toma registrada, no un objetivo.", style = MaterialTheme.typography.bodySmall)
        }
        if (entries.isEmpty()) {
            Text("Aún no hay registros hoy", style = MaterialTheme.typography.bodyMedium)
        }
    }
}
