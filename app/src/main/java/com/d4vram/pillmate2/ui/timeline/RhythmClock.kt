package com.d4vram.pillmate2.ui.timeline

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.model.HistoryEntry
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

internal fun dailyRhythmEntries(entries: List<HistoryEntry>, date: LocalDate): List<HistoryEntry> =
    entries.filter { it.timestamp.toLocalDate() == date }.sortedByDescending { it.timestamp }

/** Una toma sobre el reloj. `ring` separa tomas casi simultáneas: 0 va sobre el aro, 1 justo dentro… */
internal data class ClockDot(val entry: HistoryEntry, val minute: Int, val ring: Int)

private const val DOT_BUCKET_MINUTES = 20

internal fun clockDots(entries: List<HistoryEntry>): List<ClockDot> =
    entries.sortedBy { it.timestamp }
        .groupBy { (it.timestamp.hour * 60 + it.timestamp.minute) / DOT_BUCKET_MINUTES }
        .flatMap { (_, sameSlot) ->
            sameSlot.mapIndexed { ring, entry ->
                ClockDot(entry, entry.timestamp.hour * 60 + entry.timestamp.minute, ring)
            }
        }

/** Tomas de un mismo medicamento dentro de una hora del reloj. */
internal data class SlicePart(val label: String, val color: Color, val count: Int)

internal data class HourSlice(val hour: Int, val parts: List<SlicePart>) {
    val total: Int get() = parts.sumOf { it.count }
}

/** Siempre 24 franjas (00..23), vacías incluidas, para que el índice sea la hora. */
internal fun hourSlices(entries: List<HistoryEntry>): List<HourSlice> {
    val byHour = entries.groupBy { it.timestamp.hour }
    return (0..23).map { hour ->
        HourSlice(
            hour,
            byHour[hour].orEmpty().groupBy { it.medicationId }.values
                .map { SlicePart(it.first().clockLabel(), it.first().color, it.size) }
                .sortedByDescending { it.count }
        )
    }
}

/** Leyenda de la vista por horas: cada medicamento con su color, primero los más tomados. */
internal fun clockLegend(entries: List<HistoryEntry>): List<SlicePart> =
    entries.groupBy { it.medicationId }.values
        .map { SlicePart(it.first().clockLabel(), it.first().color, it.size) }
        .sortedByDescending { it.count }

private const val LEGEND_MAX = 6

/** Fracción del día (0 = 00:00 arriba, sentido horario) de un toque a (dx, dy) del centro. */
internal fun dayFraction(dx: Float, dy: Float): Double {
    val turn = (atan2(dy.toDouble(), dx.toDouble()) + PI / 2) / (2 * PI)
    return (turn % 1 + 1) % 1
}

private fun HistoryEntry.clockLabel(): String =
    listOfNotNull(medicationName, dose?.takeIf { it.isNotBlank() }).joinToString(" · ")

private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

private fun pointAt(center: Offset, fraction: Double, radius: Float): Offset {
    val angle = fraction * 2 * PI - PI / 2
    return Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
}

private fun Density.ringRadius(width: Float, height: Float): Float = min(width, height) / 2 - 8.dp.toPx()

private fun Density.dotCenter(dot: ClockDot, width: Float, height: Float): Offset = pointAt(
    Offset(width / 2, height / 2),
    dot.minute / 1440.0,
    // ponytail: las tomas del mismo tramo de 20 min se apilan hacia dentro; a partir de la 5.ª comparten sitio.
    ringRadius(width, height) - dot.ring.coerceAtMost(4) * 12.dp.toPx()
)

