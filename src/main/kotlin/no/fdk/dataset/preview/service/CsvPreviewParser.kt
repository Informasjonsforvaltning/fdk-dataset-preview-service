package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.Preview
import no.fdk.dataset.preview.model.Table
import no.fdk.dataset.preview.model.TableHeader
import no.fdk.dataset.preview.model.TableRow
import no.fdk.dataset.preview.util.ContentSanitizer
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.io.input.BOMInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.Arrays

@Component
class CsvPreviewParser(
    private val limits: PreviewLimits,
) {
    private val logger: Logger = LoggerFactory.getLogger(CsvPreviewParser::class.java)

    fun parse(
        rows: Int?,
        inputStream: InputStream,
        secondInputStream: InputStream?,
        charset: Charset?,
    ): Preview {
        logDebug("Parsing CSV")

        val delimiter = detectDelimiter(inputStream)
        logDebug("Detected delimiter $delimiter")

        CSVFormat.DEFAULT
            .builder()
            .setDelimiter(delimiter)
            .setEscape('\\')
            .get()
            .parse(
                InputStreamReader(
                    BOMInputStream
                        .Builder()
                        .setInputStream(secondInputStream ?: inputStream)
                        .get(),
                    charset ?: Charset.forName("UTF-8"),
                ),
            ).use { return Preview(table = parseCSVToTable(it, limits.getMaxNumberOfRows(rows)), plain = null) }
    }

    private fun detectDelimiter(inputStream: InputStream): Char {
        try {
            val length = 1024
            if (inputStream.markSupported()) {
                inputStream.mark(length)
            }

            val firstLine = String(readFromStream(inputStream, length))

            return Arrays
                .stream(PreviewLimits.DELIMITERS)
                .filter { delimiter -> firstLine.contains(delimiter.toString()) }
                .findFirst()
                .orElse(PreviewLimits.NO_DELIMITER)
        } finally {
            if (inputStream.markSupported()) {
                inputStream.reset()
            } else {
                inputStream.close()
            }
        }
    }

    private fun readFromStream(
        stream: InputStream,
        length: Int,
    ): ByteArray {
        val bytes = ByteArray(length)
        var totalRead = 0
        var lastRead = stream.read(bytes)
        while (lastRead != -1) {
            totalRead += lastRead
            if (totalRead == bytes.size) {
                return bytes
            }
            lastRead = stream.read(bytes, totalRead, bytes.size - totalRead)
        }
        val shorter = ByteArray(totalRead)
        System.arraycopy(bytes, 0, shorter, 0, totalRead)
        return shorter
    }

    private fun parseCSVToTable(
        parser: CSVParser,
        maxNumberOfRows: Int,
    ): Table {
        var tableHeader = TableHeader(emptyList())
        val tableRows = arrayListOf<TableRow>()
        val it = parser.iterator()
        if (it.hasNext()) {
            tableHeader =
                TableHeader(
                    it.next().map { headerValue ->
                        ContentSanitizer.sanitizeCellContent(headerValue)
                    },
                ).beautified()
        }

        while (it.hasNext() && (maxNumberOfRows <= 0 || tableRows.size < maxNumberOfRows)) {
            tableRows.add(
                TableRow(
                    it.next().map { cellValue ->
                        ContentSanitizer.sanitizeCellContent(cellValue)
                    },
                ),
            )
        }

        return Table(tableHeader, tableRows)
    }

    private fun logDebug(message: String) {
        if (logger.isDebugEnabled) {
            logger.debug(message)
        }
    }
}
