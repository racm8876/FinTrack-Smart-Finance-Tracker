package com.example.domain

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionType
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
    CSV("csv", "text/csv", "CSV (.csv)"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Excel (.xlsx)"),
    PDF("pdf", "application/pdf", "PDF Report (.pdf)")
}

object FinancialExportEngine {

    fun exportToFile(
        context: Context,
        transactions: List<TransactionRecord>,
        format: ExportFormat,
        reportTitle: String
    ): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val stamp = System.currentTimeMillis()
        val outFile = File(exportDir, "FinTrack_Report_$stamp.${format.extension}")

        when (format) {
            ExportFormat.CSV -> outFile.writeBytes(buildCsvBytes(transactions))
            ExportFormat.XLSX -> outFile.writeBytes(buildXlsxBytes(transactions, reportTitle))
            ExportFormat.PDF -> writePdf(outFile, transactions, reportTitle)
        }
        return outFile
    }

    fun buildCsvBytes(transactions: List<TransactionRecord>): ByteArray {
        val sb = StringBuilder()
        sb.append('\uFEFF') // UTF-8 BOM for Microsoft Excel ₹ compatibility
        sb.appendLine(
            listOf(
                "Transaction ID", "Date (IST)", "Time (IST)", "Amount (INR)", "Type",
                "Counterparty", "Mobile", "UPI ID", "Bank Account", "Masked Account",
                "Payment App", "Payment Method", "UTR / UPI Ref", "Status",
                "Category", "Source", "Description", "Notes"
            ).joinToString(",") { csvEscape(it) }
        )

        for (tx in transactions) {
            sb.appendLine(
                listOf(
                    tx.id,
                    tx.dateIst,
                    tx.timeIst,
                    tx.amount.toString(),
                    tx.type,
                    tx.counterpartyName,
                    tx.recipientMobile,
                    tx.upiId,
                    tx.bankAccountName,
                    tx.maskedAccountIdentifier,
                    tx.paymentApp,
                    tx.paymentMethod,
                    tx.utrOrUpiRef,
                    tx.status,
                    tx.category,
                    tx.source,
                    tx.description,
                    tx.notes
                ).joinToString(",") { csvEscape(it) }
            )
        }
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    fun buildXlsxBytes(transactions: List<TransactionRecord>, sheetTitle: String): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            writeZipEntry(
                zos,
                "[Content_Types].xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>
  <Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>
  <Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>
</Types>"""
            )
            writeZipEntry(
                zos,
                "_rels/.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
</Relationships>"""
            )
            writeZipEntry(
                zos,
                "xl/_rels/workbook.xml.rels",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>
  <Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
</Relationships>"""
            )
            writeZipEntry(
                zos,
                "xl/workbook.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
  <sheets>
    <sheet name="${xmlEscape(sheetTitle.take(28).ifBlank { "Transactions" })}" sheetId="1" r:id="rId1"/>
  </sheets>
</workbook>"""
            )
            writeZipEntry(
                zos,
                "xl/styles.xml",
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts>
  <fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills>
  <borders count="1"><border><left/><right/><top/><bottom/><diagonal/></border></borders>
  <cellStyleXfs count="1"><xf numFmtId="0" fontId="0" fillId="0" borderId="0"/></cellStyleXfs>
  <cellXfs count="2">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="0" borderId="0" xfId="0" applyFont="1"/>
  </cellXfs>
</styleSheet>"""
            )

            val sheetSb = StringBuilder()
            sheetSb.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
            sheetSb.append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")

            val headers = listOf(
                "Date (IST)", "Time (IST)", "Counterparty", "Type", "Amount (INR)",
                "Category", "Payment App", "Method", "Bank Account", "Masked ID",
                "UPI ID", "Mobile", "UTR / Ref", "Status", "Source", "Notes"
            )
            sheetSb.append("""<row r="1">""")
            headers.forEachIndexed { colIdx, h ->
                sheetSb.append(inlineCell(colRef(colIdx, 1), h, style = 1))
            }
            sheetSb.append("</row>")

            transactions.forEachIndexed { idx, tx ->
                val rowNum = idx + 2
                sheetSb.append("""<row r="$rowNum">""")
                val cells = listOf<Any>(
                    tx.dateIst,
                    tx.timeIst,
                    tx.counterpartyName,
                    tx.type,
                    tx.amount,
                    tx.category,
                    tx.paymentApp,
                    tx.paymentMethod,
                    tx.bankAccountName,
                    tx.maskedAccountIdentifier,
                    tx.upiId,
                    tx.recipientMobile,
                    tx.utrOrUpiRef,
                    tx.status,
                    tx.source,
                    tx.notes
                )
                cells.forEachIndexed { colIdx, value ->
                    val ref = colRef(colIdx, rowNum)
                    if (value is Number) {
                        sheetSb.append("""<c r="$ref"><v>$value</v></c>""")
                    } else {
                        sheetSb.append(inlineCell(ref, value.toString(), style = 0))
                    }
                }
                sheetSb.append("</row>")
            }

            sheetSb.append("</sheetData></worksheet>")
            writeZipEntry(zos, "xl/worksheets/sheet1.xml", sheetSb.toString())
        }
        return out.toByteArray()
    }

    private fun writePdf(file: File, transactions: List<TransactionRecord>, title: String) {
        val doc = PdfDocument()
        val pageWidth = 595
        val pageHeight = 842
        val margin = 32f

        val headerPaint = Paint().apply {
            color = Color.rgb(13, 59, 54)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subPaint = Paint().apply {
            color = Color.rgb(75, 85, 99)
            textSize = 10f
            isAntiAlias = true
        }
        val rowPaint = Paint().apply {
            color = Color.rgb(17, 24, 39)
            textSize = 9f
            isAntiAlias = true
        }
        val debitPaint = Paint(rowPaint).apply { color = Color.rgb(185, 28, 28) }
        val creditPaint = Paint(rowPaint).apply { color = Color.rgb(5, 150, 105) }
        val linePaint = Paint().apply {
            color = Color.rgb(229, 231, 235)
            strokeWidth = 1f
        }

        val totalCredit = transactions.filter { it.type == TransactionType.CREDIT.name }.sumOf { it.amount }
        val totalDebit = transactions.filter { it.type == TransactionType.DEBIT.name }.sumOf { it.amount }

        var pageNum = 1
        var page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
        var canvas = page.canvas
        var y = 44f

        fun drawHeader() {
            canvas.drawText("FinTrack – $title", margin, y, headerPaint)
            y += 18f
            canvas.drawText(
                "Generated: ${IndianCurrencyAndTime.formatReadableDateTimeIst(System.currentTimeMillis())} | Records: ${transactions.size}",
                margin,
                y,
                subPaint
            )
            y += 14f
            canvas.drawText(
                "Total Income: ${IndianCurrencyAndTime.formatInr(totalCredit)}   |   Total Expense: ${IndianCurrencyAndTime.formatInr(totalDebit)}",
                margin,
                y,
                subPaint
            )
            y += 12f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 16f
            canvas.drawText("DATE (IST)", margin, y, subPaint)
            canvas.drawText("COUNTERPARTY / DESC", margin + 72f, y, subPaint)
            canvas.drawText("CATEGORY", margin + 275f, y, subPaint)
            canvas.drawText("METHOD", margin + 375f, y, subPaint)
            canvas.drawText("AMOUNT", pageWidth - margin - 65f, y, subPaint)
            y += 8f
            canvas.drawLine(margin, y, pageWidth - margin, y, linePaint)
            y += 14f
        }

        drawHeader()

        for (tx in transactions) {
            if (y > pageHeight - 48f) {
                doc.finishPage(page)
                pageNum++
                page = doc.startPage(PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create())
                canvas = page.canvas
                y = 44f
                drawHeader()
            }
            val isCredit = tx.type == TransactionType.CREDIT.name
            val amtText = IndianCurrencyAndTime.formatInr(if (isCredit) tx.amount else -tx.amount, showSign = true)
            canvas.drawText(tx.dateIst, margin, y, rowPaint)
            canvas.drawText(tx.counterpartyName.ifBlank { tx.description }.take(34), margin + 72f, y, rowPaint)
            canvas.drawText(tx.category.take(16), margin + 275f, y, rowPaint)
            canvas.drawText(tx.paymentMethod.take(12), margin + 375f, y, rowPaint)
            canvas.drawText(amtText, pageWidth - margin - 65f, y, if (isCredit) creditPaint else debitPaint)
            y += 18f
        }

        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun writeZipEntry(zos: ZipOutputStream, name: String, content: String) {
        zos.putNextEntry(ZipEntry(name))
        zos.write(content.toByteArray(Charsets.UTF_8))
        zos.closeEntry()
    }

    private fun inlineCell(ref: String, text: String, style: Int): String =
        """<c r="$ref" t="inlineStr" s="$style"><is><t>${xmlEscape(text)}</t></is></c>"""

    private fun colRef(colIndex: Int, rowNum: Int): String {
        val letter = ('A' + colIndex)
        return "$letter$rowNum"
    }

    private fun csvEscape(value: String): String {
        val needsQuote = value.contains(',') || value.contains('"') || value.contains('\n')
        return if (needsQuote) "\"${value.replace("\"", "\"\"")}\"" else value
    }

    private fun xmlEscape(value: String): String = value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
}
