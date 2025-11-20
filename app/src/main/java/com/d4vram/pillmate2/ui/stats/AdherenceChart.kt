package com.d4vram.pillmate2.ui.stats

import android.widget.Toast
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 * 🎨 Gráfica de Adherencia Personalizada (Canvas)
 * 
 * Este componente demuestra cómo dibujar gráficos desde cero sin librerías externas.
 * Conceptos clave:
 * 1. Canvas: El lienzo de dibujo.
 * 2. Geometry: Cálculos de coordenadas (x, y) basados en el tamaño del componente.
 * 3. Animation: Animación de entrada para las barras.
 * 4. Paint: Uso de Paint nativo para dibujar texto.
 * 5. Interactivity: Detección de toques en el Canvas.
 */
@Composable
fun AdherenceChart(
    data: List<AdherenceDataPoint>,
    title: String = "Adherencia Semanal (Canvas Custom)",
    barColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedBarIndex by remember { mutableStateOf<Int?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
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

            // Estado de animación: valor de 0f a 1f
            val animationProgress = remember { Animatable(0f) }

            // Iniciar animación al componer
            LaunchedEffect(Unit) {
                animationProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 1000,
                        easing = FastOutSlowInEasing
                    )
                )
            }

            val secondaryColor = MaterialTheme.colorScheme.secondary
            val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
            val highlightColor = MaterialTheme.colorScheme.tertiary

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    .pointerInput(data) {
                        detectTapGestures { tapOffset ->
                            val width = size.width
                            val leftPadding = 40.dp.toPx() // Espacio para eje Y
                            val chartWidth = width - leftPadding
                            
                            val barWidth = (chartWidth / (data.size * 2f)).coerceAtMost(100f)
                            val spacing = (chartWidth - (barWidth * data.size)) / (data.size + 1)
                            
                            data.forEachIndexed { index, item ->
                                val x = leftPadding + spacing + (index * (barWidth + spacing))
                                if (tapOffset.x >= x && tapOffset.x <= x + barWidth) {
                                    selectedBarIndex = index
                                    val valueText = if (item.unit == "tomas") 
                                        "${item.taken}/${item.goal} tomas" 
                                    else 
                                        "${item.totalValue.toInt()}${item.unit} (${item.taken} tomas)"
                                        
                                    Toast.makeText(context, "${item.dayName}: $valueText", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
            ) {
                val width = size.width
                val height = size.height
                val bottomPadding = 30.dp.toPx()
                val leftPadding = 50.dp.toPx() // Espacio para etiquetas Eje Y
                
                val chartHeight = height - bottomPadding
                val chartWidth = width - leftPadding
                
                // Calcular valor máximo para la escala
                val maxDataValue = data.maxOfOrNull { it.totalValue } ?: 0f
                // Asegurar que el máximo sea al menos la meta diaria promedio si no hay datos, o un mínimo
                val yAxisMax = maxDataValue.coerceAtLeast(1f) * 1.2f // 20% de margen superior

                val barWidth = (chartWidth / (data.size * 2f)).coerceAtMost(100f)
                val spacing = (chartWidth - (barWidth * data.size)) / (data.size + 1)
                
                val textPaint = android.graphics.Paint().apply {
                    color = labelColor
                    textSize = 10.sp.toPx()
                    textAlign = android.graphics.Paint.Align.RIGHT
                    isAntiAlias = true
                }

                // 1. Dibujar Eje Y y Líneas de Grid
                val gridLines = 4
                for (i in 0..gridLines) {
                    val fraction = i.toFloat() / gridLines
                    val y = chartHeight - (chartHeight * fraction)
                    val value = yAxisMax * fraction
                    
                    // Línea horizontal
                    drawLine(
                        color = axisColor.copy(alpha = 0.2f),
                        start = Offset(leftPadding, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                    
                    // Etiqueta Eje Y
                    drawContext.canvas.nativeCanvas.drawText(
                        "${value.toInt()}",
                        leftPadding - 8.dp.toPx(),
                        y + 4.dp.toPx(), // Centrar verticalmente
                        textPaint
                    )
                }
                
                // Unidad en la parte inferior del eje Y (para evitar solapamiento arriba)
                val unit = data.firstOrNull { it.unit.isNotEmpty() }?.unit ?: ""
                if (unit.isNotEmpty()) {
                     drawContext.canvas.nativeCanvas.drawText(
                        unit,
                        leftPadding - 8.dp.toPx(),
                        chartHeight + 20.dp.toPx(), // Alineado con las etiquetas de los días
                        textPaint
                    )
                }

                // 2. Dibujar Barras
                data.forEachIndexed { index, item ->
                    val x = leftPadding + spacing + (index * (barWidth + spacing))
                    
                    // Altura relativa al máximo del eje Y
                    val targetBarHeight = (item.totalValue / yAxisMax) * chartHeight
                    val currentBarHeight = targetBarHeight * animationProgress.value
                    
                    val barTop = chartHeight - currentBarHeight
                    
                    val isSelected = selectedBarIndex == index
                    val baseColorToUse = if (isSelected) highlightColor else barColor
                    
                    val brush = Brush.verticalGradient(
                        colors = listOf(baseColorToUse, baseColorToUse.copy(alpha = 0.6f)),
                        startY = barTop,
                        endY = chartHeight
                    )

                    if (currentBarHeight > 0) {
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(x, barTop),
                            size = Size(barWidth, currentBarHeight),
                            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                        )
                    }

                    // 3. Etiquetas Eje X (Días)
                    val dayPaint = android.graphics.Paint().apply {
                        color = labelColor
                        textSize = 12.sp.toPx()
                        textAlign = android.graphics.Paint.Align.CENTER
                        isAntiAlias = true
                    }
                    
                    drawContext.canvas.nativeCanvas.drawText(
                        item.dayName,
                        x + barWidth / 2,
                        height - 5.dp.toPx(), 
                        dayPaint
                    )
                }
            }
        }
    }
}
