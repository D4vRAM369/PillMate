package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.time.LocalDateTime
import com.d4vram.pillmate2.model.SortMode
import com.d4vram.pillmate2.ui.components.HistoryItem
import com.d4vram.pillmate2.ui.components.MedicationListItem
import com.d4vram.pillmate2.ui.components.ReorderableMedicationGrid
import com.d4vram.pillmate2.ui.theme.AppUiStyle
import com.d4vram.pillmate2.ui.theme.HeaderGradientEnd
import com.d4vram.pillmate2.ui.theme.HeaderGradientStart
import com.d4vram.pillmate2.ui.theme.LocalUiStyle
import com.d4vram.pillmate2.widget.QuickTakeWidget
import androidx.glance.appwidget.updateAll

/**
 * 🏠 Pantalla principal de la app
 * Aquí se replica exactamente el diseño del HTML
 */
@Composable
fun HomeScreen(
    repository: MedicationRepository,
    navController: NavController,
    themeManager: com.d4vram.pillmate2.ui.theme.ThemeManager,
    onAddMedication: () -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {}
) {
    // Estados observables del repositorio
    val medicationsRaw by repository.medications.collectAsState()
    val historyEntries by repository.historyEntries.collectAsState()
    val stats by repository.stats.collectAsState()
    val chipCountPeriod by themeManager.chipCountPeriod.collectAsState(initial = com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Total)
    val sortMode by themeManager.sortMode.collectAsState(initial = SortMode.MANUAL)
    val isListView by themeManager.isListView.collectAsState(initial = false)
    val quickTakeMedicationIds by themeManager.quickTakeMedicationIds.collectAsState(
        initial = listOf(null, null, null, null)
    )
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp

    // Aplicar ordenamiento según la configuración del usuario
    val medications = remember(medicationsRaw, sortMode) {
        repository.applySorting(medicationsRaw, sortMode)
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchActive by remember { mutableStateOf(false) }

    val filteredMedications = remember(medications, searchQuery) {
        if (searchQuery.isBlank()) medications
        else medications.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.dose.contains(searchQuery, ignoreCase = true)
        }
    }
    val quickTakeSlots = remember(medications, quickTakeMedicationIds) {
        quickTakeMedicationIds.map { medicationId ->
            medicationId?.let { id -> medications.firstOrNull { it.id == id } }
        }
    }

    // Estado para el modal de notas / entrada seleccionada
    var selectedHistoryEntry by remember { mutableStateOf<HistoryEntry?>(null) }

    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .heightIn(max = screenHeight),
            state = listState
        ) {
            item {
                // Header con gradiente verde
                HeaderSection(
                    onAddMedication = onAddMedication,
                    searchQuery = searchQuery,
                    searchActive = searchActive,
                    onSearchQueryChange = { searchQuery = it },
                    onSearchActiveChange = { active ->
                        searchActive = active
                        if (!active) searchQuery = ""
                    }
                )
            }

            item {
                // Fila de estadísticas - clickeable para ver estadísticas avanzadas
                // Tarjeta de acceso a estadísticas
                StatisticsEntryCard(
                    onClick = { navController.navigate("global_stats") },
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }

            if (medications.isNotEmpty()) {
                item {
                    QuickTakeSection(
                        slots = quickTakeSlots,
                        availableMedications = medications,
                        onQuickTake = { medicationId ->
                            repository.takeMedication(medicationId, quantity = 1, customTimestamp = null)
                            scope.launch {
                                QuickTakeWidget().updateAll(context.applicationContext)
                            }
                        },
                        onAssignMedication = { slotIndex, medicationId ->
                            scope.launch {
                                themeManager.setQuickTakeMedication(slotIndex, medicationId)
                                QuickTakeWidget().updateAll(context.applicationContext)
                            }
                        },
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            }

            item {
                SectionTitle(
                    title = "Mis Medicamentos",
                    leadingIcon = Icons.Default.Medication,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    trailingContent = {
                        IconButton(
                            onClick = { scope.launch { themeManager.setListView(!isListView) } },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = if (isListView)
                                    Icons.Default.GridView
                                else
                                    Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = if (isListView) "Ver como grid" else "Ver como lista",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            if (isListView) {
                items(
                    items = filteredMedications,
                    key = { it.id }
                ) { medication ->
                    MedicationListItem(
                        medication = medication,
                        repository = repository,
                        chipCountPeriod = chipCountPeriod,
                        onTakeMedication = { medicationId, quantity, timestamp ->
                            repository.takeMedication(medicationId, quantity, timestamp)
                        },
                        onEditMedication = onEditMedication,
                        onDeleteMedication = onDeleteMedication,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            } else {
                item {
                    ReorderableMedicationGrid(
                        medications = filteredMedications,
                        repository = repository,
                        chipCountPeriod = chipCountPeriod,
                        onTakeMedication = { medicationId, quantity, timestamp ->
                            repository.takeMedication(medicationId, quantity, timestamp)
                        },
                        onAddMedication = onAddMedication,
                        onEditMedication = onEditMedication,
                        onDeleteMedication = onDeleteMedication,
                        onReorder = { fromIndex, toIndex ->
                            if (sortMode == SortMode.MANUAL) {
                                repository.reorderMedications(fromIndex, toIndex)
                            }
                        },
                        dragEnabled = sortMode == SortMode.MANUAL,
                        parentListState = listState,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                // Título del historial
                SectionTitle(
                    title = "Historial Reciente",
                    leadingIcon = Icons.Default.History,
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            item {
                // Lista del historial
                HistorySection(
                    historyEntries = historyEntries,
                    navController = navController,
                    onEntryClick = { entry -> selectedHistoryEntry = entry },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            item {
                // Espacio para el bottom navigation
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        selectedHistoryEntry?.let { entry ->
            AlertDialog(
                onDismissRequest = { selectedHistoryEntry = null },
                title = { Text(text = "Notas") },
                text = {
                    val notesText = entry.notes.ifBlank { "Sin notas para esta entrada." }
                    Text(notesText)
                },
                confirmButton = {
                    TextButton(onClick = { selectedHistoryEntry = null }) {
                        Text("Cerrar")
                    }
                }
            )
        }
    }
}

/**
 * 🎨 Header con gradiente y botón de agregar
 */
@Composable
private fun HeaderSection(
    onAddMedication: () -> Unit,
    searchQuery: String,
    searchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit
) {
    val uiStyle = LocalUiStyle.current
    val headerModifier = Modifier
        .fillMaxWidth()
        .let { base ->
            if (uiStyle == AppUiStyle.MaterialYou) {
                base.background(MaterialTheme.colorScheme.primaryContainer)
            } else {
                base.background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(HeaderGradientStart, HeaderGradientEnd)
                    )
                )
            }
        }

    Box(
        modifier = headerModifier
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column {
            Text(
                text = "PillMate",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = if (uiStyle == AppUiStyle.MaterialYou) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    Color.White
                }
            )
            Text(
                text = "Seguimiento Personal",
                style = MaterialTheme.typography.bodyMedium,
                color = if (uiStyle == AppUiStyle.MaterialYou) {
                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                } else {
                    Color.White.copy(alpha = 0.9f)
                }
            )

            // Barra de búsqueda expandible
            AnimatedVisibility(visible = searchActive) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChange,
                    placeholder = { Text("Buscar medicamento…", color = Color.White.copy(alpha = 0.7f)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.8f)
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = { onSearchActiveChange(false) }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar búsqueda",
                                tint = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color.White.copy(alpha = 0.6f),
                        unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                        cursorColor = Color.White
                    )
                )
            }
        }

        Row(
            modifier = Modifier.align(Alignment.CenterEnd),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AnimatedVisibility(visible = !searchActive) {
                IconButton(
                    onClick = { onSearchActiveChange(true) },
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            color = Color.White.copy(alpha = 0.15f),
                            shape = CircleShape
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Buscar",
                        tint = Color.White
                    )
                }
            }

            FloatingActionButton(
                onClick = onAddMedication,
                modifier = Modifier.size(48.dp),
                containerColor = if (uiStyle == AppUiStyle.MaterialYou) {
                    MaterialTheme.colorScheme.primary
                } else {
                    Color.White.copy(alpha = 0.2f)
                },
                contentColor = if (uiStyle == AppUiStyle.MaterialYou) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    Color.White
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar medicamento"
                )
            }
        }
    }
}

/**
 * 📊 Tarjeta de acceso a estadísticas
 * Un solo punto de entrada para ver el detalle completo
 */
@Composable
private fun StatisticsEntryCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(80.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icono con fondo circular
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.BarChart,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                
                Spacer(modifier = Modifier.width(16.dp))
                
                Column {
                    Text(
                        text = "Estadísticas Globales",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Ver resumen y progreso",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Ver estadísticas",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun QuickTakeSection(
    slots: List<Medication?>,
    availableMedications: List<Medication>,
    onQuickTake: (String) -> Unit,
    onAssignMedication: (Int, String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var slotBeingConfigured by remember { mutableStateOf<Int?>(null) }

    Column(modifier = modifier.fillMaxWidth()) {
        SectionTitle(
            title = "Toma rápida",
            subtitle = "4 casillas",
            leadingIcon = Icons.Default.Bolt,
            modifier = Modifier.padding(horizontal = 24.dp)
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(count = 4, key = { it }) { slotIndex ->
                QuickTakeSlotCard(
                    slotNumber = slotIndex + 1,
                    medication = slots.getOrNull(slotIndex),
                    onQuickTake = { medication ->
                        onQuickTake(medication.id)
                    },
                    onConfigure = {
                        slotBeingConfigured = slotIndex
                    }
                )
            }
        }
    }

    slotBeingConfigured?.let { slotIndex ->
        QuickTakeSlotPickerDialog(
            slotNumber = slotIndex + 1,
            medications = availableMedications,
            selectedMedicationId = slots.getOrNull(slotIndex)?.id,
            onSelectMedication = { medicationId ->
                onAssignMedication(slotIndex, medicationId)
                slotBeingConfigured = null
            },
            onClearSlot = {
                onAssignMedication(slotIndex, null)
                slotBeingConfigured = null
            },
            onDismiss = {
                slotBeingConfigured = null
            }
        )
    }
}

@Composable
private fun QuickTakeSlotCard(
    slotNumber: Int,
    medication: Medication?,
    onQuickTake: (Medication) -> Unit,
    onConfigure: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(176.dp)
            .height(132.dp)
            .clickable(enabled = medication != null) {
                medication?.let(onQuickTake)
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .background(
                            color = medication?.color?.copy(alpha = 0.14f)
                                ?: MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = medication?.emoji ?: slotNumber.toString(),
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = medication?.name ?: "Elegir",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1
                    )
                    Text(
                        text = medication?.dose ?: "Casilla $slotNumber",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onConfigure,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Configurar casilla $slotNumber",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = if (medication == null) "Sin asignar" else "1 toma",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = medication?.color ?: MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (medication == null) "Configurar" else "Tomar",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = medication?.color ?: MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickTakeSlotPickerDialog(
    slotNumber: Int,
    medications: List<Medication>,
    selectedMedicationId: String?,
    onSelectMedication: (String) -> Unit,
    onClearSlot: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Casilla $slotNumber") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(
                    items = medications,
                    key = { it.id }
                ) { medication ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectMedication(medication.id) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    color = medication.color.copy(alpha = 0.14f),
                                    shape = CircleShape
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = medication.emoji)
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = medication.name,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                maxLines = 1
                            )
                            Text(
                                text = medication.dose,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }

                        if (medication.id == selectedMedicationId) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Seleccionado",
                                tint = medication.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        },
        dismissButton = {
            TextButton(onClick = onClearSlot) {
                Text("Vaciar")
            }
        }
    )
}

/**
 * 🏷️ Título de sección con subtitle opcional
 */
@Composable
private fun SectionTitle(
    title: String,
    subtitle: String? = null,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    modifier: Modifier = Modifier,
    trailingContent: @Composable (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (leadingIcon != null) {
                    Icon(
                        imageVector = leadingIcon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            if (trailingContent != null) {
                trailingContent()
            } else if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
    }
}

/**
 * 📜 Sección del historial
 */
@Composable
private fun HistorySection(
    historyEntries: List<HistoryEntry>,
    navController: NavController,
    onEntryClick: (HistoryEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            historyEntries.take(5).forEachIndexed { index, entry ->
                HistoryItem(
                    historyEntry = entry,
                    showNotesIndicator = entry.notes.isNotEmpty(),
                    modifier = Modifier.clickable { onEntryClick(entry) },
                    onShowStats = { medicationId ->
                        if (medicationId.isNotEmpty()) {
                            navController.navigate("medication_stats/$medicationId")
                        }
                    }
                )
                if (index < historyEntries.size - 1 && index < 4) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}
