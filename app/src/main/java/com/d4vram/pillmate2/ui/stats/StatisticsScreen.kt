package com.d4vram.pillmate2.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.ui.stats.viewmodel.StatisticsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    repository: MedicationRepository
) {
    val viewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModel.provideFactory(repository)
    )

    val adherenceData by viewModel.adherenceLast7Days.collectAsState()
    val mostTaken by viewModel.mostTakenMedications.collectAsState()
    val stats by viewModel.stats.collectAsState()
    val selectedMedicationId by viewModel.selectedMedicationId.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Estadísticas Globales",
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
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Resumen General
            SummaryCards(stats)

            // Gráfica de Adherencia Semanal (Custom Canvas)
            if (adherenceData.isNotEmpty()) {
                // Buscar nombre y color del medicamento seleccionado
                val selectedMedication = mostTaken.find { it.first.id == selectedMedicationId }?.first
                
                val chartTitle = if (selectedMedication != null) 
                    "Adherencia: ${selectedMedication.name}" 
                else 
                    "Adherencia Global"
                
                // Usar color del medicamento o el primario por defecto
                val chartColor = selectedMedication?.color ?: MaterialTheme.colorScheme.primary

                AdherenceChart(
                    data = adherenceData,
                    title = chartTitle,
                    barColor = chartColor
                )
            }

            // Top Medicamentos
            if (mostTaken.isNotEmpty()) {
                TopMedicationsList(
                    medications = mostTaken,
                    selectedMedicationId = selectedMedicationId,
                    onMedicationClick = { medicationId ->
                        viewModel.selectMedication(medicationId)
                    }
                )
            }
        }
    }
}

@Composable
fun SummaryCards(stats: MedicationRepository.Stats) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        SummaryCard(
            title = "Hoy",
            value = stats.takenToday.toString(),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.primaryContainer
        )
        SummaryCard(
            title = "Semana",
            value = stats.takenThisWeek.toString(),
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.secondaryContainer
        )
        SummaryCard(
            title = "Adherencia",
            value = "${stats.adherencePercentage}%",
            modifier = Modifier.weight(1f),
            color = if (stats.adherencePercentage >= 80) 
                Color(0xFFDCFCE7) // Green container
            else 
                MaterialTheme.colorScheme.errorContainer,
            textColor = if (stats.adherencePercentage >= 80)
                Color(0xFF14532D) // Green text
            else
                MaterialTheme.colorScheme.onErrorContainer
        )
    }
}

@Composable
fun SummaryCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    color: Color,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = modifier.clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = textColor.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
fun TopMedicationsList(
    medications: List<Pair<Medication, Int>>,
    selectedMedicationId: String? = null,
    onMedicationClick: (String) -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = "Más Tomados",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            medications.forEach { (medication, count) ->
                val isSelected = medication.id == selectedMedicationId
                val backgroundColor = if (isSelected) 
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) 
                else 
                    Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                        .background(backgroundColor)
                        .clickable { onMedicationClick(medication.id) }
                        .padding(vertical = 8.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(medication.color.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = medication.emoji)
                    }
                    
                    Spacer(modifier = Modifier.size(12.dp))
                    
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = medication.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = medication.dose,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    
                    Text(
                        text = "$count tomas",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
