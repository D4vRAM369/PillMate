package com.d4vram.pillmate2.utils

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

    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    /**
     * Exporta datos estadísticos a CSV
     */
    fun exportStatsToCSV(
        medications: List<Medication>,
        history: List<HistoryEntry>,
        fileName: String = "pillmate_stats_${System.currentTimeMillis()}.csv"
    ): File? {
        return try {
            val csvContent = buildString {
                // Encabezados
                appendLine("Tipo,Fecha,Hora,Medicamento,Dosis,Cantidad,Notas,Notas de Voz")

                // Datos de historial
                history.forEach { entry ->
                    val medication = medications.find { it.id == entry.medicationId }
                    appendLine("Toma,${entry.date},${entry.time},${medication?.name ?: "Desconocido"},${medication?.dose ?: ""},${entry.quantity},${entry.notes.replace(",", ";")},${entry.audioNoteUri?.let { "Sí" } ?: "No"}")
                }

                // Datos de medicamentos
                medications.forEach { med ->
                    appendLine("Medicamento,,,,,,${med.name} - ${med.dose} - Meta diaria: ${med.dailyGoal}")
                }
            }

            // Crear archivo
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

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
                appendLine("Métrica,Valor")

                // Estadísticas generales (Foco en seguimiento)
                appendLine("Total de Dosis Registradas,$totalDoses")
                appendLine("Promedio Diario de Dosis,$averageDaily")
                appendLine("Total de Medicamentos Activos,${medications.size}")
                appendLine("Total de Eventos Registrados,${history.size}")

                // Estadísticas por medicamento
                appendLine("")
                appendLine("Medicamento,Dosis Totales Registradas,Tipo")
                medications.forEach { med ->
                    val totalDosesForMed = history.filter { it.medicationId == med.id }.sumOf { it.quantity }
                    appendLine("${med.name},$totalDosesForMed,${if (med.isPrn) "Según necesidad" else "Regular"}")
                }
            }

            // Crear archivo
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(downloadsDir, fileName)

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