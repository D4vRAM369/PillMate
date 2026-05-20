package com.d4vram.pillmate2.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.d4vram.pillmate2.ui.export.PdfExportSheet
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.ui.stats.viewmodel.DoseBreakdownItem
import com.d4vram.pillmate2.ui.stats.viewmodel.FilterRange
import com.d4vram.pillmate2.ui.stats.viewmodel.HistoryEntryWithUnit
import com.d4vram.pillmate2.ui.stats.viewmodel.StatisticsViewModel
import com.d4vram.pillmate2.utils.StatsExporter
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatisticsScreen(
    onNavigateBack: () -> Unit,
    repository: MedicationRepository,
    navController: androidx.navigation.NavController? = null
) {
    val context = LocalContext.current
    val viewModel: StatisticsViewModel = viewModel(
        factory = StatisticsViewModel.provideFactory(repository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // Estado para el menú de exportación y el sheet de PDF
    var showExportMenu by remember { mutableStateOf(false) }
    var showPdfExportSheet by remember { mutableStateOf(false) }

    // Calcular fechas del periodo actual para pasar al PDF
    val currentStartDate = remember(uiState.activeFilter, uiState.dateRangeText) {
        when (uiState.activeFilter) {
            FilterRange.WEEK -> java.time.LocalDate.now().minusDays(6)
            FilterRange.DAYS_15 -> java.time.LocalDate.now().minusDays(14)
            FilterRange.DAYS_30 -> java.time.LocalDate.now().minusDays(29)
            FilterRange.ALL_TIME -> java.time.LocalDate.now().minusYears(1)
        }
    }
    val currentEndDate = java.time.LocalDate.now()

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
                },
                actions = {
                    Box {
                        IconButton(onClick = { showExportMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = "Exportar datos"
                            )
                        }

                        // Menú desplegable con opciones CSV y PDF
                        DropdownMenu(
                            expanded = showExportMenu,
                            onDismissRequest = { showExportMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Exportar CSV") },
                                onClick = {
                                    showExportMenu = false
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
                                                    }
                                                    context.startActivity(
                                                        android.content.Intent.createChooser(intent, "Compartir reporte")
                                                    )
                                                } catch (e: Exception) {
                                                    snackbarHostState.showSnackbar("Error al compartir: ${e.message}")
                                                }
                                            } else {
                                                snackbarHostState.showSnackbar("Error al exportar datos")
                                            }
                                        } else {
                                            snackbarHostState.showSnackbar("Almacenamiento no disponible")
                                        }
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Exportar PDF") },
                                onClick = {
                                    showExportMenu = false
                                    showPdfExportSheet = true
                                }
                            )
                        }
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Filtros de tiempo
            FilterButtons(
                activeFilter = uiState.activeFilter,
                onFilterSelected = { range -> viewModel.setFilter(range) }
            )

            // Selector de medicamento
            if (uiState.availableMedications.isNotEmpty()) {
                MedicationFilterDropdown(
                    medications = uiState.availableMedications,
                    selectedId = uiState.selectedMedicationId,
                    onMedicationSelected = { id -> viewModel.selectMedication(id) }
                )
            }

            // KPIs principales
            MainKpiSection(
                totalValue = uiState.totalInUnit,
                displayUnit = uiState.displayUnit,
                averageValue = uiState.averageInUnit,
                lastRecord = uiState.timeSinceLastRecord
            )

            if (navController != null) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth(),
                    colors = CardDefaults.elevatedCardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(Color(0xFF071325), Color(0xFF0F3B66), Color(0xFF0EA5E9))
                                )
                            )
                            .padding(20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = "Abrir PillMate AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Lleva este periodo al chat privado con modelos locales, sesiones guardadas e importación/exportación.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFD6F2FF)
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Abrir IA",
                            tint = Color.White
                        )
                    }
                }
            }

            // Gráfica lineal semanal (nueva, con protagonismo)
            if (uiState.activityData.isNotEmpty()) {
                WeeklyLineChart(
                    data = uiState.activityData,
                    unit = uiState.displayUnit,
                    accentColor = uiState.selectedMedicationColor ?: MaterialTheme.colorScheme.primary,
                    dateRangeText = uiState.dateRangeText,
                    onPrevious = { viewModel.previousPeriod() },
                    onNext = { viewModel.nextPeriod() },
                    showNavigation = uiState.activeFilter != FilterRange.ALL_TIME
                )
            }

            // Desglose por variantes de dosis (solo si hay familia seleccionada)
            if (uiState.showingFamilyStats && uiState.doseBreakdown.isNotEmpty()) {
                DoseBreakdownSection(
                    familyName = uiState.familyName,
                    breakdown = uiState.doseBreakdown
                )
            }

            // Gráfica de actividad horizontal (barras por día)
            if (uiState.activityData.isNotEmpty()) {
                HorizontalActivityChart(
                    data = uiState.activityData,
                    accentColor = uiState.selectedMedicationColor ?: MaterialTheme.colorScheme.primary,
                    onDaySelected = { date -> viewModel.selectDay(date) },
                    selectedDate = uiState.selectedDate
                )
            }

            // Distribución horaria (6 franjas)
            if (uiState.hourlyPattern.isNotEmpty() && uiState.hourlyPattern.values.any { it > 0 }) {
                HourlyDistributionSection(
                    hourlyPattern = uiState.hourlyPattern,
                    accentColor = uiState.selectedMedicationColor ?: MaterialTheme.colorScheme.primary
                )
            }

            // Historial reciente
            if (uiState.recentHistoryWithUnits.isNotEmpty()) {
                RecentHistorySection(
                    history = uiState.recentHistoryWithUnits
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // BottomSheet para detalles diarios
        if (uiState.selectedDate != null) {
            androidx.compose.material3.ModalBottomSheet(
                onDismissRequest = { viewModel.selectDay(null) },
                containerColor = androidx.compose.ui.graphics.Color(0xFF1E2124), // Tono oscuro similar a capturas
                contentColor = androidx.compose.ui.graphics.Color.White
            ) {
                DailyDetailSheetContent(
                    date = uiState.selectedDate!!,
                    history = uiState.selectedDateHistory,
                    onViewFullHistory = {
                        // Navegar al historial completo o cerrar sheet
                        viewModel.selectDay(null)
                        // Si hubiera navegación directa: navController?.navigate("history")
                    }
                )
            }
        }

        // BottomSheet para exportar PDF
        if (showPdfExportSheet) {
            PdfExportSheet(
                repository = repository,
                initialStartDate = currentStartDate,
                initialEndDate = currentEndDate,
                onDismiss = { showPdfExportSheet = false }
            )
        }
    }
}

