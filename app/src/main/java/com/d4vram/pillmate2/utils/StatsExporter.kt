package com.d4vram.pillmate2.utils

import com.d4vram.pillmate2.R
import android.content.Context
import android.os.Environment
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.io.File
import java.io.FileWriter
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
                fun t(id: Int) = context.getString(id)
                appendLine("${t(R.string.csv_metric)},${t(R.string.csv_value)}")

                // Estadísticas generales (Foco en seguimiento)
                appendLine("${t(R.string.csv_total_doses)},$totalDoses")
                appendLine("${t(R.string.csv_daily_average)},$averageDaily")
                appendLine("${t(R.string.csv_active_meds)},${medications.size}")
                appendLine("${t(R.string.csv_total_events)},${history.size}")

                // Estadísticas por medicamento
                appendLine("")
                appendLine("${t(R.string.stats_medication_fallback)},${t(R.string.csv_col_total)},${t(R.string.csv_col_type)}")
                medications.forEach { med ->
                    val totalDosesForMed = history.filter { it.medicationId == med.id }.sumOf { it.quantity }
                    appendLine("${med.name},$totalDosesForMed,${t(R.string.csv_regular)}")
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