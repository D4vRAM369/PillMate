package com.d4vram.pillmate2.ui.export

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import com.d4vram.pillmate2.model.HistoryEntry
import com.d4vram.pillmate2.model.Medication
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Datos necesarios para generar el PDF.
 * Se obtienen del repositorio antes de iniciar la generación.
 */
data class PdfReportData(
    val medications: List<Medication>,
    val history: List<HistoryEntry>,
    val startDate: LocalDate,
    val endDate: LocalDate,
    // Métricas calculadas
    val totalValue: Float,
    val displayUnit: String,
    val averageDaily: Float,
    val daysWithRecords: Int,
    val totalDays: Int,
    // Datos por medicamento
    val medicationTotals: List<MedicationTotal>,
    // Datos para gráficas
    val dailyData: List<DailyDataPoint>,
    val hourlyPattern: Map<String, Int>
)

data class MedicationTotal(
    val medication: Medication,
    val totalValue: Float,
    val displayUnit: String,
    val percentage: Float
)

data class DailyDataPoint(
    val date: LocalDate,
    val value: Float
)

/**
 * Generador de reportes PDF.
 * Usa la API nativa de Android (android.graphics.pdf.PdfDocument).
 */
class PdfGenerator(private val context: Context) {

    // Dimensiones de página A4 en puntos (72 puntos = 1 pulgada)
    private val pageWidth = 595  // ~210mm
    private val pageHeight = 842 // ~297mm

    /**
     * Genera el PDF con la configuración especificada.
     * @return File del PDF generado, o null si falla
     */
    fun generate(
        config: PdfExportConfig,
        data: PdfReportData
    ): File? {
        val style = PdfStyle.fromType(config.style)
        val document = PdfDocument()

        try {
            var currentPageNumber = 1
            var yPosition = style.pageMargin

            // Crear primera página
            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            // Dibujar fondo
            canvas.drawColor(style.backgroundColor)

            // --- PORTADA ---
            yPosition = drawHeader(canvas, style, data, yPosition)
            yPosition += style.sectionSpacing

            // --- SECCIONES SELECCIONADAS ---

            // Resumen general
            if (PdfSection.SUMMARY in config.sections) {
                yPosition = drawSummarySection(canvas, style, data, yPosition)
                yPosition += style.sectionSpacing
            }

            // Lista de medicamentos
            if (PdfSection.MEDICATIONS in config.sections) {
                // Verificar si hay espacio, si no, nueva página
                if (yPosition > pageHeight - 200) {
                    document.finishPage(page)
                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    canvas.drawColor(style.backgroundColor)
                    yPosition = style.pageMargin
                }
                yPosition = drawMedicationsSection(canvas, style, data, yPosition)
                yPosition += style.sectionSpacing
            }

            // Historial de tomas
            if (PdfSection.HISTORY in config.sections) {
                if (yPosition > pageHeight - 200) {
                    document.finishPage(page)
                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    canvas.drawColor(style.backgroundColor)
                    yPosition = style.pageMargin
                }
                yPosition = drawHistorySection(canvas, style, data, yPosition, document, currentPageNumber)
            }

            // Distribución horaria
            if (PdfSection.HOURLY_DISTRIBUTION in config.sections) {
                if (yPosition > pageHeight - 180) {
                    document.finishPage(page)
                    currentPageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, currentPageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    canvas.drawColor(style.backgroundColor)
                    yPosition = style.pageMargin
                }
                yPosition = drawHourlySection(canvas, style, data, yPosition)
            }

            // Finalizar última página
            document.finishPage(page)

            // Guardar archivo
            val fileName = generateFileName(data.startDate, data.endDate)
            val file = File(context.cacheDir, fileName)
            FileOutputStream(file).use { outputStream ->
                document.writeTo(outputStream)
            }

            return file

        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            document.close()
        }
    }

