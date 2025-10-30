package com.d4vram.pillmate2.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.pillmate2.model.Medication
import kotlinx.coroutines.delay

/**
 * 💊 Card de medicamento clickeable
 * El componente más importante: muestra cada medicamento como un tile
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationCard(
    medication: Medication,
    repository: com.d4vram.pillmate2.data.MedicationRepository,
    chipCountPeriod: com.d4vram.pillmate2.ui.theme.ChipCountPeriod,
    onTakeMedication: (String, Int) -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {},
    enabled: Boolean = true,
    dragModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    // Estado para la animación al hacer click
    var isPressed by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showQuantityDialog by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "card_scale"
    )

    // Calcular el contador según el período configurado
    val takenCount = remember(medication.id, medication.name, chipCountPeriod) {
        repository.calculateTakenCount(medication.id, medication.name, chipCountPeriod)
    }

    // Determinar si ya se tomó (según el período)
    val isTakenToday = takenCount > 0

    val combinedModifier = modifier

    Card(
        modifier = combinedModifier
            .scale(scale)
            .clickable(enabled = enabled) {
                isPressed = true
                showQuantityDialog = true
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isTakenToday) {
                medication.color.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isTakenToday) 8.dp else 4.dp
        ),
        border = if (isTakenToday) {
            BorderStroke(
                width = 1.dp,
                color = medication.color.copy(alpha = 0.4f)
            )
        } else null
    ) {
        // Efecto de reset de animación
        LaunchedEffect(isPressed) {
            if (isPressed) {
                delay(150)
                isPressed = false
            }
        }

        Box {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Contador + acciones superiores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CountBadge(
                        current = takenCount,
                        goal = medication.dailyGoal,
                        isPrn = medication.isPrn,
                        isTaken = isTakenToday,
                        chipCountPeriod = chipCountPeriod
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    Box(
                        modifier = dragModifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Reordenar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Box {
                        IconButton(
                            onClick = { showMenu = !showMenu },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Opciones",
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
                                onClick = {
                                    onEditMedication(medication.id)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Eliminar") },
                                onClick = {
                                    onDeleteMedication(medication.id)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Icono de la pastilla
                PillIcon(
                    emoji = medication.emoji,
                    color = medication.color,
                    isTaken = isTakenToday
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Nombre del medicamento
                Text(
                    text = medication.name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Dosis y tipo
                Text(
                    text = "${medication.dose} • ${medication.type}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Última toma (solo visible si ya se tomó)
                if (isTakenToday && medication.lastTaken != null) {
                    Text(
                        text = "Última: ${medication.lastTaken}",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontWeight = FontWeight.Medium
                        ),
                        color = medication.color
                    )
                }
            }
        }
    }

    if (showQuantityDialog) {
        TakeMedicationDialog(
            medication = medication,
            onDismiss = { showQuantityDialog = false },
            onConfirm = { quantity ->
                onTakeMedication(medication.id, quantity)
                showQuantityDialog = false
            }
        )
    }
}

/**
 * 🏷️ Badge que muestra el conteo de dosis tomadas
 */
@Composable
private fun CountBadge(
    current: Int,
    goal: Int,
    isPrn: Boolean,
    isTaken: Boolean,
    chipCountPeriod: com.d4vram.pillmate2.ui.theme.ChipCountPeriod,
    modifier: Modifier = Modifier
) {
    // Si está configurado para ocultar, no mostrar nada
    if (chipCountPeriod == com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Hidden) {
        return
    }

    val backgroundColor = when {
        isPrn -> Color(0xFFEF4444)
        isTaken -> Color(0xFF10B981)
        else -> Color(0xFFF3F4F6)
    }

    val textColor = when {
        isPrn || isTaken -> Color.White
        else -> Color(0xFF4B5563)
    }

    // Determinar qué texto mostrar según el período
    val displayText = when {
        isPrn -> "PRN"
        chipCountPeriod == com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Daily -> "$current/$goal"
        else -> current.toString()
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor
    ) {
        Text(
            text = displayText,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            ),
            color = textColor
        )
    }
}

/**
 * 💊 Icono visual de la pastilla con check si se tomó
 */
@Composable
private fun PillIcon(
    emoji: String,
    color: Color,
    isTaken: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Círculo de fondo
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = emoji,
                fontSize = 24.sp
            )
        }

        // Check verde si se tomó
        if (isTaken) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
                    .align(Alignment.TopEnd),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓",
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TakeMedicationDialog(
    medication: Medication,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var quantity by remember { mutableStateOf(1) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Registrar toma",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = "¿Cuántas unidades de ${medication.name}?",
                    style = MaterialTheme.typography.bodyMedium
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = medication.dose,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilledIconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Text("-")
                        }

                        Text(
                            text = quantity.toString(),
                            modifier = Modifier.padding(horizontal = 16.dp),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )

                        FilledIconButton(
                            onClick = { quantity = (quantity + 1).coerceAtMost(999) },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(
                                text = "+",
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(1, 2, 5).forEach { quick ->
                        AssistChip(
                            onClick = { quantity = (quantity + quick).coerceAtMost(999) },
                            label = { Text("+${quick}") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(quantity.coerceAtLeast(1)) }) {
                Text("Registrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
