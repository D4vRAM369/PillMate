package com.d4vram.pillmate2.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * 🔥 ALERTAS INTELIGENTES BASADAS EN PATRONES 🔥
 * Muestra recomendaciones y observaciones basadas en el análisis de datos
 */
@Composable
fun SmartAlertsCard(
    adherenceRate: String,
    currentStreak: Int,
    consistency: String,
    weeklyPattern: Map<String, Int>,
    medicationEffectiveness: List<Pair<String, String>>
) {
    val alerts = generateSmartAlerts(
        adherenceRate = adherenceRate,
        currentStreak = currentStreak,
        consistency = consistency,
        weeklyPattern = weeklyPattern,
        medicationEffectiveness = medicationEffectiveness
    )

    if (alerts.isNotEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Recomendaciones Inteligentes",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )

            alerts.forEach { alert ->
                AlertCard(alert = alert)
            }
        }
    }
}

@Composable
private fun AlertCard(alert: SmartAlert) {
    val (backgroundColor, icon, iconTint) = when (alert.type) {
        AlertType.SUCCESS -> Triple(
            Color(0xFFE8F5E8),
            Icons.Default.Info,
            Color(0xFF4CAF50)
        )
        AlertType.WARNING -> Triple(
            Color(0xFFFFF8E1),
            Icons.Default.Warning,
            Color(0xFFFF9800)
        )
        AlertType.INFO -> Triple(
            Color(0xFFE3F2FD),
            Icons.Default.Info,
            Color(0xFF2196F3)
        )
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = backgroundColor
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = alert.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = iconTint
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = alert.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun generateSmartAlerts(
    adherenceRate: String,
    currentStreak: Int,
    consistency: String,
    weeklyPattern: Map<String, Int>,
    medicationEffectiveness: List<Pair<String, String>>
): List<SmartAlert> {
    val alerts = mutableListOf<SmartAlert>()

    // Análisis de adherencia
    val adherenceValue = adherenceRate.removeSuffix("%").toFloatOrNull() ?: 0f
    when {
        adherenceValue >= 90f -> {
            alerts.add(SmartAlert(
                type = AlertType.SUCCESS,
                title = "¡Excelente adherencia!",
                message = "Tu consistencia es admirable. ¡Sigue así!"
            ))
        }
        adherenceValue >= 70f -> {
            alerts.add(SmartAlert(
                type = AlertType.INFO,
                title = "Buena adherencia",
                message = "Vas por buen camino. Considera configurar recordatorios para mejorar aún más."
            ))
        }
        adherenceValue < 70f -> {
            alerts.add(SmartAlert(
                type = AlertType.WARNING,
                title = "Adherencia baja",
                message = "Considera configurar recordatorios o ajustar tu rutina para mejorar la consistencia."
            ))
        }
    }

    // Análisis de rachas
    when {
        currentStreak >= 7 -> {
            alerts.add(SmartAlert(
                type = AlertType.SUCCESS,
                title = "¡Racha impresionante!",
                message = "Llevas $currentStreak días consecutivos. ¡Estás haciendo un trabajo excepcional!"
            ))
        }
        currentStreak == 0 -> {
            alerts.add(SmartAlert(
                type = AlertType.INFO,
                title = "Comienza una racha",
                message = "No hay registros recientes. ¡Toma tu medicamento hoy para iniciar una nueva racha!"
            ))
        }
    }

    // Análisis de consistencia semanal
    val maxActivity = weeklyPattern.values.maxOrNull() ?: 0
    val minActivity = weeklyPattern.values.minOrNull() ?: 0
    if (maxActivity > 0 && minActivity >= 0) {
        val variability = if (maxActivity > 0) (maxActivity - minActivity).toFloat() / maxActivity else 0f
        if (variability > 0.5f) {
            val busiestDay = weeklyPattern.maxByOrNull { it.value }?.key ?: ""
            val slowestDay = weeklyPattern.minByOrNull { it.value }?.key ?: ""
            alerts.add(SmartAlert(
                type = AlertType.INFO,
                title = "Patrón semanal variable",
                message = "$busiestDay es tu día más activo. Considera ajustar la rutina en $slowestDay."
            ))
        }
    }

    // Análisis de efectividad por medicamento
    val lowEffectivenessMeds = medicationEffectiveness.filter { (_, rate) ->
        rate.removeSuffix("%").toFloatOrNull() ?: 100f < 70f
    }

    if (lowEffectivenessMeds.isNotEmpty()) {
        val medNames = lowEffectivenessMeds.joinToString(", ") { (name, _) -> name }
        alerts.add(SmartAlert(
            type = AlertType.WARNING,
            title = "Medicamentos con baja adherencia",
            message = "Considera revisar la rutina para: $medNames"
        ))
    }

    return alerts.take(3) // Limitar a 3 alertas para no sobrecargar
}

private data class SmartAlert(
    val type: AlertType,
    val title: String,
    val message: String
)

private enum class AlertType {
    SUCCESS, WARNING, INFO
}