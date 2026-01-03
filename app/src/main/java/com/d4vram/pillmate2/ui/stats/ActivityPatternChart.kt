package com.d4vram.pillmate2.ui.stats

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 📊 Gráfica de Patrones de Actividad
 * 
 * Reemplaza a la antigua gráfica de adherencia.
 * Muestra la distribución temporal de registros sin juzgar si es "suficiente" o no.
 */
@Composable
fun ActivityPatternChart(
    data: List<ActivityDataPoint>,
    title: String = "Patrón de Actividad",
    barColor: Color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
    modifier: Modifier = Modifier,
    tooltipColor: Color = MaterialTheme.colorScheme.tertiaryContainer
) {
    val context = LocalContext.current
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            if (data.all { it.totalValue == 0f }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = androidx.compose.ui.Alignment.Center
                ) {
                    Text(
                        text = "Aún no hay suficiente actividad en este periodo para detectar patrones.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(32.dp)
                    )
                }
            } else {
                // Estado de animación
                val animationProgress = remember { Animatable(0f) }

                LaunchedEffect(Unit) {
                    animationProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(1000, easing = FastOutSlowInEasing)
                    )
                }

                val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
                val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
                val highlightColor = MaterialTheme.colorScheme.primary

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .pointerInput(data) {
                            detectTapGestures { tapOffset ->
                                val width = size.width
                                val leftPadding = 50.dp.toPx()
                                val chartWidth = width - leftPadding
                                
                                val barWidth = (chartWidth / (data.size * 2f)).coerceAtMost(100f)
                                val spacing = (chartWidth - (barWidth * data.size)) / (data.size + 1)
                                
                                data.forEachIndexed { index, item ->
                                    val x = leftPadding + spacing + (index * (barWidth + spacing))
                                    if (tapOffset.x >= x && tapOffset.x <= x + barWidth) {
                                        selectedBarIndex = index
                                    }
                                }
                            }
                        }
                ) {
                    val width = size.width
                    val height = size.height
                    val bottomPadding = 30.dp.toPx()
                    val leftPadding = 50.dp.toPx()
                    
                    val chartHeight = height - bottomPadding
                    val chartWidth = width - leftPadding
                    
                    val maxDataValue = data.maxOfOrNull { it.totalValue } ?: 1f
                    val yAxisMax = maxDataValue.coerceAtLeast(1f) * 1.2f

                    val barWidth = (chartWidth / (data.size * 2f)).coerceAtMost(100f)
                    val spacing = (chartWidth - (barWidth * data.size)) / (data.size + 1)
                    
                    val textPaint = android.graphics.Paint().apply {
                        color = labelColor
                        textSize = 10.sp.toPx()
                        textAlign = android.graphics.Paint.Align.RIGHT
                        isAntiAlias = true
                    }

                    // 1. Grid
                    val gridLines = 4
                    val dashEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                    
                    for (i in 0..gridLines) {
                        val fraction = i.toFloat() / gridLines
                        val y = chartHeight - (chartHeight * fraction)
                        val value = yAxisMax * fraction
                        
                        drawLine(
                            color = axisColor,
                            start = Offset(leftPadding, y),
                            end = Offset(width, y),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashEffect
                        )
                        
                        drawContext.canvas.nativeCanvas.drawText(
                            "${value.toInt()}",
                            leftPadding - 8.dp.toPx(),
                            y + 4.dp.toPx(),
                            textPaint
                        )
                    }

                    // 2. Barras
                    data.forEachIndexed { index, item ->
                        val x = leftPadding + spacing + (index * (barWidth + spacing))
                        val targetBarHeight = (item.totalValue / yAxisMax) * chartHeight
                        val currentBarHeight = targetBarHeight * animationProgress.value
                        val barTop = chartHeight - currentBarHeight
                        
                        val isSelected = selectedBarIndex == index
                        val baseColorToUse = if (isSelected) highlightColor else barColor
                        
                        val brush = Brush.verticalGradient(
                            colors = listOf(baseColorToUse, baseColorToUse.copy(alpha = 0.5f)),
                            startY = barTop,
                            endY = chartHeight
                        )

                        if (currentBarHeight > 0) {
                            val barPath = androidx.compose.ui.graphics.Path().apply {
                                addRoundRect(
                                    androidx.compose.ui.geometry.RoundRect(
                                        left = x,
                                        top = barTop,
                                        right = x + barWidth,
                                        bottom = chartHeight,
                                        topLeftCornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                                        topRightCornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                                    )
                                )
                            }
                            drawPath(path = barPath, brush = brush)
                        }

                        // 3. Eje X
                        val dayPaint = android.graphics.Paint().apply {
                            color = labelColor
                            textSize = 10.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                            isFakeBoldText = isSelected
                        }

                        val skipInterval = if (data.size > 10) (data.size / 6).coerceAtLeast(1) else 1
                        if (index % skipInterval == 0 || isSelected) {
                            drawContext.canvas.nativeCanvas.drawText(
                                item.dayName,
                                x + barWidth / 2,
                                height - 5.dp.toPx(), 
                                dayPaint
                            )
                        }
                        
                        // 4. Tooltip
                        if (isSelected) {
                            val valueText = "${item.totalValue.toInt()} ${item.unit}"
                            val tooltipPaint = android.graphics.Paint().apply {
                                color = android.graphics.Color.BLACK
                                textSize = 12.sp.toPx()
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                                typeface = android.graphics.Typeface.DEFAULT_BOLD
                            }
                            
                            val dateText = item.fullDate
                            val dateWidth = tooltipPaint.measureText(dateText)
                            val valueWidth = tooltipPaint.measureText(valueText)
                            val maxWidth = maxOf(dateWidth, valueWidth)
                            
                            val padding = 12.dp.toPx()
                            val tWidth = maxWidth + (padding * 2)
                            val tHeight = 44.dp.toPx()
                            
                            val tX = x + barWidth / 2
                            val tY = barTop - 10.dp.toPx()
                            
                            drawRoundRect(
                                color = tooltipColor,
                                topLeft = Offset(tX - tWidth / 2, tY - tHeight),
                                size = Size(tWidth, tHeight),
                                cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                            )
                            
                            drawContext.canvas.nativeCanvas.drawText(
                                dateText,
                                tX,
                                tY - tHeight + padding + 10.dp.toPx(),
                                tooltipPaint
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                valueText,
                                tX,
                                tY - padding,
                                tooltipPaint
                            )
                        }
                    }
                }
            }
        }
    }
}
