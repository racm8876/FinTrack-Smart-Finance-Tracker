package com.example.domain

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.data.model.PaymentApp
import com.example.data.model.PaymentMethod
import com.example.data.model.TransactionRecord
import com.example.data.model.TransactionSource
import com.example.data.model.TransactionStatus
import com.example.data.model.TransactionType
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.Inflater
import java.util.zip.ZipInputStream

data class StatementImportResult(
    val transactions: List<TransactionRecord>,
    val skippedRows: Int,
    val diagnosticMessage: String
)

object StatementParser {

    fun importFromUri(
        context: Context,
        uri: Uri,
        fileName: String,
        bankAccountName: String,
        maskedIdentifier: String,
        userId: String
    ): StatementImportResult {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return StatementImportResult(emptyList(), 0, "Unable to open selected document.")

        val lowerName = fileName.lowercase(Locale.ENGLISH)
        return when {
            lowerName.endsWith(".csv") || lowerName.endsWith(".txt") ->
                parseCsv(bytes.decodeToString(), bankAccountName, maskedIdentifier, userId)
            lowerName.endsWith(".xlsx") ->
                parseXlsx(bytes, bankAccountName, maskedIdentifier, userId)
            lowerName.endsWith(".pdf") ->
                parsePdf(bytes, bankAccountName, maskedIdentifier, userId)
            else -> {
                // Sniff magic header
                if (bytes.size >= 4 && bytes[0] == 0x25.toByte() && bytes[1] == 0x50.toByte()) {
                    parsePdf(bytes, bankAccountName, maskedIdentifier, userId)
                } else if (bytes.size >= 2 && bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte()) {
                    parseXlsx(bytes, bankAccountName, maskedIdentifier, userId)
                } else {
                    parseCsv(bytes.decodeToString(), bankAccountName, maskedIdentifier, userId)
                }
            }
        }
    }

    fun parseCsv(
        csvContent: String,
        bankAccountName: String,
        maskedIdentifier: String,
        userId: String
    ): StatementImportResult {
        val lines = csvContent.lineSequence()
            .map { it.trimStart('\uFEFF').trim() }
            .filter { it.isNotBlank() }
            .toList()
        if (lines.isEmpty()) {
            return StatementImportResult(emptyList(), 0, "CSV file is empty.")
        }

        // Locate header row (Indian bank CSVs often have 2-10 metadata rows before the header)
        var headerIdx = -1
        var headers = emptyList<String>()
        for (i in lines.indices) {
            val cols = splitCsvLine(lines[i]).map { it.trim().lowercase(Locale.ENGLISH) }
            val hasDate = cols.any { it.contains("date") }
            val hasAmountOrDebit = cols.any {
                it.contains("amount") || it.contains("debit") || it.contains("withdrawal") || it.contains("credit") || it.contains("deposit")
            }
            if (hasDate && hasAmountOrDebit) {
                headerIdx = i
                headers = cols
                break
            }
        }

        if (headerIdx == -1) {
            return StatementImportResult(
                emptyList(),
                lines.size,
                "Could not locate Date + Amount/Debit/Credit column headers in CSV."
            )
        }

        val rows = lines.drop(headerIdx + 1).map { splitCsvLine(it) }
        return mapTabularRows(
            headers = headers,
            rows = rows,
            source = TransactionSource.STATEMENT_CSV,
            bankAccountName = bankAccountName,
            maskedIdentifier = maskedIdentifier,
            userId = userId
        )
    }