@Composable
fun DailyDetailSheetContent(
    date: java.time.LocalDate,
    history: List<HistoryEntryWithUnit>,
    onViewFullHistory: () -> Unit
) {
    val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM")
    val spanishLocale = java.util.Locale.forLanguageTag("es")
    
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Cabecera
        Column {
            Text(
                text = date.format(dateFormatter.withLocale(spanishLocale)).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.White // Título claro
            )
            Text(
                text = "${history.sumOf { it.entry.quantity }} dosis",
                style = MaterialTheme.typography.bodyMedium,
                color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f)
            )
        }

        HorizontalDivider(color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.1f))

        // Lista de medicamentos
        history.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Dot indicador con el color del medicamento
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(item.medicationColor ?: MaterialTheme.colorScheme.primary)
                )

                // Nombre del medicamento (ocupa espacio disponible, sin romper línea)
                Text(
                    text = "${item.entry.medicationName} • ${item.displayValue} ${item.displayUnit}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = androidx.compose.ui.graphics.Color.White,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Hora (ancho fijo, nunca se rompe)
                Text(
                    text = item.entry.time,
                    style = MaterialTheme.typography.bodyMedium,
                    color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.7f),
                    maxLines = 1
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Botón de ver historial completo
        androidx.compose.material3.Button(
            onClick = onViewFullHistory,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = androidx.compose.ui.graphics.Color(0xFF4ADE80), // Verde vibrante tipo capturas
                contentColor = androidx.compose.ui.graphics.Color.Black
            ),
            shape = RoundedCornerShape(25.dp) // Redondeado completo
        ) {
            Text(
                text = "Ver historial completo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


@Composable
fun DoseBreakdownSection(
    familyName: String,
    breakdown: List<DoseBreakdownItem>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Desglose por dosis",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Mostrar nombre de la familia si hay distintos nombres
                if (familyName.contains("/")) {
                    Text(
                        text = "Familia: $familyName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                breakdown.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Color del medicamento
                        Box(
                            modifier = Modifier
                                .size(12.dp)
                                .clip(CircleShape)
                                .background(item.color)
                        )

                        // Nombre y dosis
                        Column(modifier = Modifier.width(100.dp)) {
                            Text(
                                text = item.medicationName,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                text = item.dose,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Barra de progreso
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (item.percentage > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(item.percentage)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(item.color.copy(alpha = 0.8f))
                                )
                            }
                        }

                        // Valor total
                        Text(
                            text = "${formatBreakdownValue(item.totalValue)}${item.displayUnit}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.width(60.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun formatBreakdownValue(value: Float): String {
    return if (value == value.toInt().toFloat()) {
        value.toInt().toString()
    } else {
        String.format("%.1f", value)
    }
}




@Composable
fun MainKpiSection(
    totalValue: String,
    displayUnit: String,
    averageValue: String,
    lastRecord: String
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = totalValue,
                        style = MaterialTheme.typography.displayMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = displayUnit,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (lastRecord.isNotEmpty()) {
                    Text(
                        text = "Último: $lastRecord",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "$averageValue $displayUnit/día en promedio",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun HorizontalActivityChart(
    data: List<ActivityDataPoint>,
    accentColor: Color,
    onDaySelected: (java.time.LocalDate?) -> Unit,
    selectedDate: java.time.LocalDate?
) {
    val maxValue = data.maxOfOrNull { it.totalValue } ?: 1f
    val today = data.lastOrNull()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            data.forEach { point ->
                val isToday = point == today
                val barColor = if (isToday) {
                    MaterialTheme.colorScheme.tertiary
                } else if (point.date != null && point.date == selectedDate) {
                    accentColor // Color fuerte para selección
                } else {
                    accentColor.copy(alpha = 0.6f) // Un poco apagado si no es hoy ni seleccionado
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = point.hasRecords) { 
                             onDaySelected(if (point.date == selectedDate) null else point.date) 
                        },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Día
                    Text(
                        text = point.dayName,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                        color = if (isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(40.dp)
                    )

                    // Barra horizontal
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(20.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        val fraction = if (maxValue > 0) (point.totalValue / maxValue).coerceIn(0f, 1f) else 0f
                        if (fraction > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .fillMaxWidth(fraction)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barColor)
                            )
                        }
                    }

                    // Valor
                    Text(
                        text = if (point.totalValue > 0) point.totalValue.toInt().toString() else "-",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(24.dp)
                    )

                    // Indicador de hoy
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(MaterialTheme.colorScheme.tertiary, CircleShape)
                        )
                    } else {
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun HourlyDistributionSection(
    hourlyPattern: Map<String, Int>,
    accentColor: Color
) {
    val slots = listOf("Madrugada", "Mañana", "Mediodía", "Tarde", "Noche", "Nocturno")
    val slotRanges = listOf("00-06", "06-12", "12-15", "15-19", "19-23", "23-00")
    val maxValue = hourlyPattern.values.maxOrNull()?.toFloat() ?: 1f

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Distribución por horario",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                slots.forEachIndexed { index, slot ->
                    val value = hourlyPattern[slot] ?: 0
                    val fraction = if (maxValue > 0) (value.toFloat() / maxValue).coerceIn(0f, 1f) else 0f

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Nombre del slot (ancho suficiente para "Madrugada")
                        Column(modifier = Modifier.width(85.dp)) {
                            Text(
                                text = slot,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )
                            Text(
                                text = slotRanges[index],
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        // Barra horizontal
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            if (fraction > 0) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .fillMaxWidth(fraction)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(accentColor.copy(alpha = 0.8f))
                                )
                            }
                        }

                        // Valor
                        Text(
                            text = value.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.width(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RecentHistorySection(
    history: List<HistoryEntryWithUnit>
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Historial reciente",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        ElevatedCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.elevatedCardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                history.forEachIndexed { index, item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.entry.medicationName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${item.entry.date} · ${item.entry.time}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "${item.displayValue} ${item.displayUnit}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if (index < history.lastIndex) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                            modifier = Modifier.padding(vertical = 2.dp)
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
