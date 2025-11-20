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
    data: List<Pair<String, Float>>,
    title: String = "Adherencia Semanal (Canvas Custom)",
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

            val primaryColor = MaterialTheme.colorScheme.primary
            val secondaryColor = MaterialTheme.colorScheme.secondary
            val axisColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            val labelColor = MaterialTheme.colorScheme.onSurface.toArgb()
            val highlightColor = MaterialTheme.colorScheme.tertiary

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp) // Aumentamos altura para asegurar espacio
                    .pointerInput(data) {
                        detectTapGestures { tapOffset ->
                            val width = size.width
                            // Recalculamos dimensiones para encontrar la barra tocada
                            val barWidth = (width / (data.size * 2f)).coerceAtMost(100f)
                            val spacing = (width - (barWidth * data.size)) / (data.size + 1)
                            
                            data.forEachIndexed { index, item ->
                                val x = spacing + (index * (barWidth + spacing))
                                if (tapOffset.x >= x && tapOffset.x <= x + barWidth) {
                                    selectedBarIndex = index
                                    Toast.makeText(
                                        context, 
                                        "${item.first}: ${item.second.toInt()}%", 
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        }
                    }
            ) {
                // Dimensiones útiles
                val width = size.width
                val height = size.height
                val bottomPadding = 40.dp.toPx() // Espacio reservado para etiquetas
                val chartHeight = height - bottomPadding
                
                val barWidth = (width / (data.size * 2f)).coerceAtMost(100f) // Ancho dinámico
                val spacing = (width - (barWidth * data.size)) / (data.size + 1)
                
                // 1. Dibujar líneas de guía (Grid)
                val gridLines = 5
                val stepHeight = chartHeight / gridLines
                
                for (i in 0..gridLines) {
                    val y = stepHeight * i
                    drawLine(
                        color = axisColor.copy(alpha = 0.2f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // 2. Dibujar barras
                data.forEachIndexed { index, (day, percentage) ->
                    val x = spacing + (index * (barWidth + spacing))
                    
                    // Altura objetivo basada en el porcentaje (0-100)
                    val targetBarHeight = (percentage / 100f) * chartHeight
                    
                    // Aplicar animación
                    val currentBarHeight = targetBarHeight * animationProgress.value
                    
                    val barTop = chartHeight - currentBarHeight
                    
                    // Color dinámico: Highlight si está seleccionado
                    val isSelected = selectedBarIndex == index
                    val baseColor = if (isSelected) highlightColor else if (percentage >= 80f) primaryColor else secondaryColor
                    
                    // Gradiente para efecto "premium"
                    val brush = Brush.verticalGradient(
                        colors = listOf(
                            baseColor,
                            baseColor.copy(alpha = 0.6f)
                        ),
                        startY = barTop,
                        endY = chartHeight
                    )

                    drawRoundRect(
                        brush = brush,
                        topLeft = Offset(x, barTop),
                        size = Size(barWidth, currentBarHeight),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                    )

                    // 3. Dibujar etiquetas (Texto)
                    drawContext.canvas.nativeCanvas.apply {
                        val paint = android.graphics.Paint().apply {
                            color = labelColor
                            textSize = 12.sp.toPx()
                            textAlign = android.graphics.Paint.Align.CENTER
                            isAntiAlias = true
                        }
                        
                        // Etiqueta del día (debajo de la barra, en el espacio reservado)
                        drawText(
                            day,
                            x + barWidth / 2,
                            chartHeight + 20.dp.toPx(), 
                            paint
                        )
                        
                        // Etiqueta del porcentaje (encima de la barra, si la animación terminó)
                        if (animationProgress.value > 0.9f || isSelected) {
                            drawText(
                                "${percentage.toInt()}%",
                                x + barWidth / 2,
                                barTop - 5.dp.toPx(),
                                paint
                            )
                        }
                    }
                }
            }
        }
    }
}
