package com.d4vram.pillmate2.ui.export

import androidx.annotation.StringRes
import com.d4vram.pillmate2.R
import androidx.compose.ui.res.stringResource
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
                    putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.pdf_share_subject))
                }
                context.startActivity(Intent.createChooser(intent, context.getString(R.string.stats_share_chooser)))
                viewModel.clearGeneratedFile()
            } catch (e: Exception) {
                viewModel.reportShareError(e.message ?: context.getString(R.string.pdf_share_failed))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.export_pdf)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back)
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
                text = stringResource(R.string.pdf_style_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )

            StyleCard(
                title = stringResource(R.string.pdf_style_clinical),
                description = stringResource(R.string.pdf_style_clinical_desc),
                selected = uiState.selectedStyle == PdfStyleType.CLINICAL,
                onClick = { viewModel.setStyle(PdfStyleType.CLINICAL) }
            )

            StyleCard(
                title = "PillMate",
                description = stringResource(R.string.pdf_style_pillmate_desc),
                selected = uiState.selectedStyle == PdfStyleType.PILLMATE,
                onClick = { viewModel.setStyle(PdfStyleType.PILLMATE) }
            )

            StyleCard(
                title = stringResource(R.string.pdf_style_minimal),
                description = stringResource(R.string.pdf_style_minimal_desc),
                selected = uiState.selectedStyle == PdfStyleType.MINIMAL,
                onClick = { viewModel.setStyle(PdfStyleType.MINIMAL) }
            )

            HorizontalDivider()

            // Selector de periodo
            Text(
                text = stringResource(R.string.pdf_period),
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
                text = stringResource(R.string.pdf_sections),
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
                                    text = stringResource(section.labelRes),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                Text(
                                    text = stringResource(getSectionDescription(section)),
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
                    Text(stringResource(R.string.pdf_generating))
                } else {
                    Text(
                        text = stringResource(R.string.pdf_generate),
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

    val dateFormatter = com.d4vram.pillmate2.utils.localizedDateFormatter("ddMMyyyy", androidx.compose.ui.platform.LocalConfiguration.current.locales[0])

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
                        text = stringResource(R.string.pdf_from),
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
                        text = stringResource(R.string.pdf_to),
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
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
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
                    Text(stringResource(R.string.action_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndPicker = false }) {
                    Text(stringResource(R.string.action_cancel))
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
@StringRes
private fun getSectionDescription(section: PdfSection): Int {
    return when (section) {
        PdfSection.SUMMARY -> R.string.pdf_section_summary_desc
        PdfSection.MEDICATIONS -> R.string.pdf_section_medications_desc
        PdfSection.WEEKLY_CHART -> R.string.pdf_section_weekly_desc
        PdfSection.HISTORY -> R.string.pdf_section_history_desc
        PdfSection.HOURLY_DISTRIBUTION -> R.string.pdf_section_hourly_desc
        PdfSection.DOSE_BREAKDOWN -> R.string.pdf_section_breakdown_desc
        PdfSection.MONTHLY_TRENDS -> R.string.pdf_section_monthly_desc
        PdfSection.VOICE_NOTES -> R.string.pdf_section_voice_desc
    }
}
