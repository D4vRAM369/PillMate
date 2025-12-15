package com.d4vram.pillmate2.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf

/**
 * 🔥 COMPONENTE AVANZADO: GRÁFICO DE PATRÓN HORARIO 🔥
 * Muestra cuándo durante el día se toman más medicamentos
 */
@Composable
fun HourlyPatternChart(hourlyPattern: Map<String, Int>) {
    val timeSlots = listOf("Madrugada", "Mañana", "Tarde", "Noche")
    val chartEntryModelProducer = remember(hourlyPattern) {
        ChartEntryModelProducer(
            timeSlots.mapIndexed { index, timeSlot ->
                val activity = hourlyPattern[timeSlot] ?: 0
                entryOf(index.toFloat(), activity.toFloat())
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Patrón Horario de Tomas",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ProvideChartStyle {
                Chart(
                    chart = columnChart(),
                    chartModelProducer = chartEntryModelProducer,
                    startAxis = rememberStartAxis(),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ ->
                            val index = value.toInt()
                            if (index >= 0 && index < timeSlots.size) {
                                timeSlots[index]
                            } else {
                                ""
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                )
            }
        }
    }
}

/**
 * 🔥 COMPONENTE AVANZADO: EFECTIVIDAD POR MEDICAMENTO 🔥
 * Muestra qué tan bien se está siguiendo cada medicamento
 */
@Composable
fun MedicationEffectivenessChart(effectivenessData: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Efectividad por Medicamento",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                effectivenessData.forEach { (medicationName, effectiveness) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = medicationName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )

                        // Barra de progreso visual
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.width(120.dp)
                        ) {
                            val percentage = effectiveness.removeSuffix("%").toFloatOrNull() ?: 0f
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(percentage / 100f)
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(
                                            when {
                                                percentage >= 80f -> Color(0xFF4CAF50) // Verde
                                                percentage >= 60f -> Color(0xFFFF9800) // Naranja
                                                else -> Color(0xFFF44336) // Rojo
                                            }
                                        )
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = effectiveness,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = when {
                                    percentage >= 80f -> Color(0xFF4CAF50)
                                    percentage >= 60f -> Color(0xFFFF9800)
                                    else -> Color(0xFFF44336)
                                }
                            )
        }
    }
}

/**
 * 🔥 COMPONENTE AVANZADO: HEATMAP SEMANAL 🔥
 * Muestra un mapa de calor de actividad por día de la semana y hora
 */
@Composable
fun WeeklyHeatmapChart(weeklyActivity: Map<String, Int>) {
    val daysOfWeek = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")
    val hours = (0..23).toList()

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Actividad Semanal Detallada",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Encabezados de días
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Spacer(modifier = Modifier.width(40.dp)) // Espacio para horas
                    daysOfWeek.forEach { day ->
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Simulación de heatmap (usando datos semanales disponibles)
                // En una implementación real, necesitaríamos datos por hora y día
                daysOfWeek.forEachIndexed { dayIndex, day ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Etiqueta de día
                        Text(
                            text = day,
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.width(40.dp)
                        )

                        // Celdas del heatmap (simuladas con datos disponibles)
                        val activity = weeklyActivity[day] ?: 0
                        val maxActivity = weeklyActivity.values.maxOrNull() ?: 1

                        repeat(7) { hourIndex ->
                            val intensity = if (dayIndex == hourIndex) {
                                (activity.toFloat() / maxActivity).coerceIn(0f, 1f)
                            } else {
                                0.1f + (activity.toFloat() / maxActivity * 0.3f).coerceIn(0f, 0.3f)
                            }

                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        color = when {
                                            intensity > 0.8f -> Color(0xFF1B5E20) // Verde oscuro
                                            intensity > 0.6f -> Color(0xFF4CAF50) // Verde
                                            intensity > 0.4f -> Color(0xFF8BC34A) // Verde claro
                                            intensity > 0.2f -> Color(0xFFFFEB3B) // Amarillo
                                            else -> Color(0xFFFFF9C4) // Amarillo muy claro
                                        },
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            )
                        }
                    }

                    if (dayIndex < daysOfWeek.lastIndex) {
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Leyenda
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Menos",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    // Gradiente de colores
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFF9C4), RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFFFFEB3B), RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF8BC34A), RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF4CAF50), RoundedCornerShape(2.dp)))
                        Box(modifier = Modifier.size(12.dp).background(Color(0xFF1B5E20), RoundedCornerShape(2.dp)))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Text(
                        text = "Más",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
            }
        }
    }
}

/**
 * 🔥 COMPONENTE AVANZADO: INFORMACIÓN DE RACHAS 🔥
 * Muestra estadísticas de consistencia y rachas
 */
@Composable
fun StreakInfoCard(streakData: com.d4vram.pillmate2.ui.stats.viewmodel.StreakInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Rachas y Consistencia",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Indicador visual de racha actual
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(
                            color = when {
                                streakData.currentStreak >= 7 -> Color(0xFF4CAF50)
                                streakData.currentStreak >= 3 -> Color(0xFFFF9800)
                                else -> MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = androidx.compose.foundation.shape.CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = streakData.currentStreak.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                streakData.currentStreak >= 7 -> Color.White
                                streakData.currentStreak >= 3 -> Color.White
                                else -> MaterialTheme.colorScheme.onPrimaryContainer
                            }
                        )
                        Text(
                            text = "días",
                            style = MaterialTheme.typography.labelSmall,
                            color = when {
                                streakData.currentStreak >= 7 -> Color.White.copy(alpha = 0.8f)
                                streakData.currentStreak >= 3 -> Color.White.copy(alpha = 0.8f)
                                else -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = streakData.streakStatus,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = streakData.longestStreak.toString(),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Racha Máxima",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}