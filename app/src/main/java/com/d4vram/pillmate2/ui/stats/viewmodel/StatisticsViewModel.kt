// kotlin
package com.d4vram.pillmate2.ui.stats.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.d4vram.pillmate2.data.MedicationRepository
import com.d4vram.pillmate2.model.Medication
import com.d4vram.pillmate2.ui.stats.AdherenceDataPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt
import kotlin.math.abs

// Enums para los filtros de tiempo disponibles
enum class FilterRange {
    WEEK,
    DAYS_15,
    DAYS_30,
    ALL_TIME
}

data class StatisticsUiState(
    val dailyAverage: String = "0", // Promedio diario (nuevo métrica)
    val dosesTaken: Int = 0, // Dosis realmente tomadas (suma de cantidades)
    val lastTaken: String = "Nunca",
    val selectedMedicationName: String = "Todos",
    val adherenceData: List<AdherenceDataPoint> = emptyList(),
    val activeFilter: FilterRange = FilterRange.WEEK,
    val dateRangeText: String = "",
    val recentHistory: List<com.d4vram.pillmate2.model.HistoryEntry> = emptyList(),
    val topMedications: List<Pair<String, Int>> = emptyList(),
    val availableMedications: List<com.d4vram.pillmate2.model.Medication> = emptyList(),
    val selectedMedicationId: String? = null,
    val selectedMedicationColor: androidx.compose.ui.graphics.Color? = null,
    // 🔥 MÉTRICAS DE SEGUIMIENTO PURO (sin metas ni juicios)
    val totalDosesLogged: Int = 0, // Total de dosis registradas en el período
    val averageDailyDoses: String = "0", // Promedio diario de dosis tomadas
    val mostActiveDay: String = "", // Día de la semana más activo
    val leastActiveDay: String = "", // Día de la semana menos activo
    val loggingTrend: String = "0%", // Tendencia en el registro vs período anterior
    val weeklyActivityPattern: Map<String, Int> = emptyMap(), // Patrón semanal de actividad (dosis por día)
    val monthlyActivityData: List<StatisticsViewModel.MonthlyActivityPoint> = emptyList(), // Datos mensuales de actividad
    val loggingConsistency: String = "", // Nivel de consistencia en el registro
    val totalDaysWithRecords: Int = 0, // Días con al menos un registro
    val averageDosesPerActiveDay: String = "0" // Promedio de dosis en días activos
)

