package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.ui.components.HistoryItem
import com.d4vram.pillmate2.ui.components.NotesModal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit

/**
 * 📊 Pantalla del historial completo
 */
enum class TimePeriod(val label: String) {
    ALL("Todo"),
    TODAY("Hoy"),
    WEEK("Esta semana"),
    MONTH("Este mes")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: MedicationRepository
) {
    val historyEntries by repository.historyEntries.collectAsState()
    val stats by repository.stats.collectAsState()

    var selectedPeriod by remember { mutableStateOf(TimePeriod.ALL) }
    var selectedHistoryEntry by remember { mutableStateOf<HistoryEntry?>(null) }

    // Filtrar entradas según el período seleccionado
    val filteredEntries = remember(historyEntries, selectedPeriod) {
        val now = LocalDateTime.now()
        when (selectedPeriod) {
            TimePeriod.ALL -> historyEntries
            TimePeriod.TODAY -> historyEntries.filter {
                ChronoUnit.DAYS.between(it.timestamp, now) == 0L
            }
            TimePeriod.WEEK -> historyEntries.filter {
                ChronoUnit.DAYS.between(it.timestamp, now) <= 7
            }
            TimePeriod.MONTH -> historyEntries.filter {
                ChronoUnit.DAYS.between(it.timestamp, now) <= 30
            }
        }
    }

    // Agrupar entradas por período
    val groupedEntries = remember(filteredEntries) {
        groupEntriesByPeriod(filteredEntries)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "📊 Historial",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                // Resumen de estadísticas
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "📈 Resumen",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Tomadas hoy: ${stats.takenToday} • Esta semana: ${stats.takenThisWeek} • Adherencia: ${stats.adherencePercentage}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                // Filtros de período
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(TimePeriod.values()) { period ->
                        FilterChip(
                            onClick = { selectedPeriod = period },
                            label = { Text(period.label) },
                            selected = selectedPeriod == period
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Lista del historial agrupada
            groupedEntries.forEach { (periodLabel, entries) ->
                item {
                    // Encabezado del grupo
                    Text(
                        text = periodLabel,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                items(entries) { entry ->
                    val dismissState = rememberSwipeToDismissBoxState(
                        confirmValueChange = { value ->
                            value == SwipeToDismissBoxValue.Settled // No permitir dismiss
                        }
                    )

                    SwipeToDismissBox(
                        state = dismissState,
                        backgroundContent = {
                            val isSwiped = dismissState.targetValue != SwipeToDismissBoxValue.Settled
                            if (isSwiped) {
                                Box(Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.2f))) {
                                    Row(
                                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        Surface(
                                            color = Color.Green,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.clickable { selectedHistoryEntry = entry }
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                                Text("✏️", fontSize = 16.sp)
                                            }
                                        }
                                        Spacer(Modifier.width(8.dp))
                                        Surface(
                                            color = Color.Red,
                                            shape = RoundedCornerShape(4.dp),
                                            modifier = Modifier.clickable { repository.deleteHistoryEntry(entry.id) }
                                        ) {
                                            Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(8.dp)) {
                                                Text("🗑️", fontSize = 16.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().clickable { selectedHistoryEntry = entry },
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            HistoryItem(
                                historyEntry = entry,
                                showNotesIndicator = entry.notes.isNotEmpty()
                            )
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }

    // Modal de notas
    selectedHistoryEntry?.let { entry ->
        NotesModal(
            historyEntry = entry,
            onDismiss = { selectedHistoryEntry = null },
            onSaveNotes = { entryId, notes ->
                repository.updateHistoryNotes(entryId, notes)
                selectedHistoryEntry = null
            },
            onDeleteNotes = { entryId ->
                repository.deleteHistoryNotes(entryId)
            }
        )
    }
}

private fun groupEntriesByPeriod(entries: List<HistoryEntry>): List<Pair<String, List<HistoryEntry>>> {
    val now = LocalDateTime.now()
    val grouped = mutableMapOf<String, MutableList<HistoryEntry>>()

    entries.forEach { entry ->
        val daysDiff = ChronoUnit.DAYS.between(entry.timestamp, now)
        val group = when {
            daysDiff == 0L -> "Hoy"
            daysDiff == 1L -> "Ayer"
            daysDiff <= 7 -> "Esta semana"
            daysDiff <= 30 -> "Este mes"
            else -> "Anteriores"
        }

        grouped.getOrPut(group) { mutableListOf() }.add(entry)
    }

    // Ordenar grupos por relevancia temporal
    val orderedGroups = listOf("Hoy", "Ayer", "Esta semana", "Este mes", "Anteriores")
    return orderedGroups.mapNotNull { group ->
        grouped[group]?.let { group to it.sortedByDescending { it.timestamp } }
    }
}
