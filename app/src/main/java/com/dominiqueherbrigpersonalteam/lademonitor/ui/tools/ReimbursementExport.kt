package com.dominiqueherbrigpersonalteam.lademonitor.ui.tools

import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.dominiqueherbrigpersonalteam.lademonitor.R
import com.dominiqueherbrigpersonalteam.lademonitor.data.model.ReimbursementMode
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReimbursementCalculator
import com.dominiqueherbrigpersonalteam.lademonitor.data.repo.LocalReimbursementCalculator.KwhSource
import com.dominiqueherbrigpersonalteam.lademonitor.ui.common.Fmt
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

/**
 * CSV und PDF des Erstattungsberichts plus Teilen ueber das System-Teilen-Menue.
 *
 * Die CSV folgt `reimbursement.to_csv()` im Server: Deutsch mit Semikolon und Dezimalkomma (so
 * oeffnet Excel sie direkt), Englisch mit Komma und Punkt, UTF-8 mit BOM, Kopfzeilen oben,
 * Monatssummen unten. Welche Sprache, entscheidet `reimbursement_csv_language` - also dieselbe
 * Sprache, in der die App ihre Texte zeigt.
 */
object ReimbursementExport {

    private const val REPORT_DIR = "reports"

    fun sourceLabel(context: Context, source: KwhSource): String = context.getString(
        when (source) {
            KwhSource.CHARGER -> R.string.reimbursement_source_charger
            KwhSource.VEHICLE -> R.string.reimbursement_source_vehicle
            KwhSource.ESTIMATED -> R.string.reimbursement_source_estimated
        }
    )

    private fun modeLabel(context: Context, report: LocalReimbursementCalculator.Report): String {
        val base = context.getString(report.mode.labelRes)
        return if (report.includeFees) "$base (${context.getString(R.string.reimbursement_csv_with_fees)})" else base
    }

    fun fileName(report: LocalReimbursementCalculator.Report, extension: String): String =
        "lademonitor-erstattung-${report.dateFrom}_${report.dateTo}.$extension"

    // MARK: - CSV

    fun csv(context: Context, report: LocalReimbursementCalculator.Report): String {
        val german = context.getString(R.string.reimbursement_csv_language) == "de"
        val delimiter = if (german) ';' else ','
        val zone = ZoneId.systemDefault()
        val timeFormat = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

        fun num(value: Double?, digits: Int = 2): String {
            if (value == null) return ""
            val text = String.format(Locale.ROOT, "%.${digits}f", value)
            return if (german) text.replace('.', ',') else text
        }

        // Wie Pythons csv.writer (QUOTE_MINIMAL): nur Felder mit Trennzeichen, Anfuehrungszeichen
        // oder Zeilenumbruch werden in Anfuehrungszeichen gesetzt, enthaltene verdoppelt.
        fun field(value: String): String =
            if (value.any { it == delimiter || it == '"' || it == '\n' || it == '\r' }) {
                "\"" + value.replace("\"", "\"\"") + "\""
            } else value

        val out = StringBuilder("﻿")
        fun row(vararg values: String) {
            out.append(values.joinToString(delimiter.toString()) { field(it) }).append("\r\n")
        }
        fun s(id: Int) = context.getString(id)

        row(s(R.string.reimbursement_csv_title))
        row(s(R.string.reimbursement_csv_vehicle), report.vehicleName)
        report.licensePlate?.takeIf { it.isNotEmpty() }?.let { row(s(R.string.reimbursement_csv_license_plate), it) }
        report.employeeName?.takeIf { it.isNotEmpty() }?.let { row(s(R.string.reimbursement_csv_employee_name), it) }
        report.employeeNumber?.takeIf { it.isNotEmpty() }?.let { row(s(R.string.reimbursement_csv_employee_number), it) }
        row(s(R.string.reimbursement_csv_period), "${report.dateFrom} - ${report.dateTo}")
        row(s(R.string.reimbursement_csv_mode), modeLabel(context, report))
        row()
        row(
            s(R.string.reimbursement_csv_date), s(R.string.reimbursement_csv_time), "kWh",
            s(R.string.reimbursement_csv_source), s(R.string.reimbursement_csv_odometer),
            s(R.string.reimbursement_csv_rate), s(R.string.reimbursement_csv_fee_share), s(R.string.reimbursement_csv_amount)
        )
        for (r in report.rows) {
            val start = Instant.ofEpochMilli(r.startTime).atZone(zone)
            row(
                start.toLocalDate().toString(), start.format(timeFormat),
                num(r.energyKwh), sourceLabel(context, r.kwhSource),
                r.odometerKm?.toString() ?: "",
                num(r.ratePerKwh, 4), if (r.feeShare != 0.0) num(r.feeShare) else "", num(r.amount)
            )
        }
        row()
        row(
            s(R.string.reimbursement_csv_month), s(R.string.reimbursement_csv_sessions), "kWh",
            s(R.string.reimbursement_csv_estimated_kwh), s(R.string.reimbursement_csv_amount)
        )
        for (m in report.months) {
            row(m.month, m.sessions.toString(), num(m.energyKwh), num(m.estimatedKwh), num(m.amount))
        }
        row(
            s(R.string.reimbursement_csv_total), report.rows.size.toString(), num(report.totalKwh),
            num(report.estimatedKwh), num(report.totalAmount)
        )
        if (report.missingAmountSessions > 0) {
            row()
            row(context.getString(R.string.reimbursement_csv_missing, report.missingAmountSessions))
        }
        return out.toString()
    }

