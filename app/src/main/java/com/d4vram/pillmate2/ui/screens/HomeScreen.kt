package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.ui.components.HistoryItem
import com.d4vram.pillmate2.ui.components.MedicationCard
import com.d4vram.pillmate2.ui.components.ReorderableMedicationGrid
import com.d4vram.pillmate2.ui.components.StatCard
import com.d4vram.pillmate2.ui.theme.HeaderGradientEnd
import com.d4vram.pillmate2.ui.theme.HeaderGradientStart
import com.d4vram.pillmate2.ui.theme.AppUiStyle
import com.d4vram.pillmate2.ui.theme.LocalUiStyle

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
    val medications by repository.medications.collectAsState()
    val historyEntries by repository.historyEntries.collectAsState()
    val stats by repository.stats.collectAsState()
    val chipCountPeriod by themeManager.chipCountPeriod.collectAsState(initial = com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Total)

    // Estado para el modal de notas / entrada seleccionada
    var selectedHistoryEntry by remember { mutableStateOf<HistoryEntry?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                // Header con gradiente verde
                HeaderSection(
                    onAddMedication = onAddMedication
                )
            }

            item {
                // Fila de estadísticas
                StatsSection(
                    stats = stats,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
                )
            }

            item {
                // Título de medicamentos
                SectionTitle(
                    title = "🏠 Mis Medicamentos",
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            item {
                // Grid de medicamentos reordenable
                ReorderableMedicationGrid(
                    medications = medications,
                    repository = repository,
                    chipCountPeriod = chipCountPeriod,
                    onTakeMedication = { medicationId, quantity ->
                        repository.takeMedication(medicationId, quantity)
                    },
                    onAddMedication = onAddMedication,
                    onEditMedication = onEditMedication,
                    onDeleteMedication = onDeleteMedication,
                    onReorder = { fromIndex, toIndex ->
                        repository.reorderMedications(fromIndex, toIndex)
                    },
                    modifier = Modifier.padding(horizontal = 24.dp)
                )
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }

            item {
                // Título del historial
                SectionTitle(
                    title = "📊 Historial Reciente",
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
    onAddMedication: () -> Unit
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
                text = "💊 PillMate",
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
        }

        FloatingActionButton(
            onClick = onAddMedication,
            modifier = Modifier.align(Alignment.CenterEnd),
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
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}

/**
 * 📊 Sección de estadísticas (3 cards horizontales)
 */
@Composable
private fun StatsSection(
    stats: MedicationRepository.Stats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            number = stats.takenToday.toString(),
            label = "Tomadas Hoy",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            number = stats.takenThisWeek.toString(),
            label = "Esta Semana",
            modifier = Modifier.weight(1f)
        )
        StatCard(
            number = "${stats.adherencePercentage}%",
            label = "Adherencia",
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 🏷️ Título de sección con subtitle opcional
 */
@Composable
private fun SectionTitle(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
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