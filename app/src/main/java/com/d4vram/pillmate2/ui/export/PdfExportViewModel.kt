package com.d4vram.pillmate2.ui.export

import com.d4vram.pillmate2.R
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.MedicationRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * Estado de la UI para la pantalla de exportación PDF
 */
data class PdfExportUiState(
    // Configuración seleccionada
    val startDate: LocalDate = LocalDate.now().minusDays(6),
    val endDate: LocalDate = LocalDate.now(),
    val selectedSections: Set<PdfSection> = PdfSection.entries.filter { it.defaultSelected }.toSet(),
    val selectedStyle: PdfStyleType = PdfStyleType.PILLMATE,

    // Estado de generación
    val isGenerating: Boolean = false,
    val generatedFile: File? = null,
    val error: String? = null
)

/**
 * ViewModel para la pantalla de exportación PDF.
 * Maneja la configuración del usuario y la generación del PDF.
 */
class PdfExportViewModel(
    private val repository: MedicationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PdfExportUiState())
    val uiState: StateFlow<PdfExportUiState> = _uiState.asStateFlow()

    /**
     * Inicializa con el rango de fechas actual de estadísticas
     */
    fun initWithDateRange(startDate: LocalDate, endDate: LocalDate) {
        _uiState.update {
            it.copy(
                startDate = startDate,
                endDate = endDate
            )
        }
    }

    /**
     * Actualiza la fecha de inicio
     */
    fun setStartDate(date: LocalDate) {
        _uiState.update { it.copy(startDate = date) }
    }

    /**
     * Actualiza la fecha de fin
     */
    fun setEndDate(date: LocalDate) {
        _uiState.update { it.copy(endDate = date) }
    }

    /**
     * Activa/desactiva una sección
     */
    fun toggleSection(section: PdfSection) {
        _uiState.update { state ->
            val newSections = if (section in state.selectedSections) {
                state.selectedSections - section
            } else {
                state.selectedSections + section
            }
            state.copy(selectedSections = newSections)
        }
    }

    /**
     * Cambia el estilo seleccionado
     */
    fun setStyle(style: PdfStyleType) {
        _uiState.update { it.copy(selectedStyle = style) }
    }

    /**
     * Genera el PDF con la configuración actual
     */
    fun generatePdf(context: Context) {
        val state = _uiState.value
        if (state.endDate.isBefore(state.startDate)) {
            _uiState.update { it.copy(error = context.getString(R.string.pdf_err_dates)) }
            return
        }

        _uiState.update { it.copy(isGenerating = true, error = null, generatedFile = null) }

        viewModelScope.launch {
            try {
                // 1. Obtener datos del repositorio
                val reportData = gatherReportData(state.startDate, state.endDate)

                // 2. Crear configuración
                val config = PdfExportConfig(
                    startDate = state.startDate,
                    endDate = state.endDate,
                    sections = state.selectedSections,
                    style = state.selectedStyle
                )

                // 3. Generar PDF
                val file = withContext(Dispatchers.IO) {
                    val generator = PdfGenerator(context)
                    generator.generate(config, reportData)
                }

                if (file != null) {
                    _uiState.update {
                        it.copy(isGenerating = false, generatedFile = file)
                    }
                } else {
                    _uiState.update {
                        it.copy(isGenerating = false, error = context.getString(R.string.pdf_err_generate))
                    }
                }

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isGenerating = false, error = e.message ?: context.getString(R.string.pdf_err_unknown))
                }
            }
        }
    }

    /**
     * Limpia el archivo generado (para cerrar el diálogo)
     */
    fun clearGeneratedFile() {
        _uiState.update { it.copy(generatedFile = null) }
    }

    fun reportShareError(message: String) {
        _uiState.update { it.copy(error = message, generatedFile = null) }
    }

    /**
     * Limpia el error
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Recopila todos los datos necesarios para el reporte
     */
    private suspend fun gatherReportData(startDate: LocalDate, endDate: LocalDate): PdfReportData {
        val allMedications = repository.getMedicationsSnapshot()
        val history = repository.getHistoryBetween(startDate.toEpochDay(), endDate.toEpochDay())
            .sortedByDescending { it.timestamp }

        val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1
        val daysWithRecords = history.map { it.timestamp.toLocalDate() }.distinct().size

        // Calcular totales por medicamento
        val medicationTotals = allMedications.mapNotNull { med ->
            val medHistory = history.filter { it.medicationId == med.id }
            if (medHistory.isEmpty()) return@mapNotNull null

            val totalQuantity = medHistory.sumOf { it.quantity }
            val unit = getDisplayUnit(med)

            // Solo multiplicar por dosis si el tipo de medicamento lo permite
            // Para tipos no reconocidos, simplemente contar tomas
            val totalValue = if (shouldMultiplyByDose(med)) {
                val doseValue = extractDoseValue(med.dose)
                totalQuantity * doseValue
            } else {
                totalQuantity.toFloat()
            }

            MedicationTotal(
                medication = med,
                totalValue = totalValue,
                displayUnit = unit,
                percentage = 0f // Se calcula después
            )
        }

        // Calcular porcentajes
        val grandTotal = medicationTotals.sumOf { it.totalValue.toDouble() }.toFloat()
        val medicationTotalsWithPercentage = medicationTotals.map { mt ->
            mt.copy(percentage = if (grandTotal > 0) mt.totalValue / grandTotal else 0f)
        }.sortedByDescending { it.totalValue }

        // Unidad de display (si hay un solo tipo de medicamento, usar su unidad)
        val displayUnit = if (medicationTotals.isNotEmpty()) {
            medicationTotals.first().displayUnit
        } else "tomas"

        // Total general (en unidades - mantener para gráficas)
        val totalValue = grandTotal
        val averageDaily = if (totalDays > 0) totalValue / totalDays else 0f

        // Métricas de tomas (más útiles clínicamente)
        val totalTomas = history.sumOf { it.quantity }
        val averageDailyTomas = if (totalDays > 0) totalTomas.toFloat() / totalDays else 0f

        // Datos diarios para gráfica
        val dailyData = (0 until totalDays).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val dayHistory = history.filter { it.timestamp.toLocalDate() == date }
            val dayValue = dayHistory.sumOf { entry ->
                val med = allMedications.find { it.id == entry.medicationId }
                // Usar la misma lógica que medicationTotals
                if (med != null && shouldMultiplyByDose(med)) {
                    val doseValue = extractDoseValue(med.dose)
                    entry.quantity * doseValue.toDouble()
                } else {
                    entry.quantity.toDouble()
                }
            }.toFloat()

            DailyDataPoint(date = date, value = dayValue)
        }

        // Patrón horario
        val hourlyPattern = calculateHourlyPattern(history)

        return PdfReportData(
            medications = allMedications,
            history = history,
            startDate = startDate,
            endDate = endDate,
            totalValue = totalValue,
            displayUnit = displayUnit,
            averageDaily = averageDaily,
            daysWithRecords = daysWithRecords,
            totalDays = totalDays,
            totalTomas = totalTomas,
            averageDailyTomas = averageDailyTomas,
            medicationTotals = medicationTotalsWithPercentage,
            dailyData = dailyData,
            hourlyPattern = hourlyPattern
        )
    }

    /**
     * Extrae el valor numérico de una dosis.
     * Maneja tanto punto como coma decimal (ej: "0,1mg" o "0.1mg" → 0.1)
     */
    private fun extractDoseValue(dose: String): Float {
        // Normalizar: reemplazar coma por punto para decimales españoles
        val normalizedDose = dose.replace(",", ".")
        val regex = Regex("([0-9]+\\.?[0-9]*)")
        val match = regex.find(normalizedDose)
        return match?.groupValues?.get(1)?.toFloatOrNull() ?: 1f
    }

    /**
     * Determina la unidad de display según el tipo de medicamento.
     * Si el tipo no se reconoce, devuelve null para indicar que se deben usar tomas simples.
     */
    private fun getDisplayUnit(medication: com.d4vram.pillmate2.model.Medication): String {
        return when {
            medication.type.equals("Gota", ignoreCase = true) ||
            medication.type.equals("Gotas", ignoreCase = true) -> "gotas"
            medication.type.equals("Jarabe", ignoreCase = true) -> "ml"
            medication.type.equals("Pastilla", ignoreCase = true) ||
            medication.type.equals("Cápsula", ignoreCase = true) ||
            medication.type.equals("Comprimido", ignoreCase = true) -> "mg"
            else -> "tomas"
        }
    }

    /**
     * Determina si este tipo de medicamento debe multiplicarse por la dosis numérica.
     * Solo mg, ml y gotas tienen sentido multiplicar; "tomas" no.
     */
    private fun shouldMultiplyByDose(medication: com.d4vram.pillmate2.model.Medication): Boolean {
        val type = medication.type.lowercase()
        return type in listOf("gota", "gotas", "jarabe", "pastilla", "cápsula", "comprimido", "capsula")
    }

    private fun calculateHourlyPattern(history: List<com.d4vram.pillmate2.model.HistoryEntry>): Map<String, Int> {
        val slots = listOf("Madrugada", "Mañana", "Mediodía", "Tarde", "Noche", "Nocturno")
        val hourlyMap = slots.associateWith { 0 }.toMutableMap()

        history.forEach { entry ->
            val hour = entry.timestamp.hour
            val slot = when (hour) {
                in 0..5 -> "Madrugada"
                in 6..11 -> "Mañana"
                in 12..14 -> "Mediodía"
                in 15..18 -> "Tarde"
                in 19..22 -> "Noche"
                else -> "Nocturno"
            }
            hourlyMap[slot] = (hourlyMap[slot] ?: 0) + entry.quantity
        }

        return hourlyMap
    }

    companion object {
        fun provideFactory(
            repository: MedicationRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PdfExportViewModel(repository) as T
            }
        }
    }
}
