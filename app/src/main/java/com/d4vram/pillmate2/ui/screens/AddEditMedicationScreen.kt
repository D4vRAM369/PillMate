@file:OptIn(ExperimentalMaterial3Api::class)

package com.d4vram.pillmate2.ui.screens

import android.net.Uri
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.ui.components.ImagePickerSection
import com.d4vram.pillmate2.utils.ImageUtils
import com.d4vram.pillmate2.notifications.ReminderScheduler
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicationScreen(
    repository: MedicationRepository,
    medicationId: String? = null,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paletteColors = remember { mutableStateListOf<Color>().apply { addAll(defaultColorPalette) } }
    val quickEmojiOptions = remember { mutableStateListOf<String>().apply { addAll(featuredEmojis) } }

    var name by remember { mutableStateOf("") }
    var dose by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("Comprimido") }
    var selectedColor by remember { mutableStateOf(paletteColors.first()) }
    var selectedEmoji by remember { mutableStateOf(quickEmojiOptions.first()) }
    var dailyGoal by remember { mutableStateOf("1") }
    var hour by remember { mutableStateOf("08") }
    var minute by remember { mutableStateOf("00") }
    var selectedDays by remember { mutableStateOf(setOf<String>()) }
    var reminderEnabled by remember { mutableStateOf(false) }
    var isPrn by remember { mutableStateOf(false) }
    var existingMedication by remember { mutableStateOf<Medication?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var emojiSearchQuery by remember { mutableStateOf("") }
    var showCustomColorDialog by remember { mutableStateOf(false) }

    // 📸 Estados para foto del medicamento
    var photoPath by remember { mutableStateOf<String?>(null) }
    var usePhotoInstead by remember { mutableStateOf(false) }
    var isProcessingImage by remember { mutableStateOf(false) }
    
    // 🔗 Estados para combinar/vincular medicamentos
    var otherMedications by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var parentMedicationId by remember { mutableStateOf<String?>(null) }
    var expandedCombine by remember { mutableStateOf(false) }

    LaunchedEffect(medicationId) {
        val allMeds = repository.getMedicationsSnapshot()
        // No podemos vincular a medicamentos que ya son hijos de otros
        otherMedications = allMeds.filter { it.id != medicationId && it.parentMedicationId == null }
    }


    LaunchedEffect(medicationId) {
        medicationId?.let { id ->
            val medication = repository.getMedicationById(id)
            medication?.let {
                existingMedication = it
                name = it.name
                dose = it.dose
                type = it.type
                if (!paletteColors.contains(it.color)) {
                    paletteColors.add(0, it.color)
                }
                selectedColor = it.color

                if (!quickEmojiOptions.contains(it.emoji)) {
                    quickEmojiOptions.add(0, it.emoji)
                }
                selectedEmoji = it.emoji
                dailyGoal = it.dailyGoal.toString()
                isPrn = it.isPrn

                // Cargar datos de horario si existen
                it.scheduleTime?.let { time ->
                    val parts = time.split(":")
                    if (parts.size == 2) {
                        hour = parts[0]
                        minute = parts[1]
                    }
                }
                selectedDays = it.scheduleDays.toSet()
                reminderEnabled = it.reminderEnabled

                // 📸 Cargar datos de foto si existen
                photoPath = it.photoPath
                usePhotoInstead = it.usePhotoInstead
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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    if (name.isNotBlank() && dose.isNotBlank()) {
                        val baseMedication = existingMedication ?: Medication(
                            id = medicationId ?: UUID.randomUUID().toString(),
                            name = name,
                            dose = dose,
                            type = type,
                            color = selectedColor,
                            emoji = selectedEmoji,
                            scheduleTime = null,
                            scheduleDays = emptyList()
                        )
                        // Construir hora en formato HH:mm
                        val formattedTime = if (hour.isNotBlank() && minute.isNotBlank()) {
                            "${hour.padStart(2, '0')}:${minute.padStart(2, '0')}"
                        } else null

                        val savedScheduleTime = if (reminderEnabled) formattedTime else null
                        val savedScheduleDays = if (reminderEnabled) selectedDays.toList() else emptyList()

                        val medication = baseMedication.copy(
                            name = name,
                            dose = dose,
                            type = type,
                            color = selectedColor,
                            emoji = selectedEmoji,
                            dailyGoal = dailyGoal.toIntOrNull() ?: baseMedication.dailyGoal,
                            isPrn = isPrn,
                            scheduleTime = savedScheduleTime,
                            scheduleDays = savedScheduleDays,
                            reminderEnabled = reminderEnabled,
                            photoPath = photoPath,
                            usePhotoInstead = usePhotoInstead,
                            parentMedicationId = parentMedicationId
                        )

                        // Usar la nueva lógica inteligente de familias
                        repository.saveMedicationWithSmartFamilyLogic(medication, parentMedicationId)

                        // Programar o cancelar recordatorio según configuración
                        if (medication.reminderEnabled) {
                            ReminderScheduler.scheduleReminder(context, medication)
                        } else {
                            ReminderScheduler.cancelReminder(context, medication.id)
                        }

                        onNavigateBack()
                    }
                },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = null
                    )
                },
                text = {
                    Text(
                        text = if (isEditing) "Actualizar" else "Guardar",
                        fontWeight = FontWeight.Bold
                    )
                }
            )
        }
    ) { paddingValues ->
        if (showEmojiPicker) {
            EmojiPickerDialog(
                query = emojiSearchQuery,
                onQueryChange = { emojiSearchQuery = it },
                onEmojiSelected = { emoji ->
                    selectedEmoji = emoji
                    if (!quickEmojiOptions.contains(emoji)) {
                        quickEmojiOptions.add(0, emoji)
                        while (quickEmojiOptions.size > QUICK_EMOJI_LIMIT) {
                            quickEmojiOptions.removeAt(quickEmojiOptions.lastIndex)
                        }
                    }
                    showEmojiPicker = false
                    emojiSearchQuery = ""
                },
                onDismiss = {
                    showEmojiPicker = false
                    emojiSearchQuery = ""
                }
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FormSection {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del medicamento") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = dose,
                    onValueChange = { dose = it },
                    label = { Text("Dosis") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = type,
                    onValueChange = { type = it },
                    label = { Text("Tipo (ej: Comprimido, Cápsula, Jarabe, Gotas)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Comprimido") }
                )
            }

            SectionCard(
                title = "Color del medicamento",
                subtitle = "Elige uno de la paleta o agrega un tono personalizado"
            ) {
                ColorPaletteSection(
                    colors = paletteColors,
                    selectedColor = selectedColor,
                    onColorSelected = { selectedColor = it }
                )

                AssistChip(
                    onClick = { showCustomColorDialog = true },
                    label = { Text("Color personalizado") },
                    leadingIcon = {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    }
                )
            }

            SectionCard(
                title = "Emoji (opcional)",
                subtitle = "Te ayudará a identificar rápidamente el medicamento"
            ) {
                EmojiQuickPickGrid(
                    emojis = quickEmojiOptions,
                    selectedEmoji = selectedEmoji,
                    onEmojiSelected = { selectedEmoji = it }
                )

                FilledTonalButton(
                    onClick = { showEmojiPicker = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Buscar más emojis")
                }
            }

            // 📸 Sección de fotografía del medicamento
            ImagePickerSection(
                currentPhotoPath = photoPath,
                usePhotoInstead = usePhotoInstead,
                onPhotoSelected = { uri ->
                    // Procesar imagen en background
                    scope.launch {
                        isProcessingImage = true
                        val newPhotoPath = withContext(Dispatchers.IO) {
                            val currentMedicationId = medicationId ?: UUID.randomUUID().toString()
                            ImageUtils.saveImageFromUri(context, uri, currentMedicationId)
                        }
                        isProcessingImage = false

                        if (newPhotoPath != null) {
                            // Eliminar foto anterior si existe
                            photoPath?.let { oldPath ->
                                withContext(Dispatchers.IO) {
                                    ImageUtils.deleteImage(oldPath)
                                }
                            }
                            photoPath = newPhotoPath
                        }
                    }
                },
                onPhotoDeleted = {
                    scope.launch {
                        photoPath?.let { path ->
                            withContext(Dispatchers.IO) {
                                ImageUtils.deleteImage(path)
                            }
                        }
                        photoPath = null
                        usePhotoInstead = false
                    }
                },
                onUsePhotoToggled = { usePhotoInstead = it },
                medicationId = medicationId ?: UUID.randomUUID().toString()
            )

            LaunchedEffect(medicationId) {
                medicationId?.let { id ->
                    val med = repository.getMedicationById(id)
                    parentMedicationId = med?.parentMedicationId
                }
            }

            SectionCard(
                title = "Combinar medicamento",
                subtitle = "Vincular a otro para agrupar en estadísticas (ej: distintas dosis de Ibuprofeno)"
            ) {
                val selectedParent = otherMedications.find { it.id == parentMedicationId }
                
                OutlinedCard(
                    onClick = { expandedCombine = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = if (selectedParent != null) "Viculado a: ${selectedParent.name}" else "Sin vincular (Independiente)",
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                    }
                }

                if (expandedCombine) {
                    AlertDialog(
                        onDismissRequest = { expandedCombine = false },
                        title = { Text("Selecciona medicamento principal") },
                        text = {
                            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                // Opción para desvincular
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { 
                                            parentMedicationId = null
                                            expandedCombine = false 
                                        }
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = parentMedicationId == null, onClick = null)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("No vincular (Mantener separado)")
                                }
                                
                                otherMedications.forEach { med ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { 
                                                parentMedicationId = med.id
                                                expandedCombine = false 
                                            }
                                            .padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        RadioButton(selected = parentMedicationId == med.id, onClick = null)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("${med.name} (${med.dose})")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { expandedCombine = false }) {
                                Text("Cerrar")
                            }
                        }
                    )
                }
            }

            SectionCard(
                title = "Planificación",
                subtitle = "Configura la hora de toma y la frecuencia"
            ) {
                // Switch para activar recordatorios
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Activar recordatorios",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Recibir notificación a la hora programada",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = hour,
                        onValueChange = { if (it.length <= 2) hour = it },
                        label = { Text("Hora") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = minute,
                        onValueChange = { if (it.length <= 2) minute = it },
                        label = { Text("Minuto") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Días de toma",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

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

                OutlinedTextField(
                    value = dailyGoal,
                    onValueChange = { dailyGoal = it },
                    label = { Text("Veces por día") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(80.dp))
        }

        if (showCustomColorDialog) {
            CustomColorPickerDialog(
                onDismiss = { showCustomColorDialog = false },
                onConfirm = { color ->
                    if (!paletteColors.contains(color)) {
                        paletteColors.add(0, color)
                    }
                    selectedColor = color
                    showCustomColorDialog = false
                }
            )
        }
    }
}

@Composable
private fun FormSection(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorPaletteSection(
    colors: List<Color>,
    selectedColor: Color,
    onColorSelected: (Color) -> Unit
) {
    FlowRow(
        maxItemsInEachRow = 5,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        colors.forEach { swatchColor ->
            ColorSwatch(
                color = swatchColor,
                selected = swatchColor == selectedColor,
                onClick = { onColorSelected(swatchColor) }
            )
        }
    }
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Surface(
        color = color,
        shape = CircleShape,
        border = BorderStroke(width = if (selected) 3.dp else 1.5.dp, color = borderColor),
        tonalElevation = if (selected) 4.dp else 0.dp,
        shadowElevation = if (selected) 8.dp else 0.dp,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = "Color ${color.toHexLabel()}" },
        onClick = onClick
    ) {
        if (selected) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmojiQuickPickGrid(
    emojis: List<String>,
    selectedEmoji: String,
    onEmojiSelected: (String) -> Unit
) {
    FlowRow(
        maxItemsInEachRow = 5,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        emojis.forEach { emoji ->
            val isSelected = emoji == selectedEmoji
            val borderColor = if (isSelected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                },
                tonalElevation = if (isSelected) 4.dp else 0.dp,
                border = BorderStroke(width = if (isSelected) 2.dp else 1.dp, color = borderColor),
                modifier = Modifier.size(56.dp),
                onClick = { onEmojiSelected(emoji) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = emoji,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}

@Composable
private fun EmojiPickerDialog(
    query: String,
    onQueryChange: (String) -> Unit,
    onEmojiSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Combinar emojis del sistema con la biblioteca personalizada
    val allSystemEmojis = remember {
        getAllSystemEmojis()
    }

    val filteredEmojis = remember(query, allSystemEmojis) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) {
            // Mostrar primero la biblioteca curada, luego todos los del sistema
            emojiLibrary + allSystemEmojis
        } else {
            // Buscar en la biblioteca curada (con keywords)
            val fromLibrary = emojiLibrary.filter { option ->
                option.symbol.contains(normalized) ||
                    option.keywords.any { keyword ->
                        keyword.contains(normalized, ignoreCase = true)
                    }
            }
            // Buscar en emojis del sistema (solo por símbolo)
            val fromSystem = allSystemEmojis.filter { option ->
                option.symbol.contains(normalized)
            }
            (fromLibrary + fromSystem).distinctBy { it.symbol }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        title = { Text("Buscar emoji") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    placeholder = { Text("Busca: pastilla, corazón, sol...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                if (filteredEmojis.isEmpty()) {
                    Text(
                        text = "No encontramos emojis que coincidan con tu búsqueda.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 52.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .heightIn(max = 320.dp)
                            .fillMaxWidth()
                    ) {
                        items(filteredEmojis) { option ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                                modifier = Modifier.size(56.dp),
                                onClick = { onEmojiSelected(option.symbol) }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = option.symbol,
                                        style = MaterialTheme.typography.titleLarge
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@Composable
private fun SectionCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            content()
        }
    }
}

@Composable
private fun ConstructionPlaceholder() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Column {
                Text(
                    text = "Funcionalidad en desarrollo",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "La planificación de horarios estará disponible pronto.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
private fun CustomColorPickerDialog(
    onDismiss: () -> Unit,
    onConfirm: (Color) -> Unit
) {
    val controller = rememberColorPickerController()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(controller.selectedColor.value)
                }
            ) {
                Text("Añadir")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        },
        title = { Text("Nuevo color personalizado") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                HsvColorPicker(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    controller = controller
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        color = controller.selectedColor.value,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {}
                    Text(
                        text = controller.selectedColor.value.toHexLabel(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    )
}

private fun Color.toHexLabel(): String {
    val r = (red * 255).roundToInt()
    val g = (green * 255).roundToInt()
    val b = (blue * 255).roundToInt()
    return "#%02X%02X%02X".format(r, g, b)
}

private val defaultColorPalette = listOf(
    Color(0xFF2563EB),
    Color(0xFF38BDF8),
    Color(0xFF22D3EE),
    Color(0xFF34D399),
    Color(0xFF10B981),
    Color(0xFF84CC16),
    Color(0xFFFACC15),
    Color(0xFFF97316),
    Color(0xFFFB7185),
    Color(0xFFEC4899),
    Color(0xFF8B5CF6),
    Color(0xFF6366F1),
    Color(0xFF3B82F6),
    Color(0xFF64748B),
    Color(0xFF94A3B8)
)

private const val QUICK_EMOJI_LIMIT = 40

private data class EmojiOption(
    val symbol: String,
    val keywords: List<String>
)

/**
 * Obtiene todos los emojis disponibles en el sistema Android
 * Rangos Unicode de emojis:
 * - Emoticons: U+1F600 - U+1F64F
 * - Símbolos Misceláneos: U+1F300 - U+1F5FF
 * - Transporte y Mapas: U+1F680 - U+1F6FF
 * - Símbolos Suplementarios: U+1F900 - U+1F9FF
 * - Emojis Extendidos: U+1FA70 - U+1FAFF
 * - Símbolos y Pictográficos: U+2600 - U+26FF
 * - Dingbats: U+2700 - U+27BF
 */
private fun getAllSystemEmojis(): List<EmojiOption> {
    val emojis = mutableListOf<EmojiOption>()

    // Rangos de emojis Unicode
    val emojiRanges = listOf(
        0x1F600..0x1F64F,  // Emoticons
        0x1F300..0x1F5FF,  // Símbolos Misceláneos
        0x1F680..0x1F6FF,  // Transporte y Mapas
        0x1F900..0x1F9FF,  // Símbolos Suplementarios
        0x1FA70..0x1FAFF,  // Emojis Extendidos
        0x2600..0x26FF,    // Símbolos y Pictográficos
        0x2700..0x27BF,    // Dingbats
        0x1F1E6..0x1F1FF   // Banderas (Regional Indicators)
    )

    for (range in emojiRanges) {
        for (codePoint in range) {
            try {
                val emoji = String(Character.toChars(codePoint))
                // Verificar que el carácter sea válido y no sea un carácter de control
                if (emoji.isNotEmpty() && !Character.isISOControl(codePoint)) {
                    emojis.add(EmojiOption(emoji, emptyList()))
                }
            } catch (e: Exception) {
                // Ignorar code points inválidos
            }
        }
    }

    return emojis
}

private val emojiLibrary = listOf(
    EmojiOption("💊", listOf("medicina", "pastilla", "pill", "remedio")),
    EmojiOption("⚕️", listOf("médico", "salud", "cruz")),
    EmojiOption("🩹", listOf("curita", "vendaje", "herida")),
    EmojiOption("🫀", listOf("corazón", "órgano", "cardio")),
    EmojiOption("🧠", listOf("cerebro", "mente")),
    EmojiOption("🩸", listOf("sangre", "donación")),
    EmojiOption("🧪", listOf("laboratorio", "prueba", "química")),
    EmojiOption("🛌", listOf("descanso", "reposo", "cama")),
    EmojiOption("🌡️", listOf("termómetro", "fiebre")),
    EmojiOption("💤", listOf("descanso", "sueño")),
    EmojiOption("💯", listOf("logro", "meta", "perfecto")),
    EmojiOption("⭐", listOf("estrella", "destacado")),
    EmojiOption("🟢", listOf("círculo", "verde")),
    EmojiOption("🔵", listOf("círculo", "azul")),
    EmojiOption("🟡", listOf("círculo", "amarillo")),
    EmojiOption("🟣", listOf("círculo", "morado")),
    EmojiOption("🟠", listOf("círculo", "naranja")),
    EmojiOption("🟤", listOf("círculo", "marrón")),
    EmojiOption("⚫", listOf("círculo", "negro")),
    EmojiOption("⚪", listOf("círculo", "blanco")),
    EmojiOption("🔺", listOf("triángulo", "alerta")),
    EmojiOption("🔻", listOf("triángulo", "bajar")),
    EmojiOption("🔸", listOf("rombo", "destacado")),
    EmojiOption("🔹", listOf("rombo", "info")),
    EmojiOption("☀️", listOf("sol", "día", "energía")),
    EmojiOption("🌀", listOf("disperso", "viento", "high")),
    EmojiOption("🌙", listOf("luna", "noche", "descanso")),
    EmojiOption("⏰", listOf("alarma", "hora")),
    EmojiOption("📅", listOf("calendario", "agenda")),
    EmojiOption("✅", listOf("completado", "listo")),
    EmojiOption("⚠️", listOf("alerta", "precaución")),
    EmojiOption("🎯", listOf("objetivo", "meta")),
    EmojiOption("🧘", listOf("relax", "meditación")),
    EmojiOption("🚶", listOf("caminar", "actividad")),
    EmojiOption("🏃", listOf("correr", "ejercicio")),
    EmojiOption("💪", listOf("fuerza", "ánimo")),
    EmojiOption("😴", listOf("dormir", "descanso")),
    EmojiOption("🤧", listOf("alergia", "resfriado")),
    EmojiOption("🧞", listOf("genio", "deseo")),
    EmojiOption("⍉", listOf("null", "symbol")),
    EmojiOption("◕", listOf("3/4", "tres cuartos")),
    EmojiOption("◒", listOf("1/2", "media")),
    EmojiOption("◔", listOf("1/4", "un cuarto")),
    EmojiOption("𝧤", listOf("retroceso", "vuelta")),
    EmojiOption("◔", listOf("1/4", "un cuarto")),
    EmojiOption("◔", listOf("1/4", "un cuarto")),
    EmojiOption("𓀚", listOf("viajero", "caminante")),
    EmojiOption("☢", listOf("nuke", "bomb")), ///⌬ ⚛ ︎⚕  ☢
)

private val featuredEmojis = emojiLibrary.map { it.symbol }.distinct().take(20)

private val daysOfWeek = mapOf(
    "L" to "Lun",
    "M" to "Mar",
    "X" to "Mié",
    "J" to "Jue",
    "V" to "Vie",
    "S" to "Sáb",
    "D" to "Dom"
)
