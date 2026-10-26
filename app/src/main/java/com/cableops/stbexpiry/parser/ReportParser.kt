package com.cableops.stbexpiry.parser

import android.content.Context
import android.net.Uri
import com.cableops.stbexpiry.data.model.ExpiryRecord
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * Parses an HTML-disguised XLS billing report exported from GTPL / CATV billing software.
 *
 * The file is a raw HTML table saved with a `.xls` extension. Jsoup is used to parse
 * the HTML stream and extract only the three required columns: NAME, STB_NUMBER, END_DATE.
 * All other columns (CUSTOMER_NBR, ADDRESS, MSO_AMOUNT, etc.) are silently discarded.
 *
 * Header matching is case-insensitive and trims surrounding whitespace, making the
 * parser tolerant of minor variations across software versions.
 */
object ReportParser {

    // Known column header aliases – extend this list if the billing software uses
    // different header names in future exports.
    private val NAME_HEADERS = setOf("NAME", "CUSTOMER NAME", "CUST_NAME", "SUBSCRIBER NAME")
    private val STB_HEADERS  = setOf("STB_NUMBER", "STB NUMBER", "STB NO", "STB_NO", "SET TOP BOX NO", "STBNO")
    private val DATE_HEADERS  = setOf("END_DATE", "END DATE", "EXPIRY DATE", "EXPIRY_DATE", "EXP DATE", "EXP_DATE")

    /**
     * Opens [uri] via the ContentResolver, parses the HTML table, and returns a list
     * of [ExpiryRecord] objects containing only name, STB number, and expiry date.
     *
     * @throws IllegalArgumentException if the file cannot be read or no recognised
     *         header columns are found.
     * @throws IllegalStateException if the header row cannot be located in the table.
     */
    fun parseReport(context: Context, uri: Uri): List<ExpiryRecord> {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Unable to open file stream for URI: $uri")

        val document = inputStream.use { stream ->
            Jsoup.parse(stream, "UTF-8", "")
        }

        // Find the first <table> in the document – billing exports usually contain only one.
        val table = document.selectFirst("table")
            ?: throw IllegalStateException("No <table> element found in the imported file.")

        val rows = table.select("tr")
        if (rows.isEmpty()) {
            throw IllegalStateException("The table in the imported file has no rows.")
        }

        // ── Locate header row ─────────────────────────────────────────────────────────
        // The header row may use <th> or <td> elements. We scan the first few rows to
        // find one that contains at least one recognised column.
        var nameIdx = -1
        var stbIdx  = -1
        var dateIdx = -1
        var dataStartRow = 0

        for ((rowIndex, row) in rows.withIndex()) {
            val cells = row.select("th, td")
            val headers = cells.map { it.text().trim().uppercase() }

            val tempNameIdx = headers.indexOfFirst { it in NAME_HEADERS }
            val tempStbIdx  = headers.indexOfFirst { it in STB_HEADERS }
            val tempDateIdx = headers.indexOfFirst { it in DATE_HEADERS }

            // Accept this row as the header if at least two required columns are identified.
            val foundCount = listOf(tempNameIdx, tempStbIdx, tempDateIdx).count { it >= 0 }
            if (foundCount >= 2) {
                nameIdx = tempNameIdx
                stbIdx  = tempStbIdx
                dateIdx = tempDateIdx
                dataStartRow = rowIndex + 1
                break
            }
        }

        if (nameIdx < 0 && stbIdx < 0 && dateIdx < 0) {
            throw IllegalStateException(
                "Could not find NAME, STB_NUMBER, or END_DATE columns in the imported file. " +
                "Please verify the file is a valid billing export."
            )
        }

        // ── Extract data rows ─────────────────────────────────────────────────────────
        val records = mutableListOf<ExpiryRecord>()

        for (rowIndex in dataStartRow until rows.size) {
            val cells = rows[rowIndex].select("td")

            val name    = cells.getTextOrNull(nameIdx)
            val stbNum  = cells.getTextOrNull(stbIdx)
            val endDate = cells.getTextOrNull(dateIdx)

            // Skip rows with any missing key field – they are likely subtotals or empty rows.
            if (name.isNullOrBlank() || stbNum.isNullOrBlank() || endDate.isNullOrBlank()) {
                continue
            }

            // Basic date format sanity check: must match DD/MM/YYYY (10 chars, slashes at 2 & 5).
            if (!isValidDateFormat(endDate)) {
                continue
            }

            records.add(
                ExpiryRecord(
                    name      = name.trim(),
                    stbNumber = stbNum.trim(),
                    endDate   = endDate.trim()
                )
            )
        }

        return records
    }

    // ── Private helpers ───────────────────────────────────────────────────────────────

    private fun List<Element>.getTextOrNull(index: Int): String? {
        return if (index >= 0 && index < size) this[index].text().trim().takeIf { it.isNotEmpty() }
        else null
    }

    /**
     * Validates that [date] roughly follows the "DD/MM/YYYY" pattern.
     * Full parsing is intentionally avoided here to stay lenient – actual
     * date arithmetic is handled in the ViewModel using [java.time.LocalDate].
     */
    private fun isValidDateFormat(date: String): Boolean {
        if (date.length != 10) return false
        if (date[2] != '/' || date[5] != '/') return false
        return date.replace("/", "").all { it.isDigit() }
    }
}
