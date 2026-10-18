package com.d4vram.pillmate2.ui.screens

import com.d4vram.pillmate2.R
import androidx.compose.ui.res.stringResource
import com.d4vram.pillmate2.widget.fittedNameSp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.runtime.saveable.rememberSaveable
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
    if (com.d4vram.pillmate2.ui.theme.LocalAppDesign.current == com.d4vram.pillmate2.ui.theme.AppDesign.Astra) {
        AstraHomeScreen(repository, navController, themeManager, onAddMedication, onEditMedication, onDeleteMedication)
        return
    }
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

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }

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
                            repository.takeMedication(medicationId, quantity = 1.0, customTimestamp = null)
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
                    title = stringResource(R.string.home_my_medications),
                    leadingIcon = Icons.Default.Medication,
                    modifier = Modifier.padding(horizontal = 24.dp),
                    trailingContent = {
                        IconButton(
                            onClick = { scope.launch { themeManager.setListView(!isListView) } },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isListView)
                                    Icons.Default.GridView
                                else
                                    Icons.AutoMirrored.Filled.ViewList,
                                contentDescription = stringResource(if (isListView) R.string.home_view_as_grid else R.string.home_view_as_list),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                )
            }

            if (filteredMedications.isEmpty()) {
                item {
                    EmptyMedicationState(
                        searching = searchQuery.isNotBlank(),
                        onAction = { if (searchQuery.isNotBlank()) searchQuery = "" else onAddMedication() },
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }
            } else if (isListView) {
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
                            if (sortMode == SortMode.MANUAL && searchQuery.isBlank()) {
                                repository.reorderMedications(fromIndex, toIndex)
                            }
                        },
                        // Los índices filtrados no corresponden al orden completo del repositorio.
                        dragEnabled = sortMode == SortMode.MANUAL && searchQuery.isBlank(),
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
                    title = stringResource(R.string.home_recent_history),
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
                title = { Text(text = stringResource(R.string.home_notes_title)) },
                text = {
                    val notesText = entry.notes.ifBlank { stringResource(R.string.home_notes_none) }
                    Text(notesText)
                },
                confirmButton = {
                    TextButton(onClick = { selectedHistoryEntry = null }) {
                        Text(stringResource(R.string.action_close))
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
internal fun HeaderSection(
    onAddMedication: () -> Unit,
    searchQuery: String,
    searchActive: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit
) {
    val materialYou = LocalUiStyle.current == AppUiStyle.MaterialYou
    val contentColor = if (materialYou) MaterialTheme.colorScheme.onPrimaryContainer else Color.White
    val headerModifier = if (materialYou) {
        Modifier.background(MaterialTheme.colorScheme.primaryContainer)
    } else {
        Modifier.background(Brush.horizontalGradient(listOf(HeaderGradientStart, HeaderGradientEnd)))
    }

    // La búsqueda ocupa su propia fila para que nunca quede debajo del botón de añadir.
    Column(modifier = Modifier.fillMaxWidth().then(headerModifier).padding(horizontal = 24.dp, vertical = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text("PillMate", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = contentColor)
                Text(stringResource(R.string.home_subtitle), style = MaterialTheme.typography.bodyMedium, color = contentColor.copy(alpha = 0.9f))
            }
            if (!searchActive) {
                IconButton(onClick = { onSearchActiveChange(true) }, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Search, contentDescription = stringResource(R.string.action_search), tint = contentColor)
                }
            }
            FloatingActionButton(
                onClick = onAddMedication,
                modifier = Modifier.size(48.dp),
                containerColor = if (materialYou) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.2f),
                contentColor = if (materialYou) MaterialTheme.colorScheme.onPrimary else Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_medication))
            }
        }
        AnimatedVisibility(visible = searchActive) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text(stringResource(R.string.home_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = { onSearchActiveChange(false) }) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.history_search_close))
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                shape = RoundedCornerShape(28.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = contentColor,
                    unfocusedTextColor = contentColor,
                    focusedPlaceholderColor = contentColor.copy(alpha = 0.8f),
                    unfocusedPlaceholderColor = contentColor.copy(alpha = 0.8f),
                    focusedLeadingIconColor = contentColor,
                    unfocusedLeadingIconColor = contentColor,
                    focusedTrailingIconColor = contentColor,
                    unfocusedTrailingIconColor = contentColor,
                    focusedBorderColor = contentColor,
                    unfocusedBorderColor = contentColor.copy(alpha = 0.6f),
                    cursorColor = contentColor
                )
            )
        }
    }
}

@Composable
internal fun EmptyMedicationState(searching: Boolean, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(if (searching) R.string.home_empty_no_results else R.string.home_empty_first), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(if (searching) R.string.home_empty_no_results_hint else R.string.home_empty_first_hint),
                style = MaterialTheme.typography.bodyMedium
            )
            TextButton(onClick = onAction) {
                Text(stringResource(if (searching) R.string.home_clear_search else R.string.home_add_medication))
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
                        text = stringResource(R.string.home_global_stats),
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.home_global_stats_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = stringResource(R.string.history_view_stats),
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
            title = stringResource(R.string.quick_take_title),
            subtitle = stringResource(R.string.quick_take_slots),
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
            .width(200.dp)
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
                    val name = medication?.name ?: stringResource(R.string.quick_take_choose)
                    Text(
                        text = name,
                        // 92 dp = ancho de la tarjeta menos márgenes, avatar y botón de editar
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                            fontSize = fittedNameSp(name, 14, 92f, 1, LocalDensity.current.fontScale).sp
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = medication?.dose ?: stringResource(R.string.quick_take_slot, slotNumber),
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
                        contentDescription = stringResource(R.string.quick_take_slot_configure, slotNumber),
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
                    text = stringResource(if (medication == null) R.string.quick_take_unassigned else R.string.quick_take_one_dose),
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
                        text = stringResource(if (medication == null) R.string.quick_take_setup else R.string.quick_take_take),
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
internal fun QuickTakeSlotPickerDialog(
    slotNumber: Int,
    medications: List<Medication>,
    selectedMedicationId: String?,
    onSelectMedication: (String) -> Unit,
    onClearSlot: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.quick_take_slot, slotNumber)) },
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
                                contentDescription = stringResource(R.string.quick_take_selected),
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
                Text(stringResource(R.string.action_close))
            }
        },
        dismissButton = {
            TextButton(onClick = onClearSlot) {
                Text(stringResource(R.string.quick_take_clear))
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
