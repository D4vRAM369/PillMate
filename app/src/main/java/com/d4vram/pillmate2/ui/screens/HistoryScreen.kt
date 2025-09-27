package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.ui.components.HistoryItem
import com.d4vram.pillmate2.ui.components.NotesModal
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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

                itemsIndexed(entries) { index, entry ->
                    SwipeableHistoryItem(
                        historyEntry = entry,
                        onEntryClick = { selectedHistoryEntry = it },
                        onEdit = { selectedHistoryEntry = it },
                        onDelete = { repository.deleteHistoryEntry(it) }
                    )
                    if (index < entries.lastIndex) {
                        Spacer(modifier = Modifier.height(8.dp))
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

@Composable
private fun SwipeableHistoryItem(
    historyEntry: HistoryEntry,
    onEntryClick: (HistoryEntry) -> Unit,
    onEdit: (HistoryEntry) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val actionCount = 2
    val actionButtonWidth = 56.dp
    val actionSpacing = 8.dp
    val actionButtonHeight = 48.dp
    val rowHorizontalPadding = 16.dp
    val totalSpacing = actionSpacing * (actionCount - 1)
    val totalRevealOffset = (actionButtonWidth * actionCount) + totalSpacing + rowHorizontalPadding
    val density = LocalDensity.current
    val totalRevealOffsetPx = remember(density) { density.run { totalRevealOffset.toPx() } }

    fun animateTo(target: Float) {
        animationJob?.cancel()
        animationJob = scope.launch {
            animate(
                initialValue = offset,
                targetValue = target,
                animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing)
            ) { value, _ ->
                offset = value
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(totalRevealOffsetPx) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { change, dragAmount ->
                        change.consumePositionChange()
                        animationJob?.cancel()
                        val newOffset = (offset + dragAmount).coerceIn(-totalRevealOffsetPx, 0f)
                        offset = newOffset
                    },
                    onDragCancel = { animateTo(0f) },
                    onDragEnd = {
                        val shouldOpen = offset <= -totalRevealOffsetPx * 0.35f
                        animateTo(if (shouldOpen) -totalRevealOffsetPx else 0f)
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .matchParentSize()
                .padding(horizontal = rowHorizontalPadding),
            horizontalArrangement = Arrangement.spacedBy(actionSpacing, Alignment.End),
            verticalAlignment = Alignment.CenterVertically
        ) {
            HistoryActionButton(
                label = "✏️",
                backgroundColor = Color(0xFF22C55E),
                modifier = Modifier.size(width = actionButtonWidth, height = actionButtonHeight)
            ) {
                animateTo(0f)
                onEdit(historyEntry)
            }
            HistoryActionButton(
                label = "🗑️",
                backgroundColor = Color(0xFFEF4444),
                modifier = Modifier.size(width = actionButtonWidth, height = actionButtonHeight)
            ) {
                animateTo(0f)
                onDelete(historyEntry.id)
            }
        }

        Card(
            modifier = Modifier
                .offset { IntOffset(offset.roundToInt(), 0) }
                .fillMaxWidth()
                .clickable {
                    if (offset < 0f) {
                        animateTo(0f)
                    } else {
                        onEntryClick(historyEntry)
                    }
                },
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            HistoryItem(
                historyEntry = historyEntry,
                showNotesIndicator = historyEntry.notes.isNotEmpty()
            )
        }
    }
}

@Composable
private fun HistoryActionButton(
    label: String,
    backgroundColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            fontSize = 18.sp
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
