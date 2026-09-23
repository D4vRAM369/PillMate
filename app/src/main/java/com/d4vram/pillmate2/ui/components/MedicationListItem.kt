package com.d4vram.pillmate2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.pillmate2.model.Medication
import java.time.LocalDateTime

/**
 * Fila de medicamento para vista lista (alternativa al grid cuadrado).
 * Muestra franja de color, emoji, nombre/dosis/horario y menú de opciones.
 */
@Composable
fun MedicationListItem(
    medication: Medication,
    repository: com.d4vram.pillmate2.data.MedicationRepository,
    chipCountPeriod: com.d4vram.pillmate2.ui.theme.ChipCountPeriod,
    onTakeMedication: (String, Int, LocalDateTime?) -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    var showQuantityDialog by remember { mutableStateOf(false) }
    val historyEntries by repository.historyEntries.collectAsState()
    val takenCount = remember(medication.id, chipCountPeriod, historyEntries) {
        repository.calculateTakenCount(medication.id, medication.name, chipCountPeriod)
    }
    val isTaken = takenCount > 0

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Registrar toma de ${medication.name}") { showQuantityDialog = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Franja de color izquierda
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                    .background(medication.color)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Emoji del medicamento
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(medication.color.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = medication.emoji,
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Nombre + dosis + horario
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = buildString {
                        append(medication.dose)
                        if (!medication.scheduleTime.isNullOrBlank()) append(" · ${medication.scheduleTime}")
                        if (medication.type.isNotBlank()) append(" · ${medication.type}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Badge contador si ya se tomó
            if (isTaken) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(100.dp))
                        .background(medication.color.copy(alpha = 0.15f))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$takenCount",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = medication.color
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }

            // Menú de opciones
            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Opciones de ${medication.name}",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Editar") },
                        onClick = { onEditMedication(medication.id); showMenu = false },
                        leadingIcon = {
                            Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Eliminar") },
                        onClick = { onDeleteMedication(medication.id); showMenu = false },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error)
                        }
                    )
                }
            }
        }
    }

    if (showQuantityDialog) {
        val family = remember(medication.id) { repository.getMedicationFamily(medication.id) }
        TakeMedicationDialog(
            medication = medication,
            family = family,
            onDismiss = { showQuantityDialog = false },
            onConfirm = { finalId, qty, ts ->
                onTakeMedication(finalId, qty, ts)
                showQuantityDialog = false
            }
        )
    }
}
