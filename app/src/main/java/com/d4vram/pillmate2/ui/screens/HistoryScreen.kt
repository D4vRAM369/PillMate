package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.ui.components.HistoryItem

/**
 * 📊 Pantalla del historial completo
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    repository: MedicationRepository = viewModel()
) {
    val historyEntries by repository.historyEntries.collectAsState()
    val stats by repository.stats.collectAsState()

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
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Lista del historial
            items(historyEntries) { entry ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    HistoryItem(historyEntry = entry)
                }
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}