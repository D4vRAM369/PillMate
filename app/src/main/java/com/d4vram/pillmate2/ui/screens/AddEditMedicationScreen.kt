package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditMedicationScreen(
    repository: MedicationRepository,
    medicationId: String? = null,
    onNavigateBack: () -> Unit
) {
    // Estados del formulario
    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Comprimido") }
    var selectedColor by remember { mutableStateOf(predefinedColors[0]) }
    var selectedEmoji by remember { mutableStateOf("💊") }
    var dailyGoal by remember { mutableStateOf("1") }
    var hour by remember { mutableStateOf("08") }
    var minute by remember { mutableStateOf("00") }
    var selectedDays by remember { mutableStateOf(setOf<String>()) }
    var isPrn by remember { mutableStateOf(false) }
    var existingMedication by remember { mutableStateOf<Medication?>(null) }
    
    // Cargar datos si estamos editando
    LaunchedEffect(medicationId) {
        medicationId?.let { id ->
            val medication = repository.getMedicationById(id)
            medication?.let {
                existingMedication = it
                name = it.name
                dose = it.dose
                type = it.type
                selectedColor = it.color
                selectedEmoji = it.emoji
                dailyGoal = it.dailyGoal.toString()
                isPrn = it.isPrn
            }
        }
    }
    
    val isEditing = medicationId != null
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isEditing) "Editar Medicamento" else "Agregar Medicamento",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            if (name.isNotBlank() && dose.isNotBlank()) {
                                val baseMedication = existingMedication ?: Medication(
                                    id = medicationId ?: UUID.randomUUID().toString(),
                                    name = name,
                                    dose = dose,
                                    type = type,
                                    color = selectedColor,
                                    emoji = selectedEmoji
                                )
                                val medication = baseMedication.copy(
                                    name = name,
                                    dose = dose,
                                    type = type,
                                    color = selectedColor,
                                    emoji = selectedEmoji,
                                    dailyGoal = dailyGoal.toIntOrNull() ?: baseMedication.dailyGoal,
                                    isPrn = isPrn
                                )
                                
                                if (isEditing) {
                                    repository.updateMedication(medication)
                                } else {
                                    repository.addMedication(medication)
                                }
                                onNavigateBack()
                            }
                        }
                    ) {
                        Text(
                            text = "GUARDAR",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Nombre del medicamento
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nombre del medicamento") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Dosis
            OutlinedTextField(
                value = dose,
                onValueChange = { dose = it },
                label = { Text("Dosis (ej: 500mg, 1 comprimido)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Tipo de medicamento
            OutlinedTextField(
                value = type,
                onValueChange = { type = it },
                label = { Text("Tipo (ej: Comprimido, Cápsula, Jarabe, Gotas)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Comprimido") }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Selector de color
            Text(
                text = "Color del medicamento",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(predefinedColors) { color ->
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(color)
                            .border(
                                width = if (color == selectedColor) 3.dp else 1.dp,
                                color = if (color == selectedColor) MaterialTheme.colorScheme.primary else Color.Gray,
                                shape = CircleShape
                            )
                            .clickable { selectedColor = color }
                    ) {
                        if (color == selectedColor) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Selector de emoji
            Text(
                text = "Emoji (opcional)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(6),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(120.dp)
            ) {
                items(predefinedEmojis) { emoji ->
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (emoji == selectedEmoji) 
                                    MaterialTheme.colorScheme.primaryContainer 
                                else 
                                    MaterialTheme.colorScheme.surface
                            )
                            .border(
                                width = if (emoji == selectedEmoji) 2.dp else 1.dp,
                                color = if (emoji == selectedEmoji) 
                                    MaterialTheme.colorScheme.primary 
                                else 
                                    MaterialTheme.colorScheme.outline,
                                shape = RoundedCornerShape(8.dp)
                            )
                            .clickable { selectedEmoji = emoji },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = emoji,
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Horario
            Text(
                text = "Hora de toma",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row {
                OutlinedTextField(
                    value = hour,
                    onValueChange = { if (it.length <= 2) hour = it },
                    label = { Text("Hora") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                
                Spacer(modifier = Modifier.width(16.dp))
                
                OutlinedTextField(
                    value = minute,
                    onValueChange = { if (it.length <= 2) minute = it },
                    label = { Text("Minuto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Días de la semana
            Text(
                text = "Días de toma",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(daysOfWeek.toList()) { day ->
                    FilterChip(
                        onClick = {
                            selectedDays = if (selectedDays.contains(day.first)) {
                                selectedDays - day.first
                            } else {
                                selectedDays + day.first
                            }
                        },
                        label = { Text(day.second) },
                        selected = selectedDays.contains(day.first)
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Dosis diaria
            OutlinedTextField(
                value = dailyGoal,
                onValueChange = { dailyGoal = it },
                label = { Text("Veces por día") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // PRN (según necesidad)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = isPrn,
                    onCheckedChange = { isPrn = it }
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Solo cuando sea necesario (PRN)")
            }
            
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

// Colores predefinidos
private val predefinedColors = listOf(
    Color(0xFF6366F1), // Índigo
    Color(0xFFEC4899), // Rosa
    Color(0xFF10B981), // Verde
    Color(0xFFF59E0B), // Amarillo
    Color(0xFFEF4444), // Rojo
    Color(0xFF8B5CF6), // Púrpura
    Color(0xFF06B6D4), // Cian
    Color(0xFF84CC16)  // Lima
)

// Emojis predefinidos
private val predefinedEmojis = listOf(
    "💊", "💉", "🧬", "⚕️", "🩹", "🫀",
    "🧪", "🔬", "💯", "⭐", "🟢", "🔵",
    "🟡", "🔴", "🟣", "🟠", "⚫", "⚪",
    "➕", "❤️", "💚", "💙", "💜", "🧡"
)

// Días de la semana
private val daysOfWeek = mapOf(
    "L" to "Lun",
    "M" to "Mar", 
    "X" to "Mié",
    "J" to "Jue",
    "V" to "Vie",
    "S" to "Sáb",
    "D" to "Dom"
)