/**
 * Reloj de 24 horas de tomas registradas. Nunca es un anillo de progreso ni un objetivo.
 *
 * - Un solo día en [entries]: un punto por toma, en su minuto exacto y con el color del medicamento.
 * - Varios días: 24 barras radiales (una por hora) apiladas por medicamento.
 *
 * Con [interactive], tocar un punto o una barra muestra debajo qué se tomó.
 * Con [showNow] (solo tiene sentido si [entries] es el día de hoy) una raya marca la hora actual.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RhythmClock(
    entries: List<HistoryEntry>,
    modifier: Modifier = Modifier,
    diameter: Dp = 232.dp,
    interactive: Boolean = true,
    showNow: Boolean = false
) {
    val byHour = remember(entries) { entries.map { it.timestamp.toLocalDate() }.distinct().size > 1 }
    val dots = remember(entries) { if (byHour) emptyList() else clockDots(entries) }
    val slices = remember(entries) { if (byHour) hourSlices(entries) else emptyList() }
    var selected by remember(entries) { mutableStateOf<Int?>(null) }

    val now by produceState(LocalTime.now(), showNow) {
        while (showNow) {
            value = LocalTime.now()
            delay(30_000)
        }
    }
    val nowColor = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.outlineVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall
    // remember { } y semantics { } no son @Composable: lo que necesitan se pide aquí, antes.
    val resources = androidx.compose.ui.platform.LocalContext.current.resources
    val summary = remember(entries, resources) {
        entries.sortedBy { it.timestamp }.take(12).joinToString("; ") {
            "${it.timestamp.format(timeFormat)} ${it.medicationName}, " +
                resources.getQuantityString(R.plurals.clock_units, it.quantity, it.quantity)
        }
    }
    val nowSuffix = if (showNow && !byHour) " " + stringResource(R.string.clock_now_suffix, now.format(timeFormat)) else ""
    val clockDescription = stringResource(R.string.clock_description, entries.size, summary) + nowSuffix

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(diameter)) {
            val tapModifier = if (!interactive) Modifier else Modifier.pointerInput(dots, slices) {
                detectTapGestures { tap ->
                    val width = size.width.toFloat()
                    val height = size.height.toFloat()
                    val hit = if (byHour) {
                        (dayFraction(tap.x - width / 2, tap.y - height / 2) * 24).toInt().coerceIn(0, 23)
                            .takeIf { slices[it].total > 0 }
                    } else {
                        dots.indices
                            .minByOrNull { (dotCenter(dots[it], width, height) - tap).getDistance() }
                            ?.takeIf { (dotCenter(dots[it], width, height) - tap).getDistance() <= 24.dp.toPx() }
                    }
                    selected = hit.takeIf { it != selected }
                }
            }
            Canvas(
                Modifier.fillMaxSize().padding(diameter * 0.12f)
                    .semantics {
                        contentDescription = clockDescription
                    }
                    .then(tapModifier)
            ) {
                val radius = ringRadius(size.width, size.height)
                if (byHour) {
                    val inner = radius * 0.5f
                    val barWidth = (2 * PI * inner / 24 * 0.8).toFloat()
                    val max = slices.maxOf { it.total }.coerceAtLeast(1)
                    drawCircle(track, inner, style = Stroke(1.dp.toPx()))
                    slices.forEach { slice ->
                        val fraction = (slice.hour + 0.5) / 24
                        val alpha = if (selected == null || selected == slice.hour) 1f else 0.3f
                        var start = inner
                        slice.parts.forEach { part ->
                            val end = start + (radius - inner) * part.count / max
                            drawLine(part.color.copy(alpha = alpha), pointAt(center, fraction, start), pointAt(center, fraction, end), barWidth)
                            start = end
                        }
                    }
                } else {
                    drawCircle(track, radius, style = Stroke(4.dp.toPx()))
                    for (hour in 0 until 24) {
                        val half = (if (hour % 6 == 0) 6.dp else 3.dp).toPx()
                        drawLine(track, pointAt(center, hour / 24.0, radius - half), pointAt(center, hour / 24.0, radius + half), 2.dp.toPx())
                    }
                    if (showNow) {
                        // "Ahora": una raya que cruza el aro, bajo los puntos. Marca la hora, no un objetivo.
                        val fraction = (now.hour * 60 + now.minute) / 1440.0
                        val half = 9.dp.toPx()
                        drawLine(nowColor, pointAt(center, fraction, radius - half), pointAt(center, fraction, radius + half), 3.dp.toPx(), StrokeCap.Round)
                    }
                    dots.forEachIndexed { index, dot ->
                        val position = dotCenter(dot, size.width, size.height)
                        val alpha = if (selected == null || selected == index) 1f else 0.3f
                        if (selected == index) drawCircle(muted, 9.dp.toPx(), position, style = Stroke(2.dp.toPx()))
                        drawCircle(dot.entry.color.copy(alpha = alpha), 6.dp.toPx(), position)
                    }
                }
            }
            Text("00", Modifier.align(Alignment.TopCenter), style = labelStyle, color = muted)
            Text("06", Modifier.align(Alignment.CenterEnd), style = labelStyle, color = muted)
            Text("12", Modifier.align(Alignment.BottomCenter), style = labelStyle, color = muted)
            Text("18", Modifier.align(Alignment.CenterStart), style = labelStyle, color = muted)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${entries.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                if (diameter >= 180.dp) Text(stringResource(R.string.clock_doses_label), style = labelStyle, color = muted)
            }
        }

        if (interactive) {
            val index = selected
            when {
                entries.isEmpty() -> Text(stringResource(R.string.clock_empty), style = MaterialTheme.typography.bodySmall, color = muted)
                index == null -> {
                    Text(
                        stringResource(if (byHour) R.string.clock_hint_bar else R.string.clock_hint_dot),
                        style = MaterialTheme.typography.bodySmall, color = muted
                    )
                    if (byHour) {
                        val legend = remember(entries) { clockLegend(entries) }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            legend.take(LEGEND_MAX).forEach { part ->
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Box(Modifier.size(10.dp).background(part.color, CircleShape))
                                    Text(part.label, style = labelStyle, color = muted)
                                }
                            }
                            if (legend.size > LEGEND_MAX) Text(stringResource(R.string.clock_legend_more, legend.size - LEGEND_MAX), style = labelStyle, color = muted)
                        }
                    }
                }
                byHour -> {
                    val slice = slices[index]
                    Text(
                        stringResource(R.string.clock_hour_range, slice.hour, (slice.hour + 1) % 24, slice.total),
                        style = MaterialTheme.typography.labelLarge
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        slice.parts.forEach { ClockChip("${it.label} ×${it.count}", it.color) }
                    }
                }
                else -> dots[index].entry.let {
                    ClockChip(stringResource(R.string.clock_chip, it.timestamp.format(timeFormat), it.clockLabel(), pluralStringResource(R.plurals.clock_units, it.quantity, it.quantity)), it.color)
                }
            }
        }
    }
}

@Composable
private fun ClockChip(text: String, color: Color) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        leadingIcon = { Box(Modifier.size(12.dp).background(color, CircleShape)) }
    )
}