class StatisticsViewModel(
    private val medicationRepository: MedicationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(StatisticsUiState())
    val uiState: StateFlow<StatisticsUiState> = _uiState.asStateFlow()

    private val _filter = MutableStateFlow(FilterRange.WEEK)
    private var selectedMedicationId: String? = null

    // Fecha fin del rango (normalmente hoy, pero permite navegar al pasado)
    private var currentEndDate: LocalDate = LocalDate.now()

    init {
        // Recargar estadísticas cuando cambia el filtro
        _filter.onEach { range ->
            // Al cambiar filtro, reseteamos a hoy para no perdernos en el pasado
            currentEndDate = LocalDate.now()
            loadStatistics()
        }.launchIn(viewModelScope)
    }

    // --- Métodos Públicos ---

    fun setFilter(range: FilterRange) {
        _filter.value = range
    }

    fun selectMedication(medicationId: String?) {
        selectedMedicationId = medicationId
        loadStatistics()
    }

    fun previousPeriod() {
        val range = _filter.value
        currentEndDate = when (range) {
            FilterRange.WEEK -> currentEndDate.minusWeeks(1)
            FilterRange.DAYS_15 -> currentEndDate.minusDays(15)
            FilterRange.DAYS_30 -> currentEndDate.minusDays(30)
            FilterRange.ALL_TIME -> currentEndDate 
        }
        loadStatistics()
    }

    fun nextPeriod() {
        val range = _filter.value
        val today = LocalDate.now()
        val nextDate = when (range) {
            FilterRange.WEEK -> currentEndDate.plusWeeks(1)
            FilterRange.DAYS_15 -> currentEndDate.plusDays(15)
            FilterRange.DAYS_30 -> currentEndDate.plusDays(30)
            FilterRange.ALL_TIME -> currentEndDate
        }

        // No permitir navegar al futuro
        if (nextDate.isAfter(today)) {
            currentEndDate = today
        } else {
            currentEndDate = nextDate
        }
        loadStatistics()
    }

    // --- Lógica Principal de Cálculo ---

    private fun loadStatistics() {
        val range = _filter.value
        viewModelScope.launch {
            val endDate = currentEndDate
            
            // 1. Determinar fecha de inicio según rango para la query
            val queryStartDate = when (range) {
                FilterRange.WEEK -> endDate.minusDays(6) // 7 días total
                FilterRange.DAYS_15 -> endDate.minusDays(14)
                FilterRange.DAYS_30 -> endDate.minusDays(29)
                FilterRange.ALL_TIME -> LocalDate.of(2020, 1, 1) // Base temporal safe
            }

            // 2. Cargar datos del repositorio
            val allHistory = if (range == FilterRange.ALL_TIME) {
                medicationRepository.getHistorySnapshot()
            } else {
                medicationRepository.getHistoryBetween(queryStartDate.toEpochDay(), endDate.toEpochDay())
            }
            val allMedications = medicationRepository.getMedicationsSnapshot()

            // 3. Ajustar fecha de inicio REAL para visualización y cálculos
            val effectiveStartDate = if (range == FilterRange.ALL_TIME) {
                val firstHistory = allHistory.minByOrNull { it.timestamp }?.timestamp?.toLocalDate()
                val firstMedCreation = allMedications.filter { it.createdAt > 0 }
                    .minByOrNull { it.createdAt }?.createdAt?.let {
                        Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                    }
                listOfNotNull(firstHistory, firstMedCreation).minOrNull() ?: LocalDate.now().minusDays(30)
            } else {
                queryStartDate
            }

            // Filtrar historial si hay un medicamento seleccionado
            val filteredHistory = if (selectedMedicationId != null) {
                allHistory.filter { it.medicationId == selectedMedicationId }
            } else {
                allHistory
            }

            // --- Cálculos de Dosis Totales y Tomadas ---

            // Usar quantity (cantidad de pastillas)
            val dosesTaken = filteredHistory.sumOf { it.quantity }

            // Calcular días totales en el rango para el promedio
            val totalDays = ChronoUnit.DAYS.between(effectiveStartDate, endDate) + 1
            // Promedio Diario = Dosis Totales / Días en Rango
            val dailyAvg = if (totalDays > 0) {
                (dosesTaken.toFloat() / totalDays).let { (it * 10).roundToInt() / 10f }
            } else 0f

            // 🔥 CÁLCULOS DE SEGUIMIENTO PURO 🔥
            val trackingMetrics = calculateTrackingMetrics(
                startDate = effectiveStartDate,
                endDate = endDate,
                history = filteredHistory,
                medications = allMedications,
                selectedMedicationId = selectedMedicationId
            )

            // --- Última Toma ---
            val lastTaken = if (filteredHistory.isNotEmpty()) {
                val mostRecent = filteredHistory.maxByOrNull { it.timestamp }
                mostRecent?.let { entry ->
                    val today = LocalDate.now()
                    val entryDate = entry.timestamp.toLocalDate()
                    val time = entry.timestamp.format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))

                    when {
                        entryDate == today -> "Hoy $time"
                        entryDate == today.minusDays(1) -> "Ayer $time"
                        else -> "${entryDate.dayOfMonth} ${entryDate.month.name.lowercase().take(3)} $time"
                    }
                } ?: "Nunca"
            } else {
                "Nunca"
            }

            // --- Preparar UI ---
            val spanishLocale = java.util.Locale.forLanguageTag("es")
            val dateFormatter = java.time.format.DateTimeFormatter.ofPattern("d MMM", spanishLocale)
            val dateRangeText = if (range == FilterRange.ALL_TIME) {
                "Desde siempre"
            } else {
                "${effectiveStartDate.format(dateFormatter)} - ${endDate.format(dateFormatter)}"
            }

            // --- Generar Datos de la Gráfica (Consumo) ---
            val adherenceChartData = generateAdherenceData(
                start = effectiveStartDate,
                end = endDate,
                history = filteredHistory,
                medications = allMedications,
                selectedMedicationId = selectedMedicationId
            )

            // --- Top Medicamentos ---
            val topMedicationsList = filteredHistory.groupBy { it.medicationId }
                .mapValues { entry -> entry.value.sumOf { it.quantity } }
                .toList()
                .sortedByDescending { it.second }
                .take(5)
                .map { (id, count) ->
                    val name = allMedications.find { it.id == id }?.name ?: "Desconocido"
                    name to count
                }

            val recentHistoryList = filteredHistory.take(5)

            val selectedMed = selectedMedicationId?.let { id -> allMedications.find { it.id == id } }

            _uiState.update {
                it.copy(
                    dailyAverage = String.format("%.1f", dailyAvg),
                    dosesTaken = dosesTaken,
                    lastTaken = lastTaken,
                    selectedMedicationName = selectedMed?.name ?: "Todos",
                    adherenceData = adherenceChartData,
                    activeFilter = range,
                    dateRangeText = dateRangeText,
                    recentHistory = recentHistoryList,
                    topMedications = topMedicationsList,
                    availableMedications = allMedications,
                    selectedMedicationId = selectedMedicationId,
                    selectedMedicationColor = selectedMed?.color,
                    // 🔥 MÉTRICAS DE SEGUIMIENTO PURO
                    totalDosesLogged = trackingMetrics.totalDosesLogged,
                    averageDailyDoses = String.format("%.1f", trackingMetrics.averageDailyDoses),
                    mostActiveDay = trackingMetrics.mostActiveDay,
                    leastActiveDay = trackingMetrics.leastActiveDay,
                    loggingTrend = if (trackingMetrics.loggingTrend > 0) "+${trackingMetrics.loggingTrend}%" else "${trackingMetrics.loggingTrend}%",
                    weeklyActivityPattern = trackingMetrics.weeklyActivityPattern,
                    monthlyActivityData = trackingMetrics.monthlyActivityData,
                    loggingConsistency = trackingMetrics.loggingConsistency,
                    totalDaysWithRecords = trackingMetrics.totalDaysWithRecords,
                    averageDosesPerActiveDay = String.format("%.1f", trackingMetrics.averageDosesPerActiveDay)
                )
            }
        }
    }

    private fun calculateTrackingMetrics(
        startDate: LocalDate,
        endDate: LocalDate,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        medications: List<com.d4vram.pillmate2.model.Medication>,
        selectedMedicationId: String?
    ): TrackingMetrics {
        val totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1

        // Filtrar historial por medicamento si está seleccionado
        val relevantHistory = if (selectedMedicationId != null) {
            history.filter { it.medicationId == selectedMedicationId }
        } else {
            history
        }

        // Cálculos básicos de seguimiento
        val totalDosesLogged = relevantHistory.sumOf { it.quantity }
        val totalDaysWithRecords = relevantHistory.map { it.timestamp.toLocalDate() }.distinct().size
        val averageDailyDoses = if (totalDays > 0) totalDosesLogged.toFloat() / totalDays else 0f
        val averageDosesPerActiveDay = if (totalDaysWithRecords > 0) totalDosesLogged.toFloat() / totalDaysWithRecords else 0f

        // Patrón semanal de actividad (dosis por día de la semana)
        val weeklyActivityPattern = calculateWeeklyActivityPattern(relevantHistory)

        // Día más y menos activo
        val mostActiveDay = weeklyActivityPattern.maxByOrNull { it.value }?.key ?: ""
        val leastActiveDay = weeklyActivityPattern.minByOrNull { it.value }?.key ?: ""

        // Tendencia de registro vs período anterior
        val previousPeriodStart = startDate.minusDays(totalDays)
        val previousPeriodEnd = startDate.minusDays(1)
        val previousPeriodDoses = if (selectedMedicationId != null) {
            history.filter { it.medicationId == selectedMedicationId &&
                           it.timestamp.toLocalDate() in previousPeriodStart..previousPeriodEnd }
                   .sumOf { it.quantity }
        } else {
            history.filter { it.timestamp.toLocalDate() in previousPeriodStart..previousPeriodEnd }
                   .sumOf { it.quantity }
        }

        val loggingTrend = if (previousPeriodDoses > 0) {
            ((totalDosesLogged - previousPeriodDoses).toFloat() / previousPeriodDoses * 100).roundToInt()
        } else 0

        // Datos mensuales de actividad
        val monthlyActivityData = calculateMonthlyActivityData(history, selectedMedicationId)

        // Nivel de consistencia (basado en variabilidad de registros diarios)
        val loggingConsistency = when {
            totalDaysWithRecords.toFloat() / totalDays >= 0.8f -> "Muy consistente"
            totalDaysWithRecords.toFloat() / totalDays >= 0.6f -> "Consistente"
            totalDaysWithRecords.toFloat() / totalDays >= 0.4f -> "Moderado"
            else -> "Poco frecuente"
        }

        return TrackingMetrics(
            totalDosesLogged = totalDosesLogged,
            averageDailyDoses = averageDailyDoses,
            mostActiveDay = mostActiveDay,
            leastActiveDay = leastActiveDay,
            loggingTrend = loggingTrend,
            weeklyActivityPattern = weeklyActivityPattern,
            monthlyActivityData = monthlyActivityData,
            loggingConsistency = loggingConsistency,
            totalDaysWithRecords = totalDaysWithRecords,
            averageDosesPerActiveDay = averageDosesPerActiveDay
        )
    }

    private fun calculatePreviousPeriodAdherence(
        start: LocalDate,
        end: LocalDate,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        medications: List<com.d4vram.pillmate2.model.Medication>,
        selectedMedicationId: String?
    ): Float {
        // Lógica simplificada para calcular adherencia del período anterior
        val days = ChronoUnit.DAYS.between(start, end) + 1
        val medsById = medications.associateBy { it.id }

        var totalTaken = 0
        var totalPrescribed = 0

        (0 until days.toInt()).forEach { offset ->
            val date = start.plusDays(offset.toLong())
            val dayEntries = history.filter { it.timestamp.toLocalDate() == date }

            if (selectedMedicationId != null) {
                val med = medsById[selectedMedicationId]
                if (med != null) {
                    val taken = dayEntries.filter { it.medicationId == med.id }.sumOf { it.quantity }
                    val prescribed = if (med.isPrn) 0 else med.dailyGoal
                    totalTaken += minOf(taken, prescribed)
                    totalPrescribed += prescribed
                }
            } else {
                medications.forEach { med ->
                    val taken = dayEntries.filter { it.medicationId == med.id }.sumOf { it.quantity }
                    val prescribed = if (med.isPrn) 0 else med.dailyGoal
                    totalTaken += minOf(taken, prescribed)
                    totalPrescribed += prescribed
                }
            }
        }

        return if (totalPrescribed > 0) (totalTaken.toFloat() / totalPrescribed * 100) else 0f
    }

    private fun calculateWeeklyActivityPattern(
        history: List<com.d4vram.pillmate2.model.HistoryEntry>
    ): Map<String, Int> {
        val dayNames = listOf("Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom")

        return dayNames.associateWith { dayName ->
            val dayIndex = dayNames.indexOf(dayName) + 1 // DayOfWeek.MONDAY = 1
            history.filter { it.timestamp.dayOfWeek.value == dayIndex }
                   .sumOf { it.quantity }
        }
    }

    private fun calculateMonthlyActivityData(
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        selectedMedicationId: String?
    ): List<MonthlyActivityPoint> {
        val now = LocalDate.now()
        val spanishLocale = java.util.Locale.forLanguageTag("es")
        val monthFormatter = java.time.format.DateTimeFormatter.ofPattern("MMM yyyy", spanishLocale)

        return (0..5).map { monthsBack ->
            val monthStart = now.minusMonths(monthsBack.toLong()).withDayOfMonth(1)
            val monthEnd = monthStart.plusMonths(1).minusDays(1)

            val monthHistory = if (selectedMedicationId != null) {
                history.filter { it.medicationId == selectedMedicationId &&
                               it.timestamp.toLocalDate() in monthStart..monthEnd }
            } else {
                history.filter { it.timestamp.toLocalDate() in monthStart..monthEnd }
            }

            val totalDoses = monthHistory.sumOf { it.quantity }
            val activeDays = monthHistory.map { it.timestamp.toLocalDate() }.distinct().size
            val daysInMonth = ChronoUnit.DAYS.between(monthStart, monthEnd) + 1
            val averagePerDay = if (daysInMonth > 0) totalDoses.toFloat() / daysInMonth else 0f

            MonthlyActivityPoint(
                month = monthStart.format(monthFormatter),
                totalDoses = totalDoses,
                activeDays = activeDays,
                averagePerDay = averagePerDay
            )
        }.reversed() // Más reciente primero
    }

    private data class DailyAdherenceData(
        val date: LocalDate,
        val taken: Int,
        val prescribed: Int
    )

    private data class TrackingMetrics(
        val totalDosesLogged: Int,
        val averageDailyDoses: Float,
        val mostActiveDay: String,
        val leastActiveDay: String,
        val loggingTrend: Int,
        val weeklyActivityPattern: Map<String, Int>,
        val monthlyActivityData: List<MonthlyActivityPoint>,
        val loggingConsistency: String,
        val totalDaysWithRecords: Int,
        val averageDosesPerActiveDay: Float
    )

    data class MonthlyActivityPoint(
        val month: String,
        val totalDoses: Int,
        val activeDays: Int,
        val averagePerDay: Float
    )

    private fun generateAdherenceData(
        start: LocalDate,
        end: LocalDate,
        history: List<com.d4vram.pillmate2.model.HistoryEntry>,
        medications: List<com.d4vram.pillmate2.model.Medication> = emptyList(),
        selectedMedicationId: String? = null
    ): List<AdherenceDataPoint> {
        val days = ChronoUnit.DAYS.between(start, end).toInt() + 1

        val (chartStart, chartDays) = if (days > 31) {
              end.minusDays(30) to 31
         } else {
              start to days
         }

        val spanishLocale = java.util.Locale.forLanguageTag("es")
        val medsById = medications.associateBy { it.id }

        return (0 until chartDays).map { offset ->
            val date = chartStart.plusDays(offset.toLong())
            val dayName = if (chartDays <= 7) {
                date.format(java.time.format.DateTimeFormatter.ofPattern("EE", spanishLocale))
            } else {
                date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM"))
            }

            val fullDate = date.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM d 'de' MMMM", spanishLocale))

            // Tomas reales ese día
            val entriesOnDate = history.filter { it.timestamp.toLocalDate() == date }
            val takenCount = entriesOnDate.sumOf { it.quantity }

            // 🔥 CALCULAR META DIARIA REAL 🔥
            val dayMedications = if (selectedMedicationId != null) {
                val med = medsById[selectedMedicationId]
                if (med != null) listOf(med) else emptyList()
            } else {
                medications.filter { med ->
                    val medCreationDate = if (med.createdAt > 0) {
                        Instant.ofEpochMilli(med.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
                    } else LocalDate.now()
                    !date.isBefore(medCreationDate)
                }
            }

            val dayGoal = dayMedications.sumOf { if (it.isPrn) 0 else it.dailyGoal }
            val percentage = if (dayGoal > 0) {
                minOf(takenCount.toFloat() / dayGoal, 1f) * 100f
            } else 0f

            AdherenceDataPoint(
                dayName = dayName,
                percentage = percentage,
                taken = takenCount,
                goal = dayGoal,
                totalValue = takenCount.toFloat(),
                unit = "dosis",
                fullDate = fullDate
            )
        }
    }

    companion object {
        fun provideFactory(
            repository: MedicationRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return StatisticsViewModel(repository) as T
            }
        }
    }
}
