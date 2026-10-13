package com.d4vram.pillmate2.ui.components

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.utils.relativeDayLabel

/**
 * 📜 Item del historial de medicamentos tomados
 */
@Composable
fun HistoryItem(
    historyEntry: HistoryEntry,
    isDrops: Boolean = false,
    showNotesIndicator: Boolean = false,
    hasAudioNote: Boolean = false,
    onShowStats: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dropQuantityDescription = if (isDrops) {
        pluralStringResource(
            R.plurals.history_drops_accessibility,
            historyEntry.quantity,
            historyEntry.quantity
        )
    } else {
        null
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono del medicamento
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(historyEntry.color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = historyEntry.emoji,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Información del medicamento
        Column(
            modifier = Modifier.weight(1f)
        ) {
            val title = listOfNotNull(
                historyEntry.medicationName,
                historyEntry.dose?.takeIf { it.isNotBlank() }
            ).joinToString(separator = " • ")
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${relativeDayLabel(historyEntry.timestamp.toLocalDate())} ${historyEntry.time}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Indicadores
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isDrops || historyEntry.quantity > 1) {
                val quantityModifier = Modifier
                    .padding(horizontal = 8.dp, vertical = 4.dp)
                    .then(
                        dropQuantityDescription?.let { description ->
                            Modifier.semantics { contentDescription = description }
                        } ?: Modifier
                    )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Text(
                        text = historyQuantityBadgeText(historyEntry.quantity, isDrops),
                        modifier = quantityModifier,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp
                        ),
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Indicador de notas si las hay
            if (showNotesIndicator || hasAudioNote) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = if (hasAudioNote) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    }
                ) {
                    Text(
                        text = if (hasAudioNote) "🎤" else "📝",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 10.sp
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // --- NUEVO BOTÓN DE ESTADÍSTICAS ---
            IconButton(
                onClick = { onShowStats(historyEntry.medicationId) },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.BarChart,
                    contentDescription = stringResource(R.string.history_view_stats),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            
            // Badge de "Tomada"
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFFD1FAE5)
            ) {
                Text(
                    text = stringResource(R.string.entry_taken),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp
                    ),
                    color = Color(0xFF065F46)
                )
            }
        }
    }
}
