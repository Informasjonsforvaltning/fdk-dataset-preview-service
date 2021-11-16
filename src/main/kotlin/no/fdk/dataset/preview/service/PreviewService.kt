package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.*
import okhttp3.MediaType
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BOMInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*

private val LOGGER: Logger = LoggerFactory.getLogger(PreviewService::class.java)

@Service
class PreviewService(
    private val downloader: FileDownloader
) {
    companion object {
        val DELIMITERS = arrayOf(';', ',')
        const val NO_DELIMITER = '\u0000' //empty char
        const val DEFAULT_ROWS = 100
        const val MAX_ROWS = 1000
    }

    private fun detectDelimiter(inputStream: InputStream): Char {
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 500).use { reader ->
            val line = reader.readLine()
            return Arrays.stream(DELIMITERS)
                .filter { s -> line.contains(s.toString()) }
                .findFirst()
                .orElse(NO_DELIMITER)
        }
    }

    private fun getMaxNumberOfRows(rows: Int?): Int {
        if(rows == null) {
            return DEFAULT_ROWS
        }
        return if(rows > MAX_ROWS) MAX_ROWS else rows
    }

    fun readAndParseResource(resourceUrl: String, rows: Int?): Preview {
        logDebug("Read and parse resource $resourceUrl")

        try {
            val body = downloader.download(resourceUrl)
            if(isCsv(body.contentType())) {
                logDebug("Parsing CSV")
                val delimiter = detectDelimiter(body.byteStream())
                logDebug("Detected delimiter $delimiter")

                CSVFormat.DEFAULT.builder()
                    .setDelimiter(delimiter)
                    .build()
                    .parse( InputStreamReader( BOMInputStream(
                        downloader.download(resourceUrl).byteStream()), StandardCharsets.UTF_8))
                    .use { return  Preview(table = parseCSVToTable(it, getMaxNumberOfRows(rows)), plain = null) }
            } else if(isPlain(body.contentType())) {
                logDebug("Fetch plain content")
                val plain = Plain(IOUtils.toString(body.byteStream(), StandardCharsets.UTF_8), body.contentType().toString())
                return Preview(table=null, plain=plain)
            }

            throw PreviewException("Invalid content type ${body.contentType().toString()}")
        } catch(e: Exception) {
            logDebug("Unable to dowload resource $resourceUrl", e)
            throw PreviewException("Unable to dowload resource $resourceUrl")
        }
    }

    private fun parseCSVToTable(parser: CSVParser, maxNumberOfRows: Int): Table {
        var tableHeader = TableHeader(arrayListOf())
        val tableRows = arrayListOf<TableRow>()
        val it = parser.iterator()
        if(it.hasNext()) {
            tableHeader = TableHeader(it.next().toList())
            tableHeader.beautify()
        }

        while (it.hasNext() && (maxNumberOfRows <= 0 || tableRows.size < maxNumberOfRows)) {
            tableRows.add(TableRow(it.next().toList()))
        }

        return Table(tableHeader, tableRows)
    }

    private fun isCsv(mediaType: MediaType?): Boolean {
        if (mediaType == null) {
            return false
        }
        return mediaType.subtype.contains("csv") || mediaType.subtype.contains("vnd.ms-excel")
    }

    private fun isPlain(mediaType: MediaType?): Boolean {
        if (mediaType == null) {
            return false
        }
        return mediaType.subtype.contains("xml") || mediaType.subtype.contains("json")
    }

    private fun logDebug(message: String) {
        logDebug(message, null)
    }

    private fun logDebug(message: String, throwable: Throwable?) {
        if(LOGGER.isDebugEnabled) {
            LOGGER.debug(message, throwable)
        }
    }
}
