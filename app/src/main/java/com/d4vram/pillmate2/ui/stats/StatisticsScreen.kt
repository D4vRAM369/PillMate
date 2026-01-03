package com.d4vram.pillmate2.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.ui.components.StatCard
import com.d4vram.pillmate2.ui.stats.viewmodel.FilterRange
import com.d4vram.pillmate2.ui.stats.viewmodel.StatisticsViewModel
import com.d4vram.pillmate2.ui.stats.viewmodel.StatisticsUiState
import com.d4vram.pillmate2.utils.StatsExporter
import com.d4vram.pillmate2.ui.stats.ActivityPatternChart
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.column.columnChart
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.entryOf
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    repository: MedicationRepository,
    navController: androidx.navigation.NavController? = null
) {
    val viewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModel.provideFactory(repository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Estadísticas",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        if (uiState.dateRangeText.isNotEmpty()) {
                            Text(
                                text = uiState.dateRangeText,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val exporter = StatsExporter(context)
                                if (exporter.isExternalStorageWritable()) {
                                    val file = exporter.exportSummaryToCSV(
                                        totalDoses = uiState.totalDosesLogged,
                                        averageDaily = uiState.averageDailyDoses,
                                        medications = uiState.availableMedications,
                                        history = uiState.recentHistory
                                    )
                                    if (file != null) {
                                        try {
                                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                                context,
                                                "${context.packageName}.provider",
                                                file
                                            )
                                            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                                type = "text/csv"
                                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                putExtra(android.content.Intent.EXTRA_SUBJECT, "Estadísticas PillMate")
                                                putExtra(
                                                    android.content.Intent.EXTRA_TEXT,
                                                    "Adjunto reporte de estadísticas de PillMate."
                                                )
                                            }
                                            context.startActivity(
                                                android.content.Intent.createChooser(intent, "Compartir reporte")
                                            )
                                        } catch (e: Exception) {
                                            snackbarHostState.showSnackbar("Error al compartir: ${e.message}")
                                            e.printStackTrace()
                                        }
                                    } else {
                                        snackbarHostState.showSnackbar("Error al exportar datos")
                                    }
                                } else {
                                    snackbarHostState.showSnackbar("Almacenamiento no disponible")
                                }
                            }
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = "Exportar datos"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            FilterButtons(
                activeFilter = uiState.activeFilter,
                onFilterSelected = { range -> viewModel.setFilter(range) }
            )

            if (uiState.availableMedications.isNotEmpty()) {
                MedicationFilterDropdown(
                    medications = uiState.availableMedications,
                    selectedId = uiState.selectedMedicationId,
                    onMedicationSelected = { id -> viewModel.selectMedication(id) }
                )
            }

            if (uiState.activeFilter != FilterRange.ALL_TIME) {
                DateNavigation(
                    dateRangeText = uiState.dateRangeText,
                    onPrevious = { viewModel.previousPeriod() },
                    onNext = { viewModel.nextPeriod() }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 🔥 DASHBOARD AVANZADO CON KPIs DESTACADOS 🔥

            // KPI Principal - Total de dosis
            ElevatedCard(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.elevatedCardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Total Registrado",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = uiState.totalDosesLogged.toString(),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = "dosis en el período",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                        )
                    }
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(64.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "📊",
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Grid de métricas principales
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    number = uiState.averageDailyDoses,
                    label = "Promedio Diario",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    number = uiState.totalDaysWithRecords.toString(),
                    label = "Días con Registros",
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    number = uiState.loggingConsistency,
                    label = "Consistencia",
                    modifier = Modifier.weight(1f)
                )
                StatCard(
                    number = uiState.timeSinceLastRecord,
                    label = "Último Registro",
                    modifier = Modifier.weight(1f)
                )
            }

            // 🔥 NARRATIVA HUMANA 🔥
            if (uiState.narrativeSummary.isNotEmpty()) {
                NarrativeSummaryCard(summary = uiState.narrativeSummary)
            }

            // 🔥 GRÁFICA DE PATRONES DE ACTIVIDAD 🔥
            if (uiState.activityData.isNotEmpty()) {
                ActivityPatternChart(data = uiState.activityData)
            }

            // Historial Reciente
            if (uiState.recentHistory.isNotEmpty()) {
                RecentHistoryList(history = uiState.recentHistory)
            }

            // Resumen de Actividad
            ActivitySummaryCard(
                totalDosesLogged = uiState.totalDosesLogged,
                averageDailyDoses = uiState.averageDailyDoses,
                mostActiveDay = uiState.mostActiveDay,
                leastActiveDay = uiState.leastActiveDay,
                loggingConsistency = uiState.loggingConsistency,
                totalDaysWithRecords = uiState.totalDaysWithRecords,
                totalPeriodDays = calculateTotalDays(uiState.dateRangeText)
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (uiState.hourlyPattern.isNotEmpty()) {
                HourlyDistributionChart(hourlyPattern = uiState.hourlyPattern)
            }

            // Top Medicamentos
            if (uiState.topMedications.isNotEmpty()) {
                TopMedicationsList(medications = uiState.topMedications)
            }
        }
    }
}

