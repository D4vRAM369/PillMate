package com.d4vram.pillmate2.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.HistoryEntry
import java.time.LocalDate
import java.time.LocalDateTime

@OptIn(ExperimentalMaterial3Api::class)
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
        val history by viewModel.history.collectAsStateWithLifecycle()

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
                Text(
                    text = "Número de tomas encontradas: ${history.size}",
                    style = MaterialTheme.typography.bodyMedium
                )

                CalendarView(history = history)
            }
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
private fun CalendarView(history: List<HistoryEntry>) {
    val daysOfWeek = listOf("L", "M", "X", "J", "V", "S", "D")

    val currentDateTime = LocalDateTime.now()
    val year = currentDateTime.year
    val month = currentDateTime.month

    val firstDayOfMonth = LocalDate.of(year, month, 1)
    val daysInMonth = month.length(firstDayOfMonth.isLeapYear)

    val firstDayOfWeek = firstDayOfMonth.dayOfWeek.value
    val emptyCells = (firstDayOfWeek - 1)

    val calendarDays = (1..daysInMonth).toList()

    val historyDates = remember(history) {
        history.map { it.timestamp.toLocalDate() }.toSet()
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
            val date = LocalDate.of(year, month, day)
            val isTaken = date in historyDates

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .aspectRatio(1f)
                    .clip(CircleShape)
                    .background(
                        if (isTaken) medicationColor.copy(alpha = 0.8f) else Color.Transparent
                    )
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
