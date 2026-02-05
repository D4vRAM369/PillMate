package com.d4vram.pillmate2.ui.stats

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
                            contentDescription = "Anterior",
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
                            contentDescription = "Siguiente",
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
                        text = "Sin datos en este periodo",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LineChartCanvas(
                    data = data,
                    unit = unit,
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

        val chartWidth = width - leftPadding
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
            val valueText = if (value == value.toInt().toFloat()) {
                value.toInt().toString()
            } else {
                String.format("%.1f", value)
            }
            drawContext.canvas.nativeCanvas.drawText(
                valueText,
                leftPadding - 8.dp.toPx(),
                y + 4.dp.toPx(),
                textPaint
            )
        }

        // Etiqueta de unidad en la esquina superior izquierda
        val unitPaint = android.graphics.Paint().apply {
            color = labelColor
            textSize = 11.sp.toPx()
            textAlign = android.graphics.Paint.Align.LEFT
            isAntiAlias = true
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        drawContext.canvas.nativeCanvas.drawText(
            unit,
            8.dp.toPx(),
            topPadding + 12.dp.toPx(),
            unitPaint
        )

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

        // Determinar intervalo de etiquetas según cantidad de datos
        val labelInterval = when {
            data.size <= 7 -> 1
            data.size <= 15 -> 2
            else -> 5
        }

        data.forEachIndexed { index, dataPoint ->
            if (index % labelInterval == 0 || index == data.lastIndex || selectedPointIndex == index) {
                val x = leftPadding + (index * pointSpacing)

                // Extraer inicial del día y fecha
                val dayInitial = getDayInitial(dataPoint.dayName)
                val dateStr = extractDateFromDayName(dataPoint.dayName, dataPoint.fullDate)

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

                val valueText = formatValue(dataPoint.valueInUnit) + " " + unit
                val dateText = dataPoint.fullDate.take(10) // Primeros caracteres de la fecha

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

/**
 * Obtiene la inicial del día en español
 */
private fun getDayInitial(dayName: String): String {
    val lower = dayName.lowercase()
    return when {
        lower.startsWith("lun") -> "L"
        lower.startsWith("mar") -> "M"
        lower.startsWith("mi") || lower.startsWith("mié") -> "X"
        lower.startsWith("jue") -> "J"
        lower.startsWith("vie") -> "V"
        lower.startsWith("sáb") || lower.startsWith("sab") -> "S"
        lower.startsWith("dom") -> "D"
        // Si es formato DD/MM, extraer la fecha para determinar el día
        lower.contains("/") -> {
            // Ya es formato fecha, devolver vacío para el día
            ""
        }
        else -> dayName.firstOrNull()?.uppercase() ?: ""
    }
}

/**
 * Extrae la fecha DD/MM del dayName o fullDate
 */
private fun extractDateFromDayName(dayName: String, fullDate: String): String {
    // Si dayName ya es DD/MM, usarlo directamente
    if (dayName.matches(Regex("\\d{2}/\\d{2}"))) {
        return dayName
    }

    // Intentar extraer de fullDate (formato "dd/MM d de MMMM")
    val regex = Regex("(\\d{2}/\\d{2})")
    val match = regex.find(fullDate)
    return match?.value ?: ""
}

private fun formatValue(value: Float): String {
    return if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}
