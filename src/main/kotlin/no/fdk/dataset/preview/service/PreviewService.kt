package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.*
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BOMInputStream
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.mime.MediaType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.*
import java.util.zip.ZipInputStream


private val LOGGER: Logger = LoggerFactory.getLogger(PreviewService::class.java)

// Max size in bytes (10MB)
private const val MAX_SIZE_IN_BYTES: Int = 10000000

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

    private fun readFromStream(stream: InputStream, length: Int): ByteArray {
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

    private fun detectDelimiter(inputStream: InputStream): Char {
        try {
            val length = 1024
            if(inputStream.markSupported()) {
                inputStream.mark(length)
            }

            val firstLine = String(readFromStream(inputStream, length))

            return Arrays.stream(DELIMITERS)
                .filter { s -> firstLine.contains(s.toString()) }
                .findFirst()
                .orElse(NO_DELIMITER)
        } finally {
            if(inputStream.markSupported()) {
                inputStream.reset()
            } else {
                inputStream.close()
            }
        }
    }

    private fun getMaxNumberOfRows(rows: Int?): Int =
        when {
            rows == null -> DEFAULT_ROWS
            rows > MAX_ROWS -> MAX_ROWS
            else -> rows
        }

    fun readAndParseResource(resourceUrl: String, rows: Int?): Preview {
        logDebug("Read and parse resource $resourceUrl")

        try {
            val body = downloader.download(resourceUrl)
            body.byteStream().use { inputStream ->
                return when {
                    isZip(body.contentType().toString()) -> zipPreview(
                        rows,
                        inputStream)
                    isXlsx(body.contentType().toString()) || isXlsxFile(resourceUrl) -> xlsxPreview(
                        rows,
                        inputStream)
                    isCsv(body.contentType().toString()) || isCsvFile(resourceUrl) -> {
                        val secondStream = if (!inputStream.markSupported())
                            downloader.download(resourceUrl).byteStream()
                        else
                            null
                        secondStream?.use {
                            csvPreview(
                                rows,
                                inputStream,
                                it,
                                body.contentType()?.charset()
                            )
                        } ?: csvPreview(
                            rows,
                            inputStream,
                            null,
                            body.contentType()?.charset()
                        )
                    }
                    isPlain(body.contentType().toString()) || isPlainFile(resourceUrl) -> plainPreview(
                        body.byteStream().use { it },
                        body.contentType().toString(),
                        body.contentType()?.charset())
                    else -> throw PreviewException("Invalid content type ${body.contentType().toString()}")
                }
            }
        } catch(e: DownloadException) {
            logDebug("Unable to download resource $resourceUrl", e)
            throw PreviewException("Unable to download resource $resourceUrl")
        }
    }

    private fun zipPreview(rows: Int?, inputStream: InputStream): Preview {
        logDebug("Extracting zip")

        val zis = ZipInputStream(inputStream)

        try {
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                if (!zipEntry.isDirectory && isSupportedFile(zipEntry.name)) {
                    if(zipEntry.size > MAX_SIZE_IN_BYTES) {
                        throw PreviewException("File ${zipEntry.name} is too large")
                    }

                    if (isXlsxFile(zipEntry.name)) {
                        return xlsxPreview(rows, zis)
                    }
                    if (isCsvFile(zipEntry.name)) {
                        val bis = zis.toByteArrayInputStream()
                        return csvPreview(
                            rows,
                            bis,
                            null,
                            bis.getMediaType().getCharset()
                        )
                    }
                    if (isPlainFile(zipEntry.name)) {
                        val bis = zis.toByteArrayInputStream()
                        val mediaType = bis.getMediaType()
                        return plainPreview(
                            bis,
                            mediaType.toString(),
                            mediaType.getCharset()
                        )
                    }
                }
                zipEntry = zis.nextEntry
            }
        } finally {
            try {
                zis.closeEntry()
            } catch (ex: IOException) {
                // Ignore if reason is already closed zipEntry-stream
                if (ex.message != "Stream closed") throw ex
            }
            zis.close()
        }

        throw PreviewException("Zip file does not contain valid content")
    }

    private fun ZipInputStream.toByteArrayInputStream(): ByteArrayInputStream {
        val buffer = ByteArray(1024)
        val bos = ByteArrayOutputStream()
        var len: Int
        while (read(buffer).also { len = it } > 0) {
            bos.write(buffer, 0, len)
        }
        bos.close()

        return ByteArrayInputStream(bos.toByteArray())
    }

    private fun InputStream.getMediaType(): MediaType =
        Tika().detector.detect(this, Metadata())

    private fun MediaType.getCharset(): Charset =
        Charset.forName(parameters.getOrDefault("charset", "UTF-8"))

    private fun xlsxPreview(rows: Int?, inputStream: InputStream): Preview {
        logDebug("Parsing Excel")

        val tableRows = arrayListOf<TableRow>()

        val workbook: Workbook = XSSFWorkbook(inputStream)
        val formatter = DataFormatter()
        val sheet = workbook.getSheetAt(0)

        // Initialize a variable to track the last cell number in the sheet
        var lastCellNum = 0

        // Iterate through each row in the sheet
        sheet.forEach { row ->
            // Map the row's cells to a list of formatted cell values and create a TableRow object
            val tableRow = TableRow(row.map {
                formatter.formatCellValue(it) // Format the cell value for consistent representation
            })

            // Add the created TableRow to the list of table rows
            tableRows.add(tableRow)

            // Update the lastCellNum based on the current row's last cell number
            lastCellNum = when {
                row.lastCellNum <= lastCellNum -> lastCellNum // Keep the current lastCellNum if it's greater
                formatter.formatCellValue(row.last()).isNotEmpty() -> row.physicalNumberOfCells // Update if the last cell is not empty
                else -> lastCellNum // Otherwise, retain the current lastCellNum
            }
        }

        // Find the index of the header row based on the number of columns and non-empty last column
        val headerIndex = tableRows.indexOfFirst {
            it.columns.size == lastCellNum && it.columns[lastCellNum - 1].isNotEmpty()
        }

        // Determine the starting index of the header row
        val startHeaderIndex = if (headerIndex == -1) 0 else headerIndex

        // Calculate the ending index of the header row (exclusive)
        val endHeaderIndex = startHeaderIndex + 1

        // Calculate the ending index for the rows to be included in the table
        val endRowsIndex =
            if (endHeaderIndex + getMaxNumberOfRows(rows) <= tableRows.size - 1)
                endHeaderIndex + getMaxNumberOfRows(rows) // Limit rows to the maximum allowed
            else
                tableRows.size - 1 // Include all rows if the limit exceeds the total rows

        // Extract the header row from the table rows and create a TableHeader object
        val header = TableHeader(tableRows.subList(startHeaderIndex, endHeaderIndex)[0].columns)

        // Beautify the header for better readability or formatting
        header.beautify()

        val table = Table(header, tableRows.subList(endHeaderIndex, endRowsIndex))
        return Preview(table=table, plain = null)
    }

    private fun csvPreview(rows: Int?, inputStream: InputStream, secondInputStream: InputStream?, charset: Charset?): Preview {
        logDebug("Parsing CSV")

        val delimiter = detectDelimiter(inputStream)
        logDebug("Detected delimiter $delimiter")

        CSVFormat.DEFAULT.builder()
            .setDelimiter(delimiter)
            .setEscape('\\')
            .build()
            .parse( InputStreamReader( BOMInputStream(secondInputStream ?: inputStream),
                charset ?: Charset.forName("UTF-8")))
            .use { return  Preview(table = parseCSVToTable(it, getMaxNumberOfRows(rows)), plain = null) }
    }

    private fun plainPreview(inputStream: InputStream, mediaType: String?, charset: Charset?): Preview {
        logDebug("Fetch plain content")
        val plain = Plain(IOUtils.toString(inputStream,
            charset ?: Charset.forName("UTF-8")),
            mediaType ?: "")
        return Preview(table=null, plain=plain)
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

    private fun isZip(mediaType: String?): Boolean =
        when (mediaType) {
            null -> false
            "application/zip" -> true
            else -> false
        }

    private fun isXlsx(mediaType: String?): Boolean =
        when {
            mediaType == null -> false
            """application/vnd\.openxmlformats-officedocument\.spreadsheetml\.sheet"""
                .toRegex().containsMatchIn(mediaType) -> true
            else -> false
        }

    private fun isCsv(mediaType: String?): Boolean =
        when {
            mediaType == null -> false
            """\+?csv""".toRegex().containsMatchIn(mediaType) -> true
            """\+?vnd\.ms-excel""".toRegex().containsMatchIn(mediaType) -> true
            else -> false
        }

    private fun isPlain(mediaType: String?): Boolean =
        when {
            mediaType == null -> false
            """\+?xml""".toRegex().containsMatchIn(mediaType) -> true
            """\+?json""".toRegex().containsMatchIn(mediaType) -> true
            else -> false
        }

    private fun isSupportedFile(fileName: String): Boolean =
        when {
            isXlsxFile(fileName) -> true
            isCsvFile(fileName) -> true
            isPlainFile(fileName) -> true
            else -> false
        }

    private fun isXlsxFile(fileName: String): Boolean =
        when {
            fileName.endsWith(".xlsx") -> true
            else -> false
        }

    private fun isCsvFile(fileName: String): Boolean =
        when {
            fileName.endsWith(".csv") -> true
            fileName.endsWith(".xls") -> true
            else -> false
        }

    private fun isPlainFile(fileName: String): Boolean =
        when {
            fileName.endsWith(".xml") -> true
            fileName.endsWith(".json") -> true
            else -> false
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