    // MARK: - PDF

    /** Kurze Hinweise unter dem Bericht - dieselben Texte wie in der Ansicht. */
    fun notes(context: Context, report: LocalReimbursementCalculator.Report): List<String> = buildList {
        if (report.estimatedSessions > 0) {
            add(
                context.getString(
                    R.string.reimbursement_report_estimated_note,
                    report.estimatedSessions, Fmt.n("%.2f", report.estimatedKwh)
                )
            )
        }
        if (report.missingAmountSessions > 0) {
            add(
                context.getString(
                    if (report.mode == ReimbursementMode.FLAT) R.string.reimbursement_report_missing_flat
                    else R.string.reimbursement_report_missing_actual,
                    report.missingAmountSessions
                )
            )
        }
    }

    /** A4 hochkant, eine Tabelle je Vorgang, darunter die Monatssummen und die Hinweise. */
    fun pdf(context: Context, report: LocalReimbursementCalculator.Report, file: File) {
        val pageWidth = 595
        val pageHeight = 842
        val margin = 40f
        val bottom = pageHeight - margin
        val doc = PdfDocument()

        val title = Paint().apply { textSize = 16f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val bold = Paint().apply { textSize = 9f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }
        val normal = Paint().apply { textSize = 9f; isAntiAlias = true }
        val small = Paint().apply { textSize = 8f; isAntiAlias = true; color = 0xFF555555.toInt() }
        val line = Paint().apply { strokeWidth = 0.5f; color = 0xFF999999.toInt() }
        val lineHeight = 13f

        var pageNumber = 0
        var page: PdfDocument.Page? = null
        var y = 0f

        fun startPage() {
            page?.let { doc.finishPage(it) }
            pageNumber += 1
            page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create())
            y = margin
        }
        fun canvas() = page!!.canvas
        fun ensure(space: Float) { if (y + space > bottom) startPage() }

        /** Bricht [text] auf [width] um und schreibt ihn ab der aktuellen Zeile. */
        fun paragraph(text: String, paint: Paint, width: Float = pageWidth - 2 * margin) {
            val words = text.split(" ")
            var current = ""
            for (word in words) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) > width && current.isNotEmpty()) {
                    ensure(lineHeight)
                    canvas().drawText(current, margin, y + paint.textSize, paint)
                    y += lineHeight
                    current = word
                } else current = candidate
            }
            if (current.isNotEmpty()) {
                ensure(lineHeight)
                canvas().drawText(current, margin, y + paint.textSize, paint)
                y += lineHeight
            }
        }

        startPage()
        canvas().drawText(context.getString(R.string.reimbursement_csv_title), margin, y + title.textSize, title)
        y += 26f

        val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
        val header = buildList {
            add(context.getString(R.string.reimbursement_csv_vehicle) to report.vehicleName)
            report.licensePlate?.takeIf { it.isNotEmpty() }?.let { add(context.getString(R.string.reimbursement_csv_license_plate) to it) }
            report.employeeName?.takeIf { it.isNotEmpty() }?.let { add(context.getString(R.string.reimbursement_csv_employee_name) to it) }
            report.employeeNumber?.takeIf { it.isNotEmpty() }?.let { add(context.getString(R.string.reimbursement_csv_employee_number) to it) }
            add(
                context.getString(R.string.reimbursement_csv_period) to
                    "${report.dateFrom.format(dateFormat)} – ${report.dateTo.format(dateFormat)}"
            )
            add(context.getString(R.string.reimbursement_csv_mode) to modeLabel(context, report))
        }
        for ((label, value) in header) {
            canvas().drawText(label, margin, y + bold.textSize, bold)
            canvas().drawText(value, margin + 110f, y + normal.textSize, normal)
            y += lineHeight
        }
        y += 10f

        // Spalten: Datum/Uhrzeit | kWh | Herkunft | km-Stand | EUR/kWh | [Grundgebuehr] | Betrag.
        // Zahlen rechtsbuendig an der rechten Spaltenkante.
        val showFees = report.includeFees
        val colRight = if (showFees) floatArrayOf(150f, 210f, 290f, 350f, 410f, 470f, 555f)
        else floatArrayOf(170f, 240f, 330f, 400f, 470f, 470f, 555f)
        fun tableHeader() {
            ensure(lineHeight * 2)
            val labels = listOf(
                context.getString(R.string.reimbursement_csv_date), "kWh",
                context.getString(R.string.reimbursement_csv_source), context.getString(R.string.reimbursement_csv_odometer),
                context.getString(R.string.reimbursement_csv_rate), context.getString(R.string.reimbursement_csv_fee_share),
                context.getString(R.string.reimbursement_csv_amount)
            )
            canvas().drawText(labels[0], margin, y + bold.textSize, bold)
            for (i in 1 until labels.size) {
                if (i == 5 && !showFees) continue
                val text = labels[i]
                val x = if (i == 2) colRight[1] + 12f else colRight[i] - bold.measureText(text)
                canvas().drawText(text, x, y + bold.textSize, bold)
            }
            y += lineHeight
            canvas().drawLine(margin, y, pageWidth - margin, y, line)
            y += 3f
        }

        if (report.rows.isEmpty()) {
            paragraph(context.getString(R.string.reimbursement_report_empty), normal)
        } else {
            tableHeader()
            for (r in report.rows) {
                if (y + lineHeight > bottom) { startPage(); tableHeader() }
                val cells = listOf(
                    Fmt.dateTimeShort(r.startTime),
                    r.energyKwh?.let { Fmt.n("%.2f", it) } ?: "–",
                    sourceLabel(context, r.kwhSource) + if (r.kwhSource == KwhSource.ESTIMATED) "*" else "",
                    r.odometerKm?.let { Fmt.km(it) } ?: "",
                    r.ratePerKwh?.let { Fmt.n("%.4f", it) } ?: "–",
                    if (r.feeShare != 0.0) Fmt.n("%.2f", r.feeShare) else "",
                    r.amount?.let { Fmt.n("%.2f €", it) } ?: "–"
                )
                canvas().drawText(cells[0], margin, y + normal.textSize, normal)
                for (i in 1 until cells.size) {
                    if (i == 5 && !showFees) continue
                    val text = cells[i]
                    val x = if (i == 2) colRight[1] + 12f else colRight[i] - normal.measureText(text)
                    canvas().drawText(text, x, y + normal.textSize, normal)
                }
                y += lineHeight
            }
            canvas().drawLine(margin, y, pageWidth - margin, y, line)
            y += 14f

            ensure(lineHeight * 3)
            canvas().drawText(context.getString(R.string.reimbursement_report_months), margin, y + bold.textSize, bold)
            y += lineHeight + 2f
            val monthCols = floatArrayOf(260f, 340f, 440f, 555f)
            val monthHeader = listOf(
                context.getString(R.string.reimbursement_csv_sessions), "kWh",
                context.getString(R.string.reimbursement_csv_estimated_kwh), context.getString(R.string.reimbursement_csv_amount)
            )
            ensure(lineHeight)
            canvas().drawText(context.getString(R.string.reimbursement_csv_month), margin, y + bold.textSize, bold)
            monthHeader.forEachIndexed { i, text ->
                canvas().drawText(text, monthCols[i] - bold.measureText(text), y + bold.textSize, bold)
            }
            y += lineHeight
            fun monthLine(label: String, sessions: Int, kwh: Double, est: Double, amount: Double, paint: Paint) {
                ensure(lineHeight)
                canvas().drawText(label, margin, y + paint.textSize, paint)
                listOf(sessions.toString(), Fmt.n("%.2f", kwh), Fmt.n("%.2f", est), Fmt.n("%.2f €", amount))
                    .forEachIndexed { i, text ->
                        canvas().drawText(text, monthCols[i] - paint.measureText(text), y + paint.textSize, paint)
                    }
                y += lineHeight
            }
            for (m in report.months) monthLine(m.month, m.sessions, m.energyKwh, m.estimatedKwh, m.amount, normal)
            ensure(lineHeight + 4f)
            canvas().drawLine(margin, y, pageWidth - margin, y, line)
            y += 3f
            monthLine(
                context.getString(R.string.reimbursement_csv_total), report.rows.size,
                report.totalKwh, report.estimatedKwh, report.totalAmount, bold
            )
            y += 8f
            for (note in notes(context, report)) paragraph(note, small)
        }

        y += 10f
        val created = LocalDate.now().format(dateFormat)
        paragraph(context.getString(R.string.reimbursement_report_created_at, created), small)

        page?.let { doc.finishPage(it) }
        try {
            file.outputStream().use { doc.writeTo(it) }
        } finally {
            doc.close()
        }
    }

    // MARK: - Teilen

    private fun reportFile(context: Context, name: String): File {
        val dir = File(context.cacheDir, REPORT_DIR).apply { mkdirs() }
        return File(dir, name)
    }

    fun shareCsv(context: Context, report: LocalReimbursementCalculator.Report) {
        val file = reportFile(context, fileName(report, "csv"))
        file.writeText(csv(context, report), Charsets.UTF_8)
        share(context, file, "text/csv")
    }

    fun sharePdf(context: Context, report: LocalReimbursementCalculator.Report) {
        val file = reportFile(context, fileName(report, "pdf"))
        pdf(context, report, file)
        share(context, file, "application/pdf")
    }

    /** Ueber den FileProvider aus dem Manifest (`${applicationId}.fileprovider`, cache/reports). */
    private fun share(context: Context, file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, context.getString(R.string.reimbursement_share_chooser))
        // Aus einem Composable heraus ist der Context eine Activity; sicherheitshalber trotzdem.
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
