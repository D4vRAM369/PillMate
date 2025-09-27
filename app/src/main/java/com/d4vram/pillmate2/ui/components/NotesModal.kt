package com.d4vram.pillmate2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 📝 Modal para añadir y editar notas de entradas del historial
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesModal(
    historyEntry: HistoryEntry,
    onDismiss: () -> Unit,
    onSaveChanges: (String, String, LocalDateTime) -> Unit, // (entryId, notes, timestamp)
    onDeleteNotes: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val originalDateTime = remember(historyEntry.id) {
        historyEntry.timestamp.withSecond(0).withNano(0)
    }
    var notesText by remember(historyEntry.id) { mutableStateOf(historyEntry.notes) }
    var isEditing by remember(historyEntry.id) { mutableStateOf(historyEntry.notes.isEmpty()) }
    var selectedDateTime by remember(historyEntry.id) { mutableStateOf(originalDateTime) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val zoneId = remember { ZoneId.systemDefault() }
    val fullDateFormatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    val formattedDisplayDate = remember(selectedDateTime) { selectedDateTime.format(fullDateFormatter) }
    val formattedDisplayTime = remember(selectedDateTime) { selectedDateTime.format(timeFormatter) }
    val hasChanges = remember(notesText, selectedDateTime) {
        notesText != historyEntry.notes || selectedDateTime != originalDateTime
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // Header con información del medicamento
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Emoji y color del medicamento
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(androidx.compose.foundation.shape.CircleShape)
                                .background(historyEntry.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = historyEntry.emoji,
                                style = MaterialTheme.typography.titleLarge
                            )
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Column {
                            Text(
                                text = historyEntry.medicationName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${historyEntry.date} • ${historyEntry.time}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (historyEntry.quantity > 1) {
                                Text(
                                    text = "Cantidad registrada: x${historyEntry.quantity}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar"
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Sección fecha/hora
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⏰ Momento de la toma",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (!isEditing) {
                                TextButton(onClick = { isEditing = true }) {
                                    Text("Editar")
                                }
                            }
                        }

                        if (isEditing) {
                            OutlinedTextField(
                                value = formattedDisplayDate,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Fecha") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { showDatePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = "Cambiar fecha"
                                        )
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = formattedDisplayTime,
                                onValueChange = {},
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("Hora") },
                                readOnly = true,
                                trailingIcon = {
                                    IconButton(onClick = { showTimePicker = true }) {
                                        Icon(
                                            imageVector = Icons.Default.AccessTime,
                                            contentDescription = "Cambiar hora"
                                        )
                                    }
                                }
                            )
                        } else {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = "${historyEntry.date} • ${historyEntry.time}",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }

                    // Sección notas
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "📝 Notas adicionales",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            if (!isEditing && notesText.isNotEmpty()) {
                                Row {
                                    IconButton(onClick = { isEditing = true }) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Editar notas",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = {
                                        notesText = ""
                                        onDeleteNotes(historyEntry.id)
                                        onDismiss()
                                    }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Eliminar notas",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }

                        if (isEditing) {
                            OutlinedTextField(
                                value = notesText,
                                onValueChange = { notesText = it },
                                label = { Text("Notas (opcional)") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(120.dp),
                                maxLines = 5,
                                shape = RoundedCornerShape(8.dp)
                            )
                        } else {
                            if (notesText.isNotEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Text(
                                        text = notesText,
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text(
                                        text = "Sin notas adicionales",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(onClick = { isEditing = true }) {
                                        Text("Añadir notas")
                                    }
                                }
                            }
                        }
                    }

                    if (isEditing) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                notesText = historyEntry.notes
                                selectedDateTime = originalDateTime
                                isEditing = false
                            }) {
                                Text("Cancelar")
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Button(
                                onClick = {
                                    onSaveChanges(historyEntry.id, notesText, selectedDateTime)
                                    isEditing = false
                                    onDismiss()
                                },
                                enabled = hasChanges
                            ) {
                                Text("Guardar cambios")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val initialMillis = remember(selectedDateTime, zoneId) {
            selectedDateTime.atZone(zoneId).toInstant().toEpochMilli()
        }
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val newDate = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDate()
                        selectedDateTime = selectedDateTime.withYear(newDate.year)
                            .withMonth(newDate.monthValue)
                            .withDayOfMonth(newDate.dayOfMonth)
                    }
                    showDatePicker = false
                }) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showTimePicker) {
        val timePickerState = rememberTimePickerState(
            initialHour = selectedDateTime.hour,
            initialMinute = selectedDateTime.minute,
            is24Hour = true
        )

        Dialog(onDismissRequest = { showTimePicker = false }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Selecciona la hora",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    TimePicker(state = timePickerState)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = { showTimePicker = false }) {
                            Text("Cancelar")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            selectedDateTime = selectedDateTime.withHour(timePickerState.hour)
                                .withMinute(timePickerState.minute)
                                .withSecond(0)
                                .withNano(0)
                            showTimePicker = false
                        }) {
                            Text("Aceptar")
                        }
                    }
                }
            }
        }
    }
}
