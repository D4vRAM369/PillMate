package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.ui.components.MedicationListItem
import com.d4vram.pillmate2.ui.components.ReorderableMedicationGrid
import com.d4vram.pillmate2.ui.theme.ThemeManager
import kotlinx.coroutines.launch

/**
 * Pantalla de gestión de medicamentos
 * - SearchBar persistente para filtrar por nombre/dosis/tipo
 * - Toggle grid (tarjetas cuadradas) <-> lista (filas con color-stripe)
 * - Preferencia de vista persiste en DataStore vía ThemeManager
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(
    repository: MedicationRepository,
    themeManager: ThemeManager,
    onAddMedication: () -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {}
) {
    val medications by repository.medications.collectAsState()
    val chipCountPeriod by themeManager.chipCountPeriod.collectAsState(
        initial = com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Total
    )
    val isListView by themeManager.isListView.collectAsState(initial = false)
    val gridListState = rememberLazyListState()
    val itemListState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    var searchQuery by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }

    val filteredMedications = remember(medications, searchQuery) {
        if (searchQuery.isBlank()) medications
        else medications.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
            it.dose.contains(searchQuery, ignoreCase = true) ||
            it.type.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = "Mis Medicamentos",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold
                            )
                        )
                    },
                    actions = {
                        // Toggle Grid <-> Lista
                        IconButton(onClick = {
                            scope.launch { themeManager.setListView(!isListView) }
                        }) {
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

                // Barra de búsqueda persistente
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Buscar medicamento...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Borrar búsqueda"
                                )
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        focusedBorderColor = MaterialTheme.colorScheme.primary
                    )
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddMedication,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Agregar medicamento",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        if (filteredMedications.isEmpty()) {
            EmptyMedicationState(
                searching = searchQuery.isNotBlank(),
                onAction = { if (searchQuery.isNotBlank()) searchQuery = "" else onAddMedication() },
                modifier = Modifier.padding(paddingValues).padding(16.dp)
            )
        } else if (isListView) {
            // VISTA LISTA — filas con color-stripe
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                state = itemListState
            ) {
                item { Spacer(modifier = Modifier.height(4.dp)) }
                items(filteredMedications, key = { it.id }) { medication ->
                    MedicationListItem(
                        medication = medication,
                        repository = repository,
                        chipCountPeriod = chipCountPeriod,
                        onTakeMedication = { medicationId, quantity, timestamp ->
                            repository.takeMedication(medicationId, quantity, timestamp)
                        },
                        onEditMedication = onEditMedication,
                        onDeleteMedication = onDeleteMedication
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        } else {
            // VISTA GRID — tarjetas cuadradas reordenables (comportamiento original)
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
                state = gridListState
            ) {
                item { Spacer(modifier = Modifier.height(8.dp)) }
                item {
                    ReorderableMedicationGrid(
                        medications = filteredMedications,
                        repository = repository,
                        chipCountPeriod = chipCountPeriod,
                        onTakeMedication = { medicationId, quantity, timestamp ->
                            repository.takeMedication(medicationId, quantity, timestamp)
                        },
                        onEditMedication = onEditMedication,
                        onDeleteMedication = onDeleteMedication,
                        onAddMedication = onAddMedication,
                        onReorder = { fromIndex, toIndex ->
                            if (searchQuery.isBlank()) repository.reorderMedications(fromIndex, toIndex)
                        },
                        dragEnabled = searchQuery.isBlank(),
                        parentListState = gridListState,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }
}