@Composable
fun HourlyDistributionChart(hourlyPattern: Map<String, Int>) {
    val periods = listOf("Madrugada", "Mañana", "Tarde", "Noche")
    // Mapear los datos al orden cronológico correcto y asegurar que existan todas las barras
    val chartEntryModelProducer = remember(hourlyPattern) {
        val entries = periods.mapIndexed { index, period ->
            val count = hourlyPattern[period] ?: 0
            entryOf(index.toFloat(), count.toFloat())
        }
        ChartEntryModelProducer(entries)
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Column {
            Text(
                text = "⏰ Distribución Horaria",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Text(
                text = "Momentos del día con mayor actividad",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            ProvideChartStyle {
                Chart(
                    chart = columnChart(
                        columns = listOf(
                            com.patrykandpatrick.vico.compose.component.lineComponent(
                                color = MaterialTheme.colorScheme.tertiary,
                                thickness = 16.dp,
                                shape = com.patrykandpatrick.vico.core.component.shape.Shapes.roundedCornerShape(
                                    topLeftPercent = 20,
                                    topRightPercent = 20
                                )
                            )
                        )
                    ),
                    chartModelProducer = chartEntryModelProducer,
                    startAxis = rememberStartAxis(
                        valueFormatter = { value, _ ->
                            value.toInt().toString()
                        }
                    ),
                    bottomAxis = rememberBottomAxis(
                        valueFormatter = { value, _ ->
                            val index = value.toInt()
                            if (index >= 0 && index < periods.size) {
                                periods[index]
                            } else {
                                ""
                            }
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .padding(16.dp)
                )
            }
        }
    }
}

@Composable
fun NarrativeSummaryCard(summary: String) {
    androidx.compose.material3.ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "💬",
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun ActivitySummaryCard(
    totalDosesLogged: Int,
    averageDailyDoses: String,
    mostActiveDay: String,
    leastActiveDay: String,
    loggingConsistency: String,
    totalDaysWithRecords: Int,
    totalPeriodDays: Int
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Resumen de Actividad",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // 🔥 Indicador visual de actividad total 🔥
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = androidx.compose.foundation.shape.CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = totalDosesLogged.toString(),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "dosis",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 🔥 Métricas de actividad 🔥
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActivityMetricItem(
                        label = "Día Más Activo",
                        value = mostActiveDay,
                        icon = "📈",
                        modifier = Modifier.weight(1f)
                    )
                    ActivityMetricItem(
                        label = "Día Menos Activo",
                        value = leastActiveDay,
                        icon = "📉",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ActivityMetricItem(
                        label = "Consistencia",
                        value = loggingConsistency,
                        icon = "📊",
                        modifier = Modifier.weight(1f)
                    )
                    ActivityMetricItem(
                        label = "Promedio Diario",
                        value = averageDailyDoses,
                        icon = "📅",
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 🔥 Barra de actividad del período 🔥
                if (totalPeriodDays > 0) {
                    Column {
                        Text(
                            text = "Actividad del Período",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                        ) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(totalDaysWithRecords.toFloat() / totalPeriodDays)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$totalDaysWithRecords de $totalPeriodDays días con registros",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ActivityMetricItem(
    label: String,
    value: String,
    icon: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = icon,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

private fun calculateTotalDays(dateRangeText: String): Int {
    // Estimación simple basada en el texto del rango
    return when {
        dateRangeText.contains("7") -> 7
        dateRangeText.contains("15") -> 15
        dateRangeText.contains("30") -> 30
        else -> 30 // Default para "Todos"
    }
}

@Composable
fun TopMedicationsList(
    medications: List<Triple<String, Int, String>>,
    onMedicationClick: ((String) -> Unit)? = null
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Más Tomados",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                medications.forEachIndexed { index, (name, count, id) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .let { modifier ->
                                if (onMedicationClick != null) {
                                    modifier.clickable { onMedicationClick(id) }
                                } else modifier
                            },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${index + 1}.",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = name,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$count tomas",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (onMedicationClick != null) {
                                Spacer(modifier = Modifier.width(8.dp))
                                androidx.compose.material3.Icon(
                                    imageVector = androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowForward,
                                    contentDescription = "Ver detalles",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    if (index < medications.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentHistoryList(history: List<com.d4vram.pillmate2.model.HistoryEntry>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Historial Reciente",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        androidx.compose.material3.ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                history.forEachIndexed { index, entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = entry.medicationName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "${entry.date} • ${entry.time}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(4.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "${entry.quantity}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (index < history.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DateNavigation(
    dateRangeText: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.medium
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Anterior"
            )
        }

        Text(
            text = dateRangeText,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )

        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = "Siguiente"
            )
        }
    }
}

@Composable
fun FilterButtons(
    activeFilter: FilterRange,
    onFilterSelected: (FilterRange) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceContainer,
                shape = MaterialTheme.shapes.extraLarge
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        val filters = listOf(
            "7 Días" to FilterRange.WEEK,
            "15 Días" to FilterRange.DAYS_15,
            "30 Días" to FilterRange.DAYS_30,
            "Todos" to FilterRange.ALL_TIME
        )

        filters.forEach { (text, range) ->
            val isSelected = activeFilter == range
            val buttonColors = if (isSelected) {
                ButtonDefaults.textButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                ButtonDefaults.textButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            TextButton(
                onClick = { onFilterSelected(range) },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.large,
                colors = buttonColors
            ) {
                Text(
                    text = text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
