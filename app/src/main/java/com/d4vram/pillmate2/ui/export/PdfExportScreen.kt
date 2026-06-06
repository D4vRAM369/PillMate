package com.d4vram.pillmate2.ui.export

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.data.MedicationRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Pantalla completa para exportar PDF desde Ajustes.
 * Muestra los 3 estilos en cards y permite configurar secciones y periodo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportScreen(
    repository: MedicationRepository,
    onNavigateBack: () -> Unit
) {
    val viewModel: PdfExportViewModel = viewModel(
        factory = PdfExportViewModel.provideFactory(repository)
    )

    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Efecto para compartir el archivo cuando se genera
    LaunchedEffect(uiState.generatedFile) {
        uiState.generatedFile?.let { file ->
            try {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    file
                )
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    putExtra(Intent.EXTRA_SUBJECT, "Reporte PillMate")
                }
                context.startActivity(Intent.createChooser(intent, "Compartir reporte"))
                viewModel.clearGeneratedFile()
            } catch (e: Exception) {
                viewModel.reportShareError(e.message ?: "No se pudo compartir el PDF")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exportar PDF") },
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
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Selector de estilo en cards
            Text(
                text = "Estilo del reporte",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            StyleCard(
                title = "Clínico",
                description = "Formal y profesional. Ideal para compartir con médicos o incluir en historial médico. Colores neutros y estructura clara.",
                selected = uiState.selectedStyle == PdfStyleType.CLINICAL,
                onClick = { viewModel.setStyle(PdfStyleType.CLINICAL) }
            )

            StyleCard(
                title = "PillMate",
                description = "Colores y estilo de la app. Incluye indicadores de color por medicamento y diseño visual atractivo.",
                selected = uiState.selectedStyle == PdfStyleType.PILLMATE,
                onClick = { viewModel.setStyle(PdfStyleType.PILLMATE) }
            )

            StyleCard(
                title = "Minimalista",
                description = "Limpio y compacto. Solo los datos esenciales sin elementos decorativos. Perfecto para impresión.",
                selected = uiState.selectedStyle == PdfStyleType.MINIMAL,
                onClick = { viewModel.setStyle(PdfStyleType.MINIMAL) }
            )

            HorizontalDivider()

            // Selector de periodo
            Text(
                text = "Periodo",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            PeriodSelectorFull(
                startDate = uiState.startDate,
                endDate = uiState.endDate,
                onStartDateChange = { viewModel.setStartDate(it) },
                onEndDateChange = { viewModel.setEndDate(it) }
            )

            HorizontalDivider()

            // Secciones a incluir
            Text(
                text = "Secciones a incluir",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            ElevatedCard(
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(8.dp)
                ) {
                    PdfSection.entries.forEach { section ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleSection(section) }
                                .padding(vertical = 8.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = section in uiState.selectedSections,
                                onCheckedChange = { viewModel.toggleSection(section) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = section.displayName,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = getSectionDescription(section),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Botón de generar
            Button(
                onClick = { viewModel.generatePdf(context) },
                enabled = !uiState.isGenerating && uiState.selectedSections.isNotEmpty(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                if (uiState.isGenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Generando...")
                } else {
                    Text(
                        text = "Generar PDF",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Mensaje de error
            uiState.error?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

/**
 * Card seleccionable para cada estilo de PDF
 */
@Composable
private fun StyleCard(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outline
        ),
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (selected)
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadioButton(
                selected = selected,
                onClick = onClick
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Selector de periodo con fechas editables (versión para pantalla completa)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelectorFull(
    startDate: LocalDate,
    endDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Fecha inicio
        OutlinedCard(
            modifier = Modifier
                .weight(1f)
                .clickable { showStartPicker = true }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Desde",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = startDate.format(dateFormatter),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Fecha fin
        OutlinedCard(
            modifier = Modifier
                .weight(1f)
                .clickable { showEndPicker = true }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Hasta",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = endDate.format(dateFormatter),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    // Date Pickers
    if (showStartPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.toEpochDay() * 24 * 60 * 60 * 1000
        )

        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                            onStartDateChange(selectedDate)
                        }
                        showStartPicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (showEndPicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = endDate.toEpochDay() * 24 * 60 * 60 * 1000
        )

        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { millis ->
                            val selectedDate = LocalDate.ofEpochDay(millis / (24 * 60 * 60 * 1000))
                            onEndDateChange(selectedDate)
                        }
                        showEndPicker = false
                    }
                ) {
                    Text("Aceptar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text("Cancelar")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

/**
 * Devuelve una descripción corta para cada sección
 */
private fun getSectionDescription(section: PdfSection): String {
    return when (section) {
        PdfSection.SUMMARY -> "Resumen general con total de tomas y estadísticas"
        PdfSection.MEDICATIONS -> "Lista de medicamentos con cantidades totales"
        PdfSection.WEEKLY_CHART -> "Gráfica visual de consumo semanal"
        PdfSection.HISTORY -> "Historial detallado de todas las tomas"
        PdfSection.HOURLY_DISTRIBUTION -> "Gráfica de distribución por franjas horarias"
        PdfSection.DOSE_BREAKDOWN -> "Desglose detallado por dosis de cada medicamento"
        PdfSection.MONTHLY_TRENDS -> "Tendencias y patrones de consumo mensual"
        PdfSection.VOICE_NOTES -> "Transcripciones de notas de voz asociadas"
    }
}
