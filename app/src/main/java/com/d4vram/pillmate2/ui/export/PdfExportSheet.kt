package com.d4vram.pillmate2.ui.export

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.d4vram.pillmate2.data.MedicationRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Bottom Sheet para configurar y generar el reporte PDF.
 *
 * @param repository Repositorio para obtener datos de medicamentos
 * @param initialStartDate Fecha inicio (del filtro actual de estadísticas)
 * @param initialEndDate Fecha fin (del filtro actual de estadísticas)
 * @param onDismiss Callback para cerrar el sheet
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfExportSheet(
    repository: MedicationRepository,
    initialStartDate: LocalDate,
    initialEndDate: LocalDate,
    onDismiss: () -> Unit
) {
    val viewModel: PdfExportViewModel = viewModel(
        factory = PdfExportViewModel.provideFactory(repository)
    )

    // Inicializar con las fechas del filtro actual
    LaunchedEffect(initialStartDate, initialEndDate) {
        viewModel.initWithDateRange(initialStartDate, initialEndDate)
    }

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

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Título
            Text(
                text = "Generar Reporte PDF",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            // Selector de periodo
            PeriodSelector(
                startDate = uiState.startDate,
                endDate = uiState.endDate,
                onStartDateChange = { viewModel.setStartDate(it) },
                onEndDateChange = { viewModel.setEndDate(it) }
            )

            HorizontalDivider()

            // Secciones a incluir
            Text(
                text = "Secciones a incluir:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            PdfSection.entries.forEach { section ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.toggleSection(section) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = section in uiState.selectedSections,
                        onCheckedChange = { viewModel.toggleSection(section) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = section.displayName,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }

            HorizontalDivider()

            // Selector de estilo
            Text(
                text = "Estilo:",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            Column(Modifier.selectableGroup()) {
                StyleOption(
                    name = "Clínico",
                    description = "Formal, aspecto médico profesional",
                    selected = uiState.selectedStyle == PdfStyleType.CLINICAL,
                    onClick = { viewModel.setStyle(PdfStyleType.CLINICAL) }
                )
                StyleOption(
                    name = "PillMate",
                    description = "Colores y estilo de la app",
                    selected = uiState.selectedStyle == PdfStyleType.PILLMATE,
                    onClick = { viewModel.setStyle(PdfStyleType.PILLMATE) }
                )
                StyleOption(
                    name = "Minimalista",
                    description = "Limpio, solo datos esenciales",
                    selected = uiState.selectedStyle == PdfStyleType.MINIMAL,
                    onClick = { viewModel.setStyle(PdfStyleType.MINIMAL) }
                )
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
        }
    }
}

/**
 * Selector de periodo con fechas editables
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PeriodSelector(
    startDate: LocalDate,
    endDate: LocalDate,
    onStartDateChange: (LocalDate) -> Unit,
    onEndDateChange: (LocalDate) -> Unit
) {
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }

    val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Periodo:",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )

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
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Desde",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = startDate.format(dateFormatter),
                            style = MaterialTheme.typography.bodyMedium
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
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.DateRange,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "Hasta",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = endDate.format(dateFormatter),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
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
 * Opción de estilo con radio button
 */
@Composable
private fun StyleOption(
    name: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null // Handled by row
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
