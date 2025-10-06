package com.d4vram.pillmate2.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consumePositionChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.ui.components.HistoryItem
import com.d4vram.pillmate2.ui.components.NotesModal
import com.d4vram.pillmate2.ui.theme.AppUiStyle
import com.d4vram.pillmate2.ui.theme.LocalUiStyle
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt
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
fun HistoryScreen(repository: MedicationRepository, navController: NavController) {
    val historyEntries by repository.historyEntries.collectAsState()
    val stats by repository.stats.collectAsState()

    var selectedPeriod by remember { mutableStateOf(TimePeriod.ALL) }
    var selectedHistoryEntry by remember { mutableStateOf<HistoryEntry?>(null) }

    // Filtrar entradas según el período seleccionado
    val filteredEntries = remember(historyEntries, selectedPeriod) {
        val now = LocalDateTime.now()
        val today = now.toLocalDate()
        when (selectedPeriod) {
            TimePeriod.ALL -> historyEntries
            TimePeriod.TODAY -> historyEntries.filter {
                it.timestamp.toLocalDate() == today
            }
            TimePeriod.WEEK -> historyEntries.filter {
                ChronoUnit.DAYS.between(it.timestamp.toLocalDate(), today) <= 7
            }
            TimePeriod.MONTH -> historyEntries.filter {
                ChronoUnit.DAYS.between(it.timestamp.toLocalDate(), today) <= 30
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
                        onDelete = repository::deleteHistoryEntry,
                        onShowStats = { medicationId ->
                            if (medicationId.isNotEmpty()) {
                                navController.navigate("medication_stats/$medicationId")
                            }
                        }
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
            onSaveChanges = { entryId, notes, newTimestamp ->
                if (notes != entry.notes) {
                    repository.updateHistoryNotes(entryId, notes)
                }
                val sanitizedOriginal = entry.timestamp.withSecond(0).withNano(0)
                if (newTimestamp != sanitizedOriginal) {
                    repository.updateHistoryTimestamp(entryId, newTimestamp)
                }
                selectedHistoryEntry = null
            },
            onDeleteNotes = { entryId ->
                repository.deleteHistoryNotes(entryId)
                selectedHistoryEntry = null
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
    onShowStats: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var offset by remember { mutableFloatStateOf(0f) }
    var animationJob by remember { mutableStateOf<Job?>(null) }
    val uiStyle = LocalUiStyle.current
    val colorScheme = MaterialTheme.colorScheme
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
            val editColor = if (uiStyle == AppUiStyle.MaterialYou) {
                colorScheme.secondaryContainer
            } else {
                Color(0xFF22C55E)
            }
            val editContentColor = if (uiStyle == AppUiStyle.MaterialYou) {
                colorScheme.onSecondaryContainer
            } else {
                Color.White
            }
            val deleteColor = if (uiStyle == AppUiStyle.MaterialYou) {
                colorScheme.errorContainer
            } else {
                Color(0xFFEF4444)
            }
            val deleteContentColor = if (uiStyle == AppUiStyle.MaterialYou) {
                colorScheme.onErrorContainer
            } else {
                Color.White
            }

            HistoryActionButton(
                label = "✏️",
                backgroundColor = editColor,
                contentColor = editContentColor,
                modifier = Modifier.size(width = actionButtonWidth, height = actionButtonHeight)
            ) {
                animateTo(0f)
                onEdit(historyEntry)
            }
            HistoryActionButton(
                label = "🗑️",
                backgroundColor = deleteColor,
                contentColor = deleteContentColor,
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
                showNotesIndicator = historyEntry.notes.isNotEmpty(),
                onShowStats = onShowStats
            )
        }
    }
}

@Composable
private fun HistoryActionButton(
    label: String,
    backgroundColor: Color,
    contentColor: Color = Color.White,
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
            fontSize = 18.sp,
            color = contentColor
        )
    }
}

private fun groupEntriesByPeriod(entries: List<HistoryEntry>): List<Pair<String, List<HistoryEntry>>> {
    if (entries.isEmpty()) return emptyList()

    val today = LocalDate.now()
    val locale = Locale("es", "ES")
    val dateFormatter = DateTimeFormatter.ofPattern("EEE dd/MM/yy", locale)

    val groupedByDate = entries.groupBy { it.timestamp.toLocalDate() }
    val sortedDates = groupedByDate.keys.sortedDescending()

    return sortedDates.map { entryDate ->
        val daysDiff = ChronoUnit.DAYS.between(entryDate, today)
        val label = when (daysDiff) {
            0L -> "Hoy"
            1L -> "Ayer"
            else -> dateFormatter.format(entryDate).replaceFirstChar { it.titlecase(locale) }
        }
        label to groupedByDate.getValue(entryDate).sortedByDescending { it.timestamp }
    }
}