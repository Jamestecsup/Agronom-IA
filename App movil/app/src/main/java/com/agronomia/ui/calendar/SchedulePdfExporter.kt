package com.agronomia.ui.calendar

import android.content.Context
import android.net.Uri
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.agronomia.data.remote.CarePlanTaskDto
import com.agronomia.data.remote.WeeklyClassDto
import java.time.LocalTime
import java.util.Locale

object SchedulePdfExporter {
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private val dayNames = listOf("", "Lunes", "Martes", "Miércoles", "Jueves", "Viernes", "Sábado", "Domingo")

    fun write(
        context: Context,
        destination: Uri,
        classes: List<WeeklyClassDto>,
        tasks: List<CarePlanTaskDto>
    ) {
        val document = PdfDocument()
        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1B5E20.toInt(); textSize = 21f; isFakeBoldText = true }
        val sectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF176B3A.toInt(); textSize = 14f; isFakeBoldText = true }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF24202B.toInt(); textSize = 10.5f }
        val detailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF55505D.toInt(); textSize = 9.5f }
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFFD7D1DD.toInt(); strokeWidth = 0.8f }
        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var canvas: Canvas? = null
        var y = 0f

        fun newPage() {
            page?.let(document::finishPage)
            pageNumber++
            page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create())
            canvas = page!!.canvas
            y = 46f
            canvas!!.drawText("Agronom-IA · Horario semanal", 40f, y, titlePaint)
            y += 22f
            canvas!!.drawText("Clases y plan de cuidados confirmado", 40f, y, detailPaint)
            y += 18f
            canvas!!.drawLine(40f, y, PAGE_WIDTH - 40f, y, linePaint)
            y += 18f
        }

        fun ensureSpace(required: Float) {
            if (canvas == null || y + required > PAGE_HEIGHT - 45f) newPage()
        }

        fun drawWrapped(text: String, paint: Paint, indent: Float = 0f, lineHeight: Float = paint.textSize + 3f) {
            val maxWidth = PAGE_WIDTH - 80f - indent
            val words = text.split(Regex("\\s+"))
            var line = ""
            words.forEach { word ->
                val candidate = if (line.isEmpty()) word else "$line $word"
                if (line.isNotEmpty() && paint.measureText(candidate) > maxWidth) {
                    ensureSpace(lineHeight)
                    canvas!!.drawText(line, 40f + indent, y, paint)
                    y += lineHeight
                    line = word
                } else line = candidate
            }
            if (line.isNotEmpty()) {
                ensureSpace(lineHeight)
                canvas!!.drawText(line, 40f + indent, y, paint)
                y += lineHeight
            }
        }

        newPage()
        for (day in 1..7) {
            val dayClasses = classes.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
            val dayTasks = tasks.filter { it.dayOfWeek == day }.sortedBy { it.startTime }
            if (dayClasses.isEmpty() && dayTasks.isEmpty()) continue
            ensureSpace(26f)
            canvas!!.drawText(dayNames[day], 40f, y, sectionPaint)
            y += 18f
            dayClasses.forEach { block ->
                val code = block.courseCode.takeIf { it.isNotBlank() }?.let { "$it · " }.orEmpty()
                drawWrapped("${block.startTime}–${block.endTime}  $code${block.courseName}", bodyPaint, indent = 8f)
                if (block.section.isNotBlank()) drawWrapped("Sección ${block.section}", detailPaint, indent = 8f)
            }
            dayTasks.forEach { task ->
                val end = runCatching { LocalTime.parse(task.startTime).plusMinutes(task.durationMinutes.toLong()).toString() }.getOrDefault("")
                drawWrapped("${task.startTime}–$end  ${task.plantName}: ${task.title}", bodyPaint, indent = 8f)
                if (task.instructions.isNotBlank()) drawWrapped("Indicación: ${task.instructions}", detailPaint, indent = 16f)
            }
            y += 8f
            ensureSpace(1f)
            canvas!!.drawLine(40f, y, PAGE_WIDTH - 40f, y, linePaint)
            y += 16f
        }
        page?.let(document::finishPage)
        context.contentResolver.openOutputStream(destination, "w")?.use(document::writeTo)
            ?: throw IllegalStateException("No se pudo crear el archivo PDF.")
        document.close()
    }
}
