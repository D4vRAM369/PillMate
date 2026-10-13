package com.d4vram.pillmate2.ui.stats

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gráfica lineal semanal con área rellena degradada.
 * Muestra el consumo diario en la unidad del medicamento.
 */
@Composable
fun WeeklyLineChart(
    data: List<ActivityDataPoint>,
    unit: String,
    accentColor: Color,
    dateRangeText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    showNavigation: Boolean = true,
    modifier: Modifier = Modifier
) {
    var selectedPointIndex by remember { mutableStateOf<Int?>(null) }

    // Animación de entrada
    val animationProgress = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animationProgress.snapTo(0f)
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(800, easing = FastOutSlowInEasing)
        )
    }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Qué mide: sin esto se confunde con las barras de "Tomas por día"
            Text(
                text = if (unit == "tomas") stringResource(R.string.stats_doses_per_day) else stringResource(R.string.stats_amount_per_day, unitLabel(unit)),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            // Navegación integrada
            if (showNavigation) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onPrevious,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_previous),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Text(
                        text = dateRangeText,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    IconButton(
                        onClick = onNext,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = stringResource(R.string.action_next),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Gráfica
            if (data.isEmpty() || data.all { it.valueInUnit == 0f }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.stats_no_data),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LineChartCanvas(
                    data = data,
                    unit = unitLabel(unit),
                    accentColor = accentColor,
                    animationProgress = animationProgress.value,
                    selectedPointIndex = selectedPointIndex,
                    onPointSelected = { selectedPointIndex = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
        }
    }
}

@Composable
private fun LineChartCanvas(
    data: List<ActivityDataPoint>,
    unit: String,
    accentColor: Color,
    animationProgress: Float,
    selectedPointIndex: Int?,
    onPointSelected: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val secondaryLabelColor = MaterialTheme.colorScheme.onSurfaceVariant.toArgb()
    val tooltipBgColor = MaterialTheme.colorScheme.inverseSurface
    val tooltipTextColor = MaterialTheme.colorScheme.inverseOnSurface.toArgb()
    val surfaceColor = MaterialTheme.colorScheme.surface

    val maxValue = (data.maxOfOrNull { it.valueInUnit } ?: 1f).coerceAtLeast(1f)
    val yAxisMax = maxValue * 1.2f

    // Dentro del Canvas no se pueden pedir textos: idioma y formato se preparan aquí.
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val shortDate = remember(locale) { com.d4vram.pillmate2.utils.localizedDateFormatter("ddMM", locale) }
    val showDayInitial = data.size <= 7

    // Calcular puntos para detección de taps
    val pointPositions = remember(data) { mutableListOf<Offset>() }

    Canvas(
        modifier = modifier
            .pointerInput(data) {
                detectTapGestures { tapOffset ->
                    // Encontrar el punto más cercano
                    val closest = pointPositions.withIndex().minByOrNull { (_, pos) ->
                        (pos - tapOffset).getDistance()
                    }
                    if (closest != null && (closest.value - tapOffset).getDistance() < 40.dp.toPx()) {
                        onPointSelected(if (selectedPointIndex == closest.index) null else closest.index)
                    } else {
                        onPointSelected(null)
                    }
                }
            }
    ) {
        val width = size.width
        val height = size.height
        val leftPadding = 40.dp.toPx()
        val bottomPadding = 45.dp.toPx()
        val topPadding = 10.dp.toPx()

        // Margen derecho: la fecha del último punto va centrada bajo él y se cortaba contra el borde.
        val chartWidth = width - leftPadding - 14.dp.toPx()
        val chartHeight = height - bottomPadding - topPadding

        // Limpiar posiciones previas
        pointPositions.clear()

        // 1. Dibujar grid horizontal punteado
        val gridLines = 4
        val dashEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f)

        val textPaint = android.graphics.Paint().apply {
            color = secondaryLabelColor
            textSize = 10.sp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
            isAntiAlias = true
        }

        for (i in 0..gridLines) {
            val fraction = i.toFloat() / gridLines
            val y = topPadding + chartHeight - (chartHeight * fraction)
            val value = yAxisMax * fraction

            // Línea de grid
            drawLine(
                color = axisColor,
                start = Offset(leftPadding, y),
                end = Offset(width, y),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashEffect
            )

            // Etiqueta del eje Y
            val valueText = formatStatisticsNumber(value, locale)
            drawContext.canvas.nativeCanvas.drawText(
                valueText,
                leftPadding - 8.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint
            )
        }

        // 2. Calcular posiciones de puntos
        val pointSpacing = if (data.size > 1) chartWidth / (data.size - 1) else chartWidth
        val points = data.mapIndexed { index, point ->
            val x = leftPadding + (index * pointSpacing)
            val normalizedValue = (point.valueInUnit / yAxisMax).coerceIn(0f, 1f)
            val y = topPadding + chartHeight - (chartHeight * normalizedValue * animationProgress)
            Offset(x, y)
        }

        pointPositions.addAll(points)

        // 3. Dibujar área rellena con degradado
        if (points.size >= 2) {
            val areaPath = Path().apply {
                moveTo(points.first().x, topPadding + chartHeight)

                // Línea a primer punto
                lineTo(points.first().x, points.first().y)

                // Curvas Bezier entre puntos
                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val controlX = (p0.x + p1.x) / 2

                    cubicTo(
                        controlX, p0.y,
                        controlX, p1.y,
                        p1.x, p1.y
                    )
                }

                // Cerrar el path
                lineTo(points.last().x, topPadding + chartHeight)
                close()
            }

            val gradientBrush = Brush.verticalGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.4f),
                    accentColor.copy(alpha = 0.1f),
                    accentColor.copy(alpha = 0f)
                ),
                startY = topPadding,
                endY = topPadding + chartHeight
            )

            drawPath(
                path = areaPath,
                brush = gradientBrush
            )
        }

        // 4. Dibujar línea
        if (points.size >= 2) {
            val linePath = Path().apply {
                moveTo(points.first().x, points.first().y)

                for (i in 0 until points.size - 1) {
                    val p0 = points[i]
                    val p1 = points[i + 1]
                    val controlX = (p0.x + p1.x) / 2

                    cubicTo(
                        controlX, p0.y,
                        controlX, p1.y,
                        p1.x, p1.y
                    )
                }
            }

            drawPath(
                path = linePath,
                color = accentColor,
                style = Stroke(
                    width = 2.5.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }

        // 5. Dibujar puntos
        points.forEachIndexed { index, point ->
            val isSelected = selectedPointIndex == index
            val pointRadius = if (isSelected) 8.dp.toPx() else 5.dp.toPx()

            // Círculo exterior (borde)
            drawCircle(
                color = accentColor,
                radius = pointRadius,
                center = point
            )

            // Círculo interior (relleno)
            drawCircle(
                color = if (isSelected) accentColor else surfaceColor,
                radius = pointRadius - 2.dp.toPx(),
                center = point
            )
        }

        // 6. Etiquetas del eje X
        val dayPaint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 11.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        val datePaint = android.graphics.Paint().apply {
            color = secondaryLabelColor
            textSize = 9.sp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }

        // Como mucho ~8 fechas, sea cual sea el periodo, para que no se pisen.
        val labelInterval = (data.size + 6) / 7

        data.forEachIndexed { index, dataPoint ->
            // La última siempre; las demás cada `labelInterval`, salvo que queden pegadas a la última.
            // El punto seleccionado no añade fecha: ya la lleva el globo.
            if (index == data.lastIndex || (index % labelInterval == 0 && data.lastIndex - index >= labelInterval)) {
                val x = leftPadding + (index * pointSpacing)

                // Extraer inicial del día y fecha
                val dayInitial = if (showDayInitial) dataPoint.date?.dayOfWeek?.getDisplayName(java.time.format.TextStyle.NARROW, locale).orEmpty() else ""
                val dateStr = dataPoint.date?.format(shortDate).orEmpty()

                drawContext.canvas.nativeCanvas.drawText(
                    dayInitial,
                    x,
                    height - 25.dp.toPx(),
                    dayPaint
                )

                drawContext.canvas.nativeCanvas.drawText(
                    dateStr,
                    x,
                    height - 10.dp.toPx(),
                    datePaint
                )
            }
        }

        // 7. Tooltip para punto seleccionado
        selectedPointIndex?.let { index ->
            if (index in data.indices) {
                val point = points[index]
                val dataPoint = data[index]

                val valueText = formatStatisticsNumber(dataPoint.valueInUnit, locale) + " " + unit
                val dateText = dataPoint.date?.format(shortDate).orEmpty()

                val tooltipPaint = android.graphics.Paint().apply {
                    color = tooltipTextColor
                    textSize = 12.sp.toPx()
                    textAlign = android.graphics.Paint.Align.CENTER
                    isAntiAlias = true
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }

                val padding = 10.dp.toPx()
                val tooltipWidth = maxOf(
                    tooltipPaint.measureText(valueText),
                    tooltipPaint.measureText(dateText)
                ) + padding * 2
                val tooltipHeight = 44.dp.toPx()

                val tooltipX = point.x.coerceIn(
                    leftPadding + tooltipWidth / 2,
                    width - tooltipWidth / 2
                )
                val tooltipY = (point.y - 15.dp.toPx() - tooltipHeight).coerceAtLeast(0f)

                // Fondo del tooltip
                drawRoundRect(
                    color = tooltipBgColor,
                    topLeft = Offset(tooltipX - tooltipWidth / 2, tooltipY),
                    size = Size(tooltipWidth, tooltipHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                )

                // Texto del tooltip
                drawContext.canvas.nativeCanvas.drawText(
                    dateText,
                    tooltipX,
                    tooltipY + padding + 12.dp.toPx(),
                    tooltipPaint
                )
                drawContext.canvas.nativeCanvas.drawText(
                    valueText,
                    tooltipX,
                    tooltipY + tooltipHeight - padding,
                    tooltipPaint
                )
            }
        }
    }
}
