package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddEditMedicationScreen(
    repository: MedicationRepository,
    medicationId: String? = null,
    onNavigateBack: () -> Unit
) {
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
    var isPrn by remember { mutableStateOf(false) }
    var existingMedication by remember { mutableStateOf<Medication?>(null) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var emojiSearchQuery by remember { mutableStateOf("") }
    var showCustomColorDialog by remember { mutableStateOf(false) }
    var customColorInput by remember { mutableStateOf("#") }
    var customColorError by remember { mutableStateOf<String?>(null) }

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
                                    emoji = selectedEmoji,
                                    scheduleTime = null,
                                    scheduleDays = emptyList()
                                )
                                val medication = baseMedication.copy(
                                    name = name,
                                    dose = dose,
                                    type = type,
                                    color = selectedColor,
                                    emoji = selectedEmoji,
                                    dailyGoal = dailyGoal.toIntOrNull() ?: baseMedication.dailyGoal,
                                    isPrn = isPrn,
                                    scheduleTime = baseMedication.scheduleTime,
                                    scheduleDays = baseMedication.scheduleDays
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
        if (showEmojiPicker) {
            EmojiPickerDialog(
                query = emojiSearchQuery,
                onQueryChange = { emojiSearchQuery = it },
                onEmojiSelected = { emoji ->
                    selectedEmoji = emoji
                    if (!quickEmojiOptions.contains(emoji)) {
                        quickEmojiOptions.add(0, emoji)
                        while (quickEmojiOptions.size > QUICK_EMOJI_LIMIT) {
                            quickEmojiOptions.removeLast()
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
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                    label = { Text("Dosis (ej: 500mg, 1 comprimido)") },
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

            Text(
                text = "🚧 EN CONSTRUCCIÓN ⬇️🚧",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            SectionCard(
                title = "Planificación",
                subtitle = "Configura la hora de toma y la frecuencia"
            )  {
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
        }

        if (showCustomColorDialog) {
            AddColorDialog(
                value = customColorInput,
                errorMessage = customColorError,
                onValueChange = {
                    customColorInput = it
                    customColorError = null
                },
                onConfirm = {
                    val parsedColor = parseHexColor(customColorInput)
                    if (parsedColor == null) {
                        customColorError = "Usa el formato #RRGGBB o #AARRGGBB"
                    } else {
                        if (!paletteColors.contains(parsedColor)) {
                            paletteColors.add(parsedColor)
                        }
                        selectedColor = parsedColor
                        customColorInput = "#"
                        customColorError = null
                        showCustomColorDialog = false
                    }
                },
                onDismiss = {
                    showCustomColorDialog = false
                    customColorError = null
                    customColorInput = "#"
                }
            )
        }
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
        MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f)
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
    }

    Surface(
        color = color,
        shape = CircleShape,
        border = BorderStroke(width = if (selected) 3.dp else 1.dp, color = borderColor),
        tonalElevation = if (selected) 4.dp else 0.dp,
        shadowElevation = if (selected) 6.dp else 0.dp,
        modifier = Modifier
            .size(56.dp)
            .semantics { contentDescription = "Color ${color.toHexLabel()}" },
        onClick = onClick
    ) {
        if (selected) {
            Box(Modifier.fillMaxSize()) {
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
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
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
    val filteredEmojis = remember(query) {
        val normalized = query.trim().lowercase()
        if (normalized.isEmpty()) {
            emojiLibrary
        } else {
            emojiLibrary.filter { option ->
                option.symbol.contains(normalized) ||
                        option.keywords.any { keyword ->
                            keyword.contains(normalized, ignoreCase = true)
                        }
            }
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
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        tonalElevation = 2.dp,
        shadowElevation = 6.dp,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
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
private fun AddColorDialog(
    value: String,
    errorMessage: String?,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Introduce el color en formato hexadecimal (por ejemplo, #FF4081).",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = value,
                    onValueChange = { onValueChange(it.uppercase()) },
                    label = { Text("Código de color") },
                    singleLine = true,
                    isError = errorMessage != null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    supportingText = {
                        if (errorMessage != null) {
                            Text(
                                text = errorMessage,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                )
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

private fun parseHexColor(raw: String): Color? {
    val normalized = raw.trim().removePrefix("#")
    if (normalized.length != 6 && normalized.length != 8) return null
    return try {
        val value = normalized.toLong(16)
        if (normalized.length == 8) {
            val a = ((value shr 24) and 0xFF).toInt()
            val r = ((value shr 16) and 0xFF).toInt()
            val g = ((value shr 8) and 0xFF).toInt()
            val b = (value and 0xFF).toInt()
            Color(r / 255f, g / 255f, b / 255f, a / 255f)
        } else {
            val r = ((value shr 16) and 0xFF).toInt()
            val g = ((value shr 8) and 0xFF).toInt()
            val b = (value and 0xFF).toInt()
            Color(r / 255f, g / 255f, b / 255f)
        }
    } catch (ignored: NumberFormatException) {
        null
    }
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