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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay
import com.d4vram.pillmate2.ui.theme.AppDesign
import com.d4vram.pillmate2.ui.theme.LocalAppDesign
import com.d4vram.pillmate2.ui.timeline.RhythmClock
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
    // El orden de aquí es el orden de los chips en pantalla
    TODAY("Hoy"),
    YESTERDAY("Ayer"),
    WEEK("7 días"),
    MONTH("30 días"),
    ALL("Todo")
}

/**
 * ¿Entra [date] en el periodo, siendo [today] el día actual? Ventanas móviles e iguales a las de
 * Estadísticas: "7 días" es hoy y los 6 anteriores.
 */
internal fun TimePeriod.includes(date: LocalDate, today: LocalDate): Boolean = when (this) {
    TimePeriod.TODAY -> date == today
    TimePeriod.YESTERDAY -> date == today.minusDays(1)
    TimePeriod.WEEK -> date in today.minusDays(6)..today
    TimePeriod.MONTH -> date in today.minusDays(29)..today
    TimePeriod.ALL -> true
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: MedicationRepository,
    navController: NavController,
    initialDay: LocalDate? = null // Si llega (desde el detalle de un día en Estadísticas), se abre mostrando solo ese día.
) {
    val historyEntries by repository.historyEntries.collectAsState()
    val stats by repository.stats.collectAsState()

    val astra = LocalAppDesign.current == AppDesign.Astra
    // Re-evaluate calendar filters while the screen stays open across midnight.
    val today by produceState(LocalDate.now()) {
        while (true) {
            value = LocalDate.now()
            delay(30_000)
        }
    }
    var selectedPeriod by remember(astra) { mutableStateOf(if (astra) TimePeriod.TODAY else TimePeriod.ALL) }
    // Día concreto: manda sobre el periodo hasta que se quita o se elige un periodo.
    var selectedDay by remember { mutableStateOf(initialDay) }
    var selectedHistoryEntry by remember { mutableStateOf<HistoryEntry?>(null) }
    var isSearchVisible by remember { mutableStateOf(false) }
    var notesSearchQuery by remember { mutableStateOf("") }

    // Filtrar entradas según el período seleccionado
    val entriesByPeriod = remember(historyEntries, selectedPeriod, selectedDay, today) {
        val day = selectedDay
        if (day != null) historyEntries.filter { it.timestamp.toLocalDate() == day }
        else historyEntries.filter { selectedPeriod.includes(it.timestamp.toLocalDate(), today) }
    }

    // Filtrar por búsqueda en notas (case-insensitive)
    val filteredEntries = remember(entriesByPeriod, notesSearchQuery) {
        val normalizedQuery = notesSearchQuery.trim().lowercase(Locale.getDefault())
        if (normalizedQuery.isBlank()) {
            entriesByPeriod
        } else {
            entriesByPeriod.filter { entry ->
                entry.notes.isNotBlank() && entry.notes.lowercase(Locale.getDefault()).contains(normalizedQuery)
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
                        text = if (astra) "Historial" else "📊 Historial",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                },
                actions = {
                    IconButton(
                        onClick = {
                            isSearchVisible = !isSearchVisible
                            if (!isSearchVisible) notesSearchQuery = ""
                        }
                    ) {
                        Icon(
                            imageVector = if (isSearchVisible) Icons.Default.Close else Icons.Default.Search,
                            contentDescription = if (isSearchVisible) "Cerrar búsqueda" else "Buscar en notas"
                        )
                    }
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
                if (!astra) StatisticsQuickCard(
                    takenToday = stats.takenToday,
                    takenThisWeek = stats.takenThisWeek,
                    onClick = { navController.navigate("global_stats") }
                )
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))

                // Filtros de período
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedDay?.let { day ->
                        item {
                            FilterChip(
                                onClick = { selectedDay = null },
                                label = { Text(day.format(DateTimeFormatter.ofPattern("EEE d/M", Locale("es", "ES")))) },
                                selected = true,
                                trailingIcon = {
                                    Icon(Icons.Default.Close, contentDescription = "Quitar filtro de día", modifier = Modifier.size(18.dp))
                                }
                            )
                        }
                    }
                    items(TimePeriod.values()) { period ->
                        FilterChip(
                            onClick = { selectedDay = null; selectedPeriod = period },
                            label = { Text(period.label) },
                            selected = selectedDay == null && selectedPeriod == period
                        )
                    }
                }
            }

            if (astra) {
                item {
                    // El reloj sigue al filtro: un día → puntos; varios días → barras por hora.
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Tu ritmo · " + (selectedDay?.format(DateTimeFormatter.ofPattern("d/M")) ?: selectedPeriod.label.lowercase()),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Cada marca es una toma registrada, no un objetivo.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )
                        RhythmClock(entriesByPeriod)
                        androidx.compose.material3.TextButton(onClick = { navController.navigate("global_stats") }) {
                            Text("Ver estadísticas")
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }

            if (isSearchVisible) {
                item {
                    OutlinedTextField(
                        value = notesSearchQuery,
                        onValueChange = { notesSearchQuery = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("Buscar en notas del historial...") },
                        singleLine = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = null
                            )
                        },
                        trailingIcon = {
                            if (notesSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { notesSearchQuery = "" }) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Limpiar búsqueda"
                                    )
                                }
                            }
                        },
                        colors = TextFieldDefaults.colors()
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }

                item {
                    val resultText = if (notesSearchQuery.isBlank()) {
                        "Escribe para filtrar por contenido de notas"
                    } else {
                        "${filteredEntries.size} resultado(s) en notas"
                    }
                    Text(
                        text = resultText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            // Lista del historial agrupada
            if (groupedEntries.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        )
                    ) {
                        Text(
                            text = if (notesSearchQuery.isNotBlank()) {
                                "No hay notas que coincidan con \"$notesSearchQuery\""
                            } else {
                                "No hay registros para este filtro"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

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
                    Row(verticalAlignment = Alignment.Top) {
                        if (astra) {
                            Column(
                                Modifier.width(52.dp).padding(top = 16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(entry.timestamp.format(DateTimeFormatter.ofPattern("HH:mm")),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.height(8.dp))
                                Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
                                Box(Modifier.width(2.dp).height(64.dp).background(MaterialTheme.colorScheme.outlineVariant))
                            }
                        }
                        SwipeableHistoryItem(
                        modifier = Modifier.weight(1f),
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
                    }
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
            onSaveChanges = { entryId, notes, newTimestamp, audioNoteUri ->
                if (notes != entry.notes || audioNoteUri != entry.audioNoteUri) {
                    repository.updateHistoryNotes(entryId, notes, audioNoteUri)
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
                        change.consume()
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
                hasAudioNote = !historyEntry.audioNoteUri.isNullOrBlank(),
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

/**
 * 📊 Card clickeable para acceder a estadísticas desde el historial
 * Muestra un resumen rápido y permite navegar a la pantalla completa
 */
@Composable
private fun StatisticsQuickCard(
    takenToday: Int,
    takenThisWeek: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Icono con fondo circular
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                            shape = CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "📊",
                        style = MaterialTheme.typography.titleMedium
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = "Ver Estadísticas",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "Hoy: $takenToday • Semana: $takenThisWeek",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }

            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Ver estadísticas",
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
            )
        }
    }
}
