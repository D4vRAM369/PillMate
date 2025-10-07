package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.ui.components.NotesModal
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MedicationStatsScreen(
    medicationId: String?,
    onNavigateBack: () -> Unit,
    repository: MedicationRepository
) {
    if (medicationId != null) {
        val viewModel: MedicationStatsViewModel = viewModel(
            factory = MedicationStatsViewModel.provideFactory(repository, medicationId)
        )
        val filteredHistory by viewModel.filteredHistory.collectAsStateWithLifecycle()
        val selectedPeriod by viewModel.selectedPeriod.collectAsState()
        val displayedMonth by viewModel.displayedMonth.collectAsStateWithLifecycle()
        var selectedDayEntries by remember { mutableStateOf<List<HistoryEntry>?>(null) }
        var entryToEdit by remember { mutableStateOf<HistoryEntry?>(null) }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = "Estadísticas",
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
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .padding(paddingValues)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatsPeriod.values().forEach { period ->
                        FilterChip(
                            selected = selectedPeriod == period,
                            onClick = { viewModel.setPeriod(period) },
                            label = { Text(period.label) }
                        )
                    }
                }

                Text(
                    text = "Número de tomas encontradas: ${filteredHistory.size}",
                    style = MaterialTheme.typography.bodyMedium
                )

                MonthNavigator(
                    month = displayedMonth,
                    onPrevious = viewModel::goToPreviousMonth,
                    onNext = viewModel::goToNextMonth
                )

                CalendarView(
                    month = displayedMonth,
                    history = filteredHistory,
                    onDayClick = { date ->
                        selectedDayEntries = filteredHistory.filter { it.timestamp.toLocalDate() == date }
                    }
                )
            }
        }

        selectedDayEntries?.let { entries ->
            DayDetailsModal(
                entries = entries,
                onDismiss = { selectedDayEntries = null },
                onEditEntry = { entry ->
                    selectedDayEntries = null
                    entryToEdit = entry
                }
            )
        }

        entryToEdit?.let { entry ->
            NotesModal(
                historyEntry = entry,
                onDismiss = { entryToEdit = null },
                onSaveChanges = { entryId, notes, newTimestamp ->
                    if (notes != entry.notes) {
                        repository.updateHistoryNotes(entryId, notes)
                    }
                    val sanitizedOriginal = entry.timestamp.withSecond(0).withNano(0)
                    if (newTimestamp != sanitizedOriginal) {
                        repository.updateHistoryTimestamp(entryId, newTimestamp)
                    }
                    entryToEdit = null
                },
                onDeleteNotes = { entryId ->
                    repository.deleteHistoryNotes(entryId)
                    entryToEdit = null
                }
            )
        }

    } else {
        Scaffold {
            Box(modifier = Modifier.fillMaxSize().padding(it), contentAlignment = Alignment.Center) {
                Text("Error: ID del medicamento no encontrado.")
            }
        }
    }
}

@Composable
private fun CalendarView(
    month: YearMonth,
    history: List<HistoryEntry>,
    onDayClick: (LocalDate) -> Unit
) {
    val daysOfWeek = listOf("L", "M", "X", "J", "V", "S", "D")

    val firstDayOfMonth = month.atDay(1)
    val daysInMonth = month.lengthOfMonth()

    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value
    val emptyCells = (firstDayOfWeek - 1)

    val calendarDays = (1..daysInMonth).toList()

    val historyDates = remember(history, month) {
        history
            .filter { YearMonth.from(it.timestamp) == month }
            .map { it.timestamp.toLocalDate() }
            .toSet()
    }
    val medicationColor = history.firstOrNull()?.color ?: MaterialTheme.colorScheme.secondaryContainer

    LazyVerticalGrid(
        columns = GridCells.Fixed(7),
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(daysOfWeek) { day ->
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        items(emptyCells) {
            Box {}
        }

        items(calendarDays) { day ->
            val date = month.atDay(day)
            val isTaken = date in historyDates

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (isTaken) medicationColor.copy(alpha = 0.8f) else Color.Transparent
                    )
                    .clickable(enabled = isTaken) {
                        onDayClick(date)
                    }
            ) {
                Text(
                    text = day.toString(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isTaken) Color.White else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun MonthNavigator(
    month: YearMonth,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val formatter = remember { DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault()) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = "Mes anterior"
            )
        }

        val monthLabel = remember(month) {
            formatter.format(month).replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
        }
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = "Mes siguiente"
            )
        }
    }
}

@Composable
private fun DayDetailsModal(
    entries: List<HistoryEntry>,
    onDismiss: () -> Unit,
    onEditEntry: (HistoryEntry) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            val medicationName = entries.firstOrNull()?.medicationName ?: "Medicamento"
            Text(
                text = "$medicationName - ${entries.firstOrNull()?.date}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                items(entries) { entry ->
                    Column {
                        Text(
                            text = "Hora: ${entry.time}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Dosis: ${entry.dose}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = { onEditEntry(entry) }
                        ) {
                            Text(
                                text = "✏️ Editar notas",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        if (entry.notes.isNotEmpty()) {
                            Text(
                                text = "Notas: ${entry.notes}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic
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
        }
    )
}
