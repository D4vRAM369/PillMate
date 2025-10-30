package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.ui.components.ReorderableMedicationGrid

/**
 * 💊 Pantalla de gestión de medicamentos
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationsScreen(
    repository: MedicationRepository,
    themeManager: com.d4vram.pillmate2.ui.theme.ThemeManager,
    onAddMedication: () -> Unit,
    onEditMedication: (String) -> Unit = {},
    onDeleteMedication: (String) -> Unit = {}
) {
    val medications by repository.medications.collectAsState()
    val chipCountPeriod by themeManager.chipCountPeriod.collectAsState(initial = com.d4vram.pillmate2.ui.theme.ChipCountPeriod.Total)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "💊 Mis Medicamentos",
                        style = MaterialTheme.typography.headlineMedium.copy(
                            fontWeight = FontWeight.Bold
                        )
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAddMedication,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Text(
                    text = "+",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(8.dp))
            }

            item {
                ReorderableMedicationGrid(
                    medications = medications,
                    repository = repository,
                    chipCountPeriod = chipCountPeriod,
                    onTakeMedication = { medicationId, quantity ->
                        repository.takeMedication(medicationId, quantity)
                    },
                    onEditMedication = onEditMedication,
                    onDeleteMedication = onDeleteMedication,
                    onAddMedication = onAddMedication,
                    onReorder = { fromIndex, toIndex ->
                        repository.reorderMedications(fromIndex, toIndex)
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}