    /**
     * Dibuja la cabecera del reporte (título y periodo)
     */
    private fun drawHeader(
        canvas: Canvas,
        style: PdfStyle,
        data: PdfReportData,
        startY: Float
    ): Float {
        var y = startY
        val centerX = pageWidth / 2f

        // Título principal
        val titlePaint = style.titlePaint().apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("PillMate", centerX, y + style.titleSize, titlePaint)
        y += style.titleSize + style.lineSpacing

        // Subtítulo
        val subtitlePaint = style.subtitlePaint().apply {
            textAlign = Paint.Align.CENTER
            color = style.textSecondary
        }
        canvas.drawText("Reporte de Medicación", centerX, y + style.subtitleSize, subtitlePaint)
        y += style.subtitleSize + style.sectionSpacing

        // Periodo
        val dateFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        val periodText = "Periodo: ${data.startDate.format(dateFormatter)} - ${data.endDate.format(dateFormatter)}"
        val secondaryPaint = style.secondaryPaint().apply {
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText(periodText, centerX, y + style.bodySize, secondaryPaint)
        y += style.bodySize + style.lineSpacing

        // Fecha de generación
        val nowFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        val generatedText = "Generado: ${LocalDateTime.now().format(nowFormatter)}"
        canvas.drawText(generatedText, centerX, y + style.smallSize, style.smallPaint().apply {
            textAlign = Paint.Align.CENTER
        })
        y += style.smallSize + style.lineSpacing

        // Línea separadora
        y += 10f
        val linePaint = Paint().apply {
            color = style.borderColor
            strokeWidth = 1f
        }
        canvas.drawLine(style.pageMargin, y, pageWidth - style.pageMargin, y, linePaint)
        y += 10f

        return y
    }

    /**
     * Dibuja la sección de resumen general
     */
    private fun drawSummarySection(
        canvas: Canvas,
        style: PdfStyle,
        data: PdfReportData,
        startY: Float
    ): Float {
        var y = startY
        val leftMargin = style.pageMargin

        // Título de sección
        canvas.drawText("RESUMEN", leftMargin, y + style.subtitleSize, style.subtitlePaint())
        y += style.subtitleSize + style.lineSpacing * 2

        // Card/contenedor
        val cardTop = y
        val cardPadding = 12f

        // Datos del resumen
        val bodyPaint = style.bodyPaint()
        val accentPaint = style.accentPaint()

        val items = listOf(
            "Total registrado:" to "${formatValue(data.totalValue)} ${data.displayUnit}",
            "Promedio diario:" to "${formatValue(data.averageDaily)} ${data.displayUnit}/día",
            "Días con registro:" to "${data.daysWithRecords} de ${data.totalDays}",
            "Medicamentos activos:" to "${data.medications.size}"
        )

        y += cardPadding
        items.forEach { (label, value) ->
            canvas.drawText(label, leftMargin + cardPadding, y + style.bodySize, bodyPaint)
            canvas.drawText(
                value,
                pageWidth - style.pageMargin - cardPadding,
                y + style.bodySize,
                accentPaint.apply { textAlign = Paint.Align.RIGHT }
            )
            y += style.bodySize + style.lineSpacing
        }
        y += cardPadding

        // Dibujar borde del card si el estilo lo requiere
        if (style.showCardBorders) {
            val cardRect = RectF(
                leftMargin,
                cardTop,
                pageWidth - style.pageMargin,
                y
            )
            val borderPaint = Paint().apply {
                color = style.borderColor
                this.style = Paint.Style.STROKE
                strokeWidth = 1f
            }
            canvas.drawRoundRect(cardRect, 4f, 4f, borderPaint)
        }

        return y
    }

    /**
     * Dibuja la sección de lista de medicamentos
     */
    private fun drawMedicationsSection(
        canvas: Canvas,
        style: PdfStyle,
        data: PdfReportData,
        startY: Float
    ): Float {
        var y = startY
        val leftMargin = style.pageMargin

        // Título de sección
        canvas.drawText("MEDICAMENTOS", leftMargin, y + style.subtitleSize, style.subtitlePaint())
        y += style.subtitleSize + style.lineSpacing * 2

        // Lista de medicamentos
        val bodyPaint = style.bodyPaint()

        data.medicationTotals.forEach { medTotal ->
            // Indicador de color (si el estilo lo permite)
            if (style.useColoredMedications) {
                val colorPaint = Paint().apply {
                    color = medTotal.medication.color.toArgb()
                }
                canvas.drawCircle(leftMargin + 6f, y + style.bodySize / 2, 4f, colorPaint)
            }

            val nameX = if (style.useColoredMedications) leftMargin + 18f else leftMargin

            // Nombre del medicamento
            canvas.drawText(
                "${medTotal.medication.name} ${medTotal.medication.dose}",
                nameX,
                y + style.bodySize,
                bodyPaint
            )

            // Valor y porcentaje
            val valueText = "${formatValue(medTotal.totalValue)}${medTotal.displayUnit} (${(medTotal.percentage * 100).toInt()}%)"
            canvas.drawText(
                valueText,
                pageWidth - style.pageMargin,
                y + style.bodySize,
                style.secondaryPaint().apply { textAlign = Paint.Align.RIGHT }
            )

            y += style.bodySize + style.lineSpacing
        }

        return y
    }

    /**
     * Dibuja la sección de historial de tomas
     */
    private fun drawHistorySection(
        canvas: Canvas,
        style: PdfStyle,
        data: PdfReportData,
        startY: Float,
        document: PdfDocument,
        pageNumber: Int
    ): Float {
        var y = startY
        val leftMargin = style.pageMargin

        // Título de sección
        canvas.drawText("HISTORIAL DE TOMAS", leftMargin, y + style.subtitleSize, style.subtitlePaint())
        y += style.subtitleSize + style.lineSpacing * 2

        // Cabecera de tabla
        val headerPaint = style.bodyPaint().apply {
            typeface = android.graphics.Typeface.create(typeface, android.graphics.Typeface.BOLD)
        }

        val col1 = leftMargin
        val col2 = leftMargin + 80f
        val col3 = leftMargin + 160f
        val col4 = pageWidth - style.pageMargin

        if (style.showTableBorders) {
            canvas.drawLine(leftMargin, y, pageWidth - style.pageMargin, y, Paint().apply {
                color = style.borderColor
            })
        }
        y += 4f

        canvas.drawText("Fecha", col1, y + style.bodySize, headerPaint)
        canvas.drawText("Hora", col2, y + style.bodySize, headerPaint)
        canvas.drawText("Medicamento", col3, y + style.bodySize, headerPaint)
        canvas.drawText("Dosis", col4, y + style.bodySize, headerPaint.apply {
            textAlign = Paint.Align.RIGHT
        })
        y += style.bodySize + style.lineSpacing

        if (style.showTableBorders) {
            canvas.drawLine(leftMargin, y, pageWidth - style.pageMargin, y, Paint().apply {
                color = style.borderColor
            })
        }
        y += 4f

        // Filas de historial (limitado a 50 para no hacer el PDF muy largo)
        val bodyPaint = style.bodyPaint()
        val limitedHistory = data.history.take(50)

        limitedHistory.forEach { entry ->
            canvas.drawText(entry.date, col1, y + style.bodySize, bodyPaint)
            canvas.drawText(entry.time, col2, y + style.bodySize, bodyPaint)
            canvas.drawText(entry.medicationName, col3, y + style.bodySize, bodyPaint)

            val med = data.medications.find { it.id == entry.medicationId }
            val doseText = if (med != null) {
                "${entry.quantity}x ${med.dose}"
            } else {
                "${entry.quantity} tomas"
            }
            canvas.drawText(doseText, col4, y + style.bodySize, bodyPaint.apply {
                textAlign = Paint.Align.RIGHT
            })

            y += style.bodySize + style.lineSpacing
        }

        if (data.history.size > 50) {
            y += style.lineSpacing
            canvas.drawText(
                "... y ${data.history.size - 50} registros más",
                leftMargin,
                y + style.smallSize,
                style.smallPaint()
            )
            y += style.smallSize
        }

        return y
    }

    /**
     * Dibuja la sección de distribución horaria
     */
    private fun drawHourlySection(
        canvas: Canvas,
        style: PdfStyle,
        data: PdfReportData,
        startY: Float
    ): Float {
        var y = startY
        val leftMargin = style.pageMargin

        // Título de sección
        canvas.drawText("DISTRIBUCIÓN HORARIA", leftMargin, y + style.subtitleSize, style.subtitlePaint())
        y += style.subtitleSize + style.lineSpacing * 2

        val slots = listOf(
            "Madrugada" to "00-06",
            "Mañana" to "06-12",
            "Mediodía" to "12-15",
            "Tarde" to "15-19",
            "Noche" to "19-23",
            "Nocturno" to "23-00"
        )

        val maxValue = data.hourlyPattern.values.maxOrNull()?.toFloat() ?: 1f
        val barMaxWidth = pageWidth - style.pageMargin * 2 - 120f

        val bodyPaint = style.bodyPaint()
        val barPaint = Paint().apply {
            color = style.accentColor
        }

        slots.forEach { (name, range) ->
            val value = data.hourlyPattern[name] ?: 0
            val barWidth = if (maxValue > 0) (value / maxValue) * barMaxWidth else 0f

            // Nombre del slot
            canvas.drawText(name, leftMargin, y + style.bodySize, bodyPaint)

            // Rango horario
            canvas.drawText(range, leftMargin + 70f, y + style.bodySize, style.smallPaint())

            // Barra
            val barLeft = leftMargin + 110f
            val barTop = y + 2f
            val barBottom = y + style.bodySize - 2f
            if (barWidth > 0) {
                canvas.drawRoundRect(
                    RectF(barLeft, barTop, barLeft + barWidth, barBottom),
                    3f, 3f,
                    barPaint
                )
            }

            // Valor
            canvas.drawText(
                value.toString(),
                pageWidth - style.pageMargin,
                y + style.bodySize,
                style.secondaryPaint().apply { textAlign = Paint.Align.RIGHT }
            )

            y += style.bodySize + style.lineSpacing + 4f
        }

        return y
    }

    /**
     * Genera un nombre de archivo descriptivo
     */
    private fun generateFileName(startDate: LocalDate, endDate: LocalDate): String {
        val formatter = DateTimeFormatter.ofPattern("dd-MM")
        return "PillMate_Reporte_${startDate.format(formatter)}_${endDate.format(formatter)}.pdf"
    }

    /**
     * Formatea un valor numérico
     */
    private fun formatValue(value: Float): String {
        return if (value == value.toInt().toFloat()) {
            value.toInt().toString()
        } else {
            String.format("%.1f", value)
        }
    }

    /**
     * Convierte Color de Compose a Int de Android
     */
    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (alpha * 255).toInt(),
            (red * 255).toInt(),
            (green * 255).toInt(),
            (blue * 255).toInt()
        )
    }
}
