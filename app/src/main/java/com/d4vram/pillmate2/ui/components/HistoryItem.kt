package com.d4vram.pillmate2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.pillmate2.model.HistoryEntry

/**
 * 📜 Item del historial de medicamentos tomados
 */
@Composable
fun HistoryItem(
    historyEntry: HistoryEntry,
    modifier: Modifier = Modifier
) {
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
            Text(
                text = historyEntry.medicationName,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${historyEntry.date} ${historyEntry.time}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Badge de "Tomada"
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFFD1FAE5)
        ) {
            Text(
                text = "Tomada",
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