    fun parseXlsx(
        xlsxBytes: ByteArray,
        bankAccountName: String,
        maskedIdentifier: String,
        userId: String
    ): StatementImportResult {
        val sharedStrings = mutableListOf<String>()
        var sheetXml: ByteArray? = null

        ZipInputStream(ByteArrayInputStream(xlsxBytes)).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                when {
                    entry.name.equals("xl/sharedStrings.xml", ignoreCase = true) -> {
                        sharedStrings.addAll(parseSharedStringsXml(zis.readBytes()))
                    }
                    entry.name.equals("xl/worksheets/sheet1.xml", ignoreCase = true) ||
                        (sheetXml == null && entry.name.startsWith("xl/worksheets/sheet") && entry.name.endsWith(".xml")) -> {
                        sheetXml = zis.readBytes()
                    }
                }
                entry = zis.nextEntry
            }
        }

        val targetSheet = sheetXml
            ?: return StatementImportResult(emptyList(), 0, "Invalid Excel (.xlsx) workbook: no worksheet found.")

        val tableRows = parseWorksheetXml(targetSheet, sharedStrings)
        if (tableRows.isEmpty()) {
            return StatementImportResult(emptyList(), 0, "Excel worksheet contains no rows.")
        }

        var headerIdx = -1
        var headers = emptyList<String>()
        for (i in tableRows.indices) {
            val cols = tableRows[i].map { it.trim().lowercase(Locale.ENGLISH) }
            val hasDate = cols.any { it.contains("date") }
            val hasAmountOrDebit = cols.any {
                it.contains("amount") || it.contains("debit") || it.contains("withdrawal") || it.contains("credit") || it.contains("deposit")
            }
            if (hasDate && hasAmountOrDebit) {
                headerIdx = i
                headers = cols
                break
            }
        }
        if (headerIdx == -1) {
            return StatementImportResult(emptyList(), tableRows.size, "Could not locate statement header row in Excel sheet.")
        }

        return mapTabularRows(
            headers = headers,
            rows = tableRows.drop(headerIdx + 1),
            source = TransactionSource.STATEMENT_XLSX,
            bankAccountName = bankAccountName,
            maskedIdentifier = maskedIdentifier,
            userId = userId
        )
    }

    fun parsePdf(
        pdfBytes: ByteArray,
        bankAccountName: String,
        maskedIdentifier: String,
        userId: String
    ): StatementImportResult {
        val rawLatin = String(pdfBytes, Charsets.ISO_8859_1)
        if (rawLatin.contains("/Encrypt")) {
            return StatementImportResult(
                emptyList(),
                0,
                "PDF is password-protected/encrypted. Export an unlocked PDF or CSV/Excel statement from your bank."
            )
        }

        val extractedLines = mutableListOf<String>()
        val streamRegex = Regex("stream\\r?\\n(.*?)\\r?\\nendstream", setOf(RegexOption.DOT_MATCHES_ALL))
        for (match in streamRegex.findAll(rawLatin)) {
            val streamData = match.groupValues[1].toByteArray(Charsets.ISO_8859_1)
            val decoded = tryDecompressFlate(streamData) ?: String(streamData, Charsets.ISO_8859_1)
            if (decoded.contains("BT") || decoded.contains("Tj") || decoded.contains("TJ")) {
                extractedLines.addAll(extractPdfTextLines(decoded))
            }
        }

        if (extractedLines.isEmpty()) {
            return StatementImportResult(
                emptyList(),
                0,
                "No selectable text streams found in PDF (it may be a scanned image). Please use CSV or Excel (.xlsx) export."
            )
        }

        val parsed = mutableListOf<TransactionRecord>()
        var skipped = 0
        val linePattern = Regex("""(\d{1,2}[/-]\d{1,2}[/-]\d{2,4}|\d{1,2}\s+[A-Za-z]{3}\s+\d{2,4})\s+(.+?)\s+([0-9,]+\.\d{2})\s*(Cr|Dr|CR|DR)?$""")

        for (line in extractedLines) {
            val m = linePattern.find(line.trim())
            if (m != null) {
                val dateStr = m.groupValues[1]
                val narration = m.groupValues[2].trim()
                val amount = m.groupValues[3].replace(",", "").toDoubleOrNull()
                val flag = m.groupValues[4].uppercase(Locale.ENGLISH)
                if (amount != null && amount > 0.0) {
                    val ts = parseIndianDateToMillis(dateStr) ?: System.currentTimeMillis()
                    val type = if (flag == "CR" || narration.contains("CREDIT", true) || narration.contains("DEPOSIT", true)) {
                        TransactionType.CREDIT
                    } else {
                        TransactionType.DEBIT
                    }
                    parsed.add(
                        buildRecordFromNarration(
                            userId = userId,
                            tsMillis = ts,
                            amount = amount,
                            type = type,
                            narration = narration,
                            ref = "",
                            bankAccountName = bankAccountName,
                            maskedIdentifier = maskedIdentifier,
                            source = TransactionSource.STATEMENT_PDF,
                            rawLine = line
                        )
                    )
                } else {
                    skipped++
                }
            }
        }

        return if (parsed.isEmpty()) {
            StatementImportResult(
                emptyList(),
                extractedLines.size,
                "Extracted ${extractedLines.size} PDF lines, but layout did not match standard Indian statement rows. Try CSV or Excel (.xlsx)."
            )
        } else {
            StatementImportResult(
                parsed,
                skipped,
                "Parsed ${parsed.size} transactions from PDF statement."
            )
        }
    }

    private fun mapTabularRows(
        headers: List<String>,
        rows: List<List<String>>,
        source: TransactionSource,
        bankAccountName: String,
        maskedIdentifier: String,
        userId: String
    ): StatementImportResult {
        val dateIdx = headers.indexOfFirst { it.contains("date") && !it.contains("value") }.let {
            if (it >= 0) it else headers.indexOfFirst { h -> h.contains("date") }
        }
        val descIdx = headers.indexOfFirst {
            it.contains("narration") || it.contains("description") || it.contains("particular") || it.contains("remark") || it.contains("detail")
        }
        val refIdx = headers.indexOfFirst {
            it.contains("ref") || it.contains("utr") || it.contains("chq") || it.contains("cheque")
        }
        val debitIdx = headers.indexOfFirst {
            it.contains("debit") || it.contains("withdrawal") || it == "dr"
        }
        val creditIdx = headers.indexOfFirst {
            it.contains("credit") || it.contains("deposit") || it == "cr"
        }
        val amountIdx = headers.indexOfFirst { it.contains("amount") }
        val typeIdx = headers.indexOfFirst { it == "type" || it.contains("dr/cr") || it.contains("cr/dr") }

        val result = mutableListOf<TransactionRecord>()
        var skipped = 0

        for (row in rows) {
            if (row.all { it.isBlank() }) continue
            val dateRaw = row.getOrNull(dateIdx)?.trim().orEmpty()
            val ts = parseIndianDateToMillis(dateRaw)
            if (ts == null) {
                skipped++
                continue
            }

            val narration = row.getOrNull(descIdx)?.trim().orEmpty().ifBlank { "Bank Statement Entry" }
            val ref = row.getOrNull(refIdx)?.trim().orEmpty()

            var amount = 0.0
            var txType = TransactionType.DEBIT

            if (debitIdx >= 0 || creditIdx >= 0) {
                val debitVal = parseAmount(row.getOrNull(debitIdx))
                val creditVal = parseAmount(row.getOrNull(creditIdx))
                if (creditVal > 0.0) {
                    amount = creditVal
                    txType = TransactionType.CREDIT
                } else if (debitVal > 0.0) {
                    amount = debitVal
                    txType = TransactionType.DEBIT
                }
            }

            if (amount <= 0.0 && amountIdx >= 0) {
                val rawAmt = parseAmount(row.getOrNull(amountIdx))
                if (rawAmt != 0.0) {
                    val typeText = row.getOrNull(typeIdx)?.trim()?.lowercase(Locale.ENGLISH).orEmpty()
                    txType = when {
                        typeText.contains("cr") || typeText.contains("credit") || rawAmt < 0 && false -> TransactionType.CREDIT
                        typeText.contains("dr") || typeText.contains("debit") -> TransactionType.DEBIT
                        rawAmt < 0 -> TransactionType.DEBIT
                        else -> TransactionType.DEBIT
                    }
                    amount = kotlin.math.abs(rawAmt)
                }
            }

            if (amount <= 0.0) {
                skipped++
                continue
            }

            result.add(
                buildRecordFromNarration(
                    userId = userId,
                    tsMillis = ts,
                    amount = amount,
                    type = txType,
                    narration = narration,
                    ref = ref,
                    bankAccountName = bankAccountName,
                    maskedIdentifier = maskedIdentifier,
                    source = source,
                    rawLine = row.joinToString("|")
                )
            )
        }

        return StatementImportResult(
            transactions = result,
            skippedRows = skipped,
            diagnosticMessage = "Imported ${result.size} transactions (${skipped} non-transaction rows skipped)."
        )
    }

    private fun buildRecordFromNarration(
        userId: String,
        tsMillis: Long,
        amount: Double,
        type: TransactionType,
        narration: String,
        ref: String,
        bankAccountName: String,
        maskedIdentifier: String,
        source: TransactionSource,
        rawLine: String
    ): TransactionRecord {
        val lower = narration.lowercase(Locale.ENGLISH)
        val app = IndianTransactionParser.detectPaymentApp(null, lower)
        val method = IndianTransactionParser.detectPaymentMethod(lower, app)
        val vpa = Regex("""([a-zA-Z0-9._\-]{2,}@[a-zA-Z0-9]{2,})""").find(narration)?.groupValues?.getOrNull(1) ?: ""
        val utr = ref.ifBlank {
            Regex("""\b(\d{12})\b""").find(narration)?.groupValues?.getOrNull(1) ?: ""
        }
        val counterparty = if (vpa.isNotBlank()) {
            vpa.substringBefore("@").replace('.', ' ').replace('_', ' ')
                .split(' ').filter { it.isNotBlank() }
                .joinToString(" ") { p -> p.replaceFirstChar { it.uppercase() } }
        } else {
            narration.split('/', '-', '|').firstOrNull { it.trim().length >= 3 }?.trim()?.take(60)
                ?: narration.take(60)
        }
        val dateIst = IndianCurrencyAndTime.formatDateIst(tsMillis)
        val timeIst = IndianCurrencyAndTime.formatTimeIst(tsMillis)
        val category = IndianTransactionParser.inferCategory(counterparty, narration, type)
        val fp = IndianTransactionParser.computeDedupFingerprint(
            amount = amount,
            dateIst = dateIst,
            type = type.name,
            utrOrRef = utr,
            counterparty = counterparty,
            upiId = vpa
        )

        return TransactionRecord(
            userId = userId,
            transactionTimestampMillis = tsMillis,
            dateIst = dateIst,
            timeIst = timeIst,
            amount = amount,
            type = type.name,
            senderName = if (type == TransactionType.CREDIT) counterparty else "Self",
            receiverName = if (type == TransactionType.DEBIT) counterparty else "Self",
            counterpartyName = counterparty,
            recipientMobile = "Unavailable",
            upiId = vpa,
            bankAccountName = bankAccountName.ifBlank { IndianTransactionParser.detectBankName(null, lower) },
            maskedAccountIdentifier = maskedIdentifier,
            paymentApp = app.name,
            paymentMethod = method.name,
            referenceNumber = utr,
            utrOrUpiRef = utr,
            status = TransactionStatus.SUCCESS.name,
            category = category,
            description = narration.take(280),
            source = source.name,
            notes = "",
            dedupFingerprint = fp,
            rawSourceHash = IndianTransactionParser.sha256(rawLine)
        )
    }

    private fun parseAmount(raw: String?): Double {
        if (raw.isNullOrBlank()) return 0.0
        val cleaned = raw.replace("₹", "").replace("Rs.", "", true)
            .replace("INR", "", true).replace(",", "").trim()
        return cleaned.toDoubleOrNull() ?: 0.0
    }

    fun parseIndianDateToMillis(raw: String): Long? {
        val cleaned = raw.trim()
        if (cleaned.isBlank()) return null
        val patterns = listOf(
            "dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yy", "dd-MM-yy",
            "dd MMM yyyy", "dd-MMM-yyyy", "dd MMM yy", "dd-MMM-yy",
            "dd/MM/yyyy HH:mm:ss", "yyyy-MM-dd HH:mm:ss"
        )
        for (pattern in patterns) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.ENGLISH).apply {
                    isLenient = false
                    timeZone = IndianCurrencyAndTime.istTimeZone
                }
                val date = sdf.parse(cleaned)
                if (date != null) return date.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    private fun splitCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val sb = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                c == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        sb.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                c == ',' && !inQuotes -> {
                    result.add(sb.toString())
                    sb.clear()
                }
                else -> sb.append(c)
            }
            i++
        }
        result.add(sb.toString())
        return result
    }

    private fun parseSharedStringsXml(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var inSi = false
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        inSi = true
                        current.clear()
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inSi) current.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        list.add(current.toString())
                        inSi = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseWorksheetXml(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var currentRow = mutableListOf<String>()
        var currentCellType = ""
        var inValue = false
        val valueBuf = StringBuilder()
        var event = parser.eventType

        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> currentCellType = parser.getAttributeValue(null, "t") ?: ""
                    "v", "t" -> {
                        inValue = true
                        valueBuf.clear()
                    }
                }
                XmlPullParser.TEXT -> if (inValue) valueBuf.append(parser.text)
                XmlPullParser.END_TAG -> when (parser.name) {
                    "v", "t" -> {
                        if (inValue) {
                            val raw = valueBuf.toString()
                            val cellVal = if (currentCellType == "s") {
                                raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: raw
                            } else {
                                raw
                            }
                            currentRow.add(cellVal)
                            inValue = false
                        }
                    }
                    "row" -> if (currentRow.isNotEmpty()) rows.add(currentRow)
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun tryDecompressFlate(data: ByteArray): String? {
        return try {
            val inflater = Inflater()
            inflater.setInput(data)
            val out = ByteArrayOutputStream()
            val buf = ByteArray(2048)
            while (!inflater.finished()) {
                val count = inflater.inflate(buf)
                if (count == 0 && inflater.needsInput()) break
                out.write(buf, 0, count)
            }
            inflater.end()
            out.toString("ISO-8859-1")
        } catch (_: Exception) {
            null
        }
    }

    private fun extractPdfTextLines(contentStream: String): List<String> {
        val lines = mutableListOf<String>()
        val textOps = Regex("""\(([^()\\]*(?:\\.[^()\\]*)*)\)\s*Tj|\[(.*?)\]\s*TJ""")
        val lineBuf = StringBuilder()
        for (match in textOps.findAll(contentStream)) {
            val single = match.groupValues[1]
            val array = match.groupValues[2]
            if (single.isNotEmpty()) {
                lineBuf.append(unescapePdfString(single)).append(" ")
            } else if (array.isNotEmpty()) {
                val inner = Regex("""\(([^()\\]*(?:\\.[^()\\]*)*)\)""")
                for (m in inner.findAll(array)) {
                    lineBuf.append(unescapePdfString(m.groupValues[1]))
                }
                lineBuf.append(" ")
            }
            if (lineBuf.length > 160) {
                lines.add(lineBuf.toString().trim())
                lineBuf.clear()
            }
        }
        if (lineBuf.isNotBlank()) lines.add(lineBuf.toString().trim())
        return lines
    }

    private fun unescapePdfString(s: String): String =
        s.replace("\\(", "(").replace("\\)", ")").replace("\\\\", "\\")
}
