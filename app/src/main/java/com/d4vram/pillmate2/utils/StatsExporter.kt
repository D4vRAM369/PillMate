package com.d4vram.pillmate2.utils

import com.d4vram.pillmate2.R
import android.content.Context
import android.os.Environment
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.text.NumberFormat
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal data class MedicationStatisticsRow(
    val medication: Medication,
    val takeCount: Int,
    val amount: MedicationAmount
)

internal fun medicationStatisticsRows(
    history: List<HistoryEntry>,
    medications: List<Medication>
): List<MedicationStatisticsRow> {
    val entriesByMedication = history.groupBy { it.medicationId }
    return medications.mapNotNull { medication ->
        val entries = entriesByMedication[medication.id].orEmpty()
        if (entries.isEmpty()) return@mapNotNull null
        MedicationStatisticsRow(
            medication = medication,
            takeCount = countTakeEvents(entries),
            amount = amountForMedication(medication, entries)
        )
    }
}

/**
 * 🔥 UTILIDAD PARA EXPORTAR DATOS ESTADÍSTICOS 🔥
 * Genera archivos CSV con datos de medicamentos e historial
 */
class StatsExporter(private val context: Context) {

    /**
     * Exporta resumen estadístico a CSV
     */
    fun exportSummaryToCSV(
        totalDoses: Int,
        averageDaily: String,
        medications: List<Medication>,
        history: List<HistoryEntry>,
        fileName: String = "pillmate_summary_${System.currentTimeMillis()}.csv"
    ): File? {
        return try {
            val csvContent = buildString {
                // Encabezados
                fun t(id: Int, vararg args: Any) = context.getString(id, *args)
                appendLine("${t(R.string.csv_metric)},${t(R.string.csv_value)}")

                // Estadísticas generales (Foco en seguimiento)
                appendLine("${t(R.string.csv_total_doses)},$totalDoses")
                appendLine("${t(R.string.csv_daily_average)},$averageDaily")
                appendLine("${t(R.string.csv_active_meds)},${medications.size}")
                appendLine("${t(R.string.csv_total_events)},${history.size}")

                medicationStatisticsRows(history, medications).forEach { row ->
                    val unitLabel = when (row.amount.unit) {
                        "gotas" -> t(R.string.unit_drops)
                        "units" -> t(R.string.unit_units)
                        else -> row.amount.unit
                    }
                    val label = t(R.string.csv_medication_amount, row.medication.name, unitLabel)
                    val number = NumberFormat.getNumberInstance(
                        context.resources.configuration.locales[0]
                    ).format(row.amount.value)
                    appendLine("$label,$number")
                }

                // Estadísticas por medicamento
                appendLine("")
                appendLine("${t(R.string.stats_medication_fallback)},${t(R.string.csv_col_total)},${t(R.string.csv_col_type)}")
                medicationStatisticsRows(history, medications).forEach { row ->
                    appendLine("${row.medication.name},${row.takeCount},${t(R.string.csv_regular)}")
                }
            }

            // 📦 Guardar en cache para compartir con FileProvider
            // (FileProvider ya tiene configurado <cache-path> en file_paths.xml)
            val cacheDir = context.cacheDir
            val file = File(cacheDir, fileName)

            FileWriter(file).use { writer ->
                writer.write(csvContent)
            }

            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }


    /**
     * Verifica si el almacenamiento externo está disponible para escritura
     */
    fun isExternalStorageWritable(): Boolean {
        return Environment.getExternalStorageState() == Environment.MEDIA_MOUNTED
    }
}
