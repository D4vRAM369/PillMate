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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.d4vram.pillmate2.model.Medication
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.Calendar

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
    onTakeMedication: (String, Int, LocalDateTime?) -> Unit,  // Añadido: timestamp opcional
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
        .shadow(
            elevation = if (isTakenToday) 4.dp else 2.dp,
            shape = MaterialTheme.shapes.medium,
            ambientColor = if (isTakenToday) medication.color.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f),
            spotColor = if (isTakenToday) medication.color.copy(alpha = 0.2f) else Color.Black.copy(alpha = 0.1f)
        )

    Card(
        modifier = combinedModifier
            .scale(scale)
            .clickable(enabled = enabled) {
                isPressed = true
                showQuantityDialog = true
            },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            // Fondo sólido blanco/surface
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        ),
        // Borde completo alrededor de la card cuando está tomado
        border = if (isTakenToday) {
            BorderStroke(
                width = 2.dp,
                color = medication.color  // Color sólido, sin transparencia
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

                // Icono de la pastilla o foto
                PillIcon(
                    emoji = medication.emoji,
                    color = medication.color,
                    isTaken = isTakenToday,
                    photoPath = medication.photoPath,
                    usePhotoInstead = medication.usePhotoInstead
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
        val family = remember(medication.id) { repository.getMedicationFamily(medication.id) }
        TakeMedicationDialog(
            medication = medication,
            family = family,
            onDismiss = { showQuantityDialog = false },
            onConfirm = { finalId, quantity, timestamp ->
                onTakeMedication(finalId, quantity, timestamp)
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

    // MODO ON-DEMAND: Solo mostramos la cantidad actual, nada de metas o PRN tags
    val countText = "$current"

    val takenBackgroundColor = when {
        isTaken -> Color(0xFF10B981)
        else -> Color(0xFFF3F4F6)
    }

    val takenTextColor = when {
        isTaken -> Color.White
        else -> Color(0xFF4B5563)
    }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Solo mostramos el contador de tomas
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = takenBackgroundColor
        ) {
            Text(
                text = countText,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                ),
                color = takenTextColor
            )
        }
    }
}

/**
 * 💊 Icono visual de la pastilla con check si se tomó
 * 📸 Muestra foto si está configurado usePhotoInstead
 */
@Composable
private fun PillIcon(
    emoji: String,
    color: Color,
    isTaken: Boolean,
    photoPath: String? = null,
    usePhotoInstead: Boolean = false,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.size(48.dp),
        contentAlignment = Alignment.Center
    ) {
        // Mostrar foto si está configurado y existe
        if (usePhotoInstead && !photoPath.isNullOrEmpty() && File(photoPath).exists()) {
            // Foto del medicamento
            AsyncImage(
                model = photoPath,
                contentDescription = "Foto del medicamento",
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape),
                contentScale = ContentScale.Crop
            )
        } else {
            // Círculo de fondo con emoji (comportamiento original)
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
        }

        // Check verde si se tomó (siempre visible)
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

/**
 * 🕐 Dialog para registrar toma de medicamento
 * Incluye selector de dosis (si hay familia), hora y cantidad
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TakeMedicationDialog(
    medication: Medication,
    family: List<Medication>,
    onDismiss: () -> Unit,
    onConfirm: (String, Int, LocalDateTime?) -> Unit  // Añadido: timestamp
) {
    var selectedMedication by remember { mutableStateOf(medication) }
    var quantity by remember { mutableStateOf(1) }

    // Estado para el selector de hora
    var useCurrentTime by remember { mutableStateOf(true) }
    val currentTime = remember { Calendar.getInstance() }
    var selectedHour by remember { mutableStateOf(currentTime.get(Calendar.HOUR_OF_DAY)) }
    var selectedMinute by remember { mutableStateOf(currentTime.get(Calendar.MINUTE)) }
    var showTimePicker by remember { mutableStateOf(false) }

    // Formatear la hora seleccionada para mostrar
    val formattedTime = remember(selectedHour, selectedMinute) {
        String.format("%02d:%02d", selectedHour, selectedMinute)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 6.dp,
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Column {
                Text(
                    text = "Registrar toma",
                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "¿Qué dosis de ${medication.name} vas a registrar?",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // Selector de dosis (si hay familia)
                if (family.size > 1) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Selecciona la dosis:",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            family.forEach { med ->
                                val isSelected = selectedMedication.id == med.id
                                FilterChip(
                                    selected = isSelected,
                                    onClick = { selectedMedication = med },
                                    label = { Text(med.dose) },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = med.color.copy(alpha = 0.2f),
                                        selectedLabelColor = med.color,
                                        selectedLeadingIconColor = med.color
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = isSelected,
                                        borderColor = if(isSelected) med.color else MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                                        selectedBorderColor = med.color,
                                        borderWidth = 1.dp,
                                        selectedBorderWidth = 2.dp
                                    )
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }

                // ⏰ Selector de hora
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Hora de la toma:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Chip "Ahora"
                        FilterChip(
                            selected = useCurrentTime,
                            onClick = { useCurrentTime = true },
                            label = { Text("Ahora") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        )

                        // Chip para hora personalizada
                        FilterChip(
                            selected = !useCurrentTime,
                            onClick = {
                                useCurrentTime = false
                                showTimePicker = true
                            },
                            label = {
                                Text(if (!useCurrentTime) formattedTime else "Otra hora")
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                // Selector de cantidad
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Cantidad de unidades",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilledIconButton(
                            onClick = { if (quantity > 1) quantity-- },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        ) {
                            Text("-", style = MaterialTheme.typography.headlineSmall)
                        }

                        Text(
                            text = quantity.toString(),
                            style = MaterialTheme.typography.displaySmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )

                        FilledIconButton(
                            onClick = { quantity = (quantity + 1).coerceAtMost(999) },
                            modifier = Modifier.size(48.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = selectedMedication.color.copy(alpha = 0.8f),
                                contentColor = Color.White
                            )
                        ) {
                            Text("+", style = MaterialTheme.typography.headlineSmall)
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        listOf(1, 2, 5).forEach { quick ->
                            SuggestionChip(
                                onClick = { quantity = (quantity + quick).coerceAtMost(999) },
                                label = { Text("+${quick}") },
                                colors = SuggestionChipDefaults.suggestionChipColors(
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            // Calcular si el color es claro para usar texto con buen contraste
            val buttonColor = selectedMedication.color
            val luminance = (0.299f * buttonColor.red + 0.587f * buttonColor.green + 0.114f * buttonColor.blue)
            val textColor = if (luminance > 0.5f) Color.Black else Color.White

            Button(
                onClick = {
                    // Crear timestamp si se seleccionó hora personalizada
                    val timestamp = if (useCurrentTime) {
                        null  // null = usar hora actual en el repository
                    } else {
                        LocalDateTime.now()
                            .withHour(selectedHour)
                            .withMinute(selectedMinute)
                    }
                    onConfirm(selectedMedication.id, quantity.coerceAtLeast(1), timestamp)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonColor,
                    contentColor = textColor
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp)
            ) {
                Text("Registrar", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Cancelar", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    )

    // TimePicker Dialog
    if (showTimePicker) {
        TimePickerDialog(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            onDismiss = { showTimePicker = false },
            onConfirm = { hour, minute ->
                selectedHour = hour
                selectedMinute = minute
                showTimePicker = false
            }
        )
    }
}

/**
 * 🕐 Dialog con TimePicker de Material3
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialHour,
        initialMinute = initialMinute,
        is24Hour = true
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = {
            Text(
                text = "Seleccionar hora",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TimePicker(state = timePickerState)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(timePickerState.hour, timePickerState.minute) }
            ) {
                Text("Aceptar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}
