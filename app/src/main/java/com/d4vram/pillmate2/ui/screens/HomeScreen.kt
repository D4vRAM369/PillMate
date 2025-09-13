package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.background
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.ui.components.HistoryItem
import com.d4vram.pillmate2.ui.components.MedicationCard
import com.d4vram.pillmate2.ui.components.StatCard
import com.d4vram.pillmate2.ui.theme.HeaderGradientEnd
import com.d4vram.pillmate2.ui.theme.HeaderGradientStart

/**
 * 🏠 Pantalla principal de la app
 * Aquí se replica exactamente el diseño del HTML
 */
@Composable
fun HomeScreen(
    repository: MedicationRepository = viewModel(),
    onAddMedication: () -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {}
) {
    // Estados observables del repositorio
    val medications by repository.medications.collectAsState()
    val historyEntries by repository.historyEntries.collectAsState()
    val stats by repository.stats.collectAsState()

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
                subtitle = "Presiona para registrar",
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        item {
            // Grid de medicamentos
            MedicationGrid(
                medications = medications,
                onTakeMedication = { medicationId ->
                    repository.takeMedication(medicationId)
                },
                onAddMedication = onAddMedication,
                onEditMedication = onEditMedication,
                onDeleteMedication = onDeleteMedication,
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
                modifier = Modifier.padding(horizontal = 24.dp)
            )
        }

        item {
            // Espacio para el bottom navigation
            Spacer(modifier = Modifier.height(100.dp))
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
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.horizontalGradient(
                    colors = listOf(HeaderGradientStart, HeaderGradientEnd)
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Column {
            Text(
                text = "💊 PillMate",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White
            )
            Text(
                text = "Seguimiento Personal",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }

        // Botón de agregar medicamento
        FloatingActionButton(
            onClick = onAddMedication,
            modifier = Modifier.align(Alignment.CenterEnd),
            containerColor = Color.White.copy(alpha = 0.2f),
            contentColor = Color.White
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
 * 🔲 Grid de medicamentos (2 columnas)
 */
@Composable
private fun MedicationGrid(
    medications: List<com.d4vram.pillmate2.model.Medication>,
    onTakeMedication: (String) -> Unit,
    onAddMedication: () -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.height((((medications.size + 1) / 2 + 1) * 180).dp),
        userScrollEnabled = false
    ) {
        // Cards de medicamentos
        items(medications) { medication ->
            MedicationCard(
                medication = medication,
                onTakeMedication = onTakeMedication,
                onEditMedication = onEditMedication,
                onDeleteMedication = onDeleteMedication
            )
        }

        // Card de "Agregar medicamento"
        item {
            AddMedicationCard(
                onClick = onAddMedication
            )
        }
    }
}

/**
 * ➕ Card para agregar nuevo medicamento
 */
@Composable
private fun AddMedicationCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3F4F6)
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineLarge,
                color = Color(0xFF6B7280)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Agregar\nMedicamento",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold
                ),
                color = Color(0xFF6B7280)
            )
        }
    }
}

/**
 * 📜 Sección del historial
 */
@Composable
private fun HistorySection(
    historyEntries: List<com.d4vram.pillmate2.model.HistoryEntry>,
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
            historyEntries.forEachIndexed { index, entry ->
                HistoryItem(
                    historyEntry = entry,
                    showNotesIndicator = entry.notes.isNotEmpty()
                )
                if (index < historyEntries.size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                    )
                }
            }
        }
    }
}