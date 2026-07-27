package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.*
import no.fdk.dataset.preview.util.ContentSanitizer
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.io.IOUtils
import org.apache.commons.io.input.BOMInputStream
import org.apache.poi.openxml4j.opc.OPCPackage
import org.apache.poi.openxml4j.opc.PackageAccess
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.util.CellReference
import org.apache.poi.util.XMLHelper
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable
import org.apache.poi.xssf.eventusermodel.XSSFReader
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler
import org.apache.poi.xssf.model.StylesTable
import org.apache.poi.xssf.usermodel.XSSFComment
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.mime.MediaType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.util.*
import java.util.zip.ZipInputStream
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream


private val LOGGER: Logger = LoggerFactory.getLogger(PreviewService::class.java)

@Service
class PreviewService(
    private val downloader: FileDownloader
) {
    
    @Value("\${application.security.maxFileSize:10485760}")
    private val maxFileSizeBytes: Long = 10485760
    
    @Value("\${application.security.maxProcessingTime:30}")
    private val maxProcessingTimeSeconds: Long = 30L
    companion object {
        val DELIMITERS = arrayOf(';', ',')
        const val NO_DELIMITER = '\u0000' //empty char
        const val DEFAULT_ROWS = 100
        const val MAX_ROWS = 1000
        const val MAX_COLUMNS = 100
    }

    private class SheetParseLimitReached : SAXException("Sheet parse row limit reached")

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
            return downloader.download(resourceUrl, { body ->
                val contentLength = body.contentLength()
                if (contentLength > maxFileSizeBytes) {
                    throw PreviewException("File is too large to process")
                }

                body.byteStream().use { inputStream ->
                    when {
                        isZip(body.contentType().toString()) -> zipPreview(
                            rows,
                            inputStream)
                        isXlsx(body.contentType().toString()) || isXlsxFile(resourceUrl) -> xlsxPreview(
                            rows,
                            inputStream)
                        isCsv(body.contentType().toString()) || isCsvFile(resourceUrl) ->
                            if (!inputStream.markSupported())
                                downloader.download(resourceUrl, { secondBody ->
                                    secondBody.byteStream().use { secondInputStream ->
                                        csvPreview(
                                            rows,
                                            inputStream,
                                            secondInputStream,
                                            body.contentType()?.charset()
                                        )
                                    }
                                })
                            else
                                csvPreview(
                                    rows,
                                    inputStream,
                                    null,
                                    body.contentType()?.charset()
                                )
                        isPlain(body.contentType().toString()) || isPlainFile(resourceUrl) -> plainPreview(
                            inputStream,
                            body.contentType().toString(),
                            body.contentType()?.charset())
                        else -> throw PreviewException("Unsupported file format")
                    }
                }
            })
        } catch(e: DownloadException) {
            logDebug("Unable to download resource $resourceUrl", e)
            throw PreviewException("Failed to download file")
        }
    }

    private fun zipPreview(rows: Int?, inputStream: InputStream): Preview {
        logDebug("Extracting zip")

        val zis = ZipInputStream(inputStream)

        try {
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                if (!zipEntry.isDirectory && isSupportedFile(zipEntry.name)) {
                    if (zipEntry.size > maxFileSizeBytes) {
                        throw PreviewException("File is too large to process")
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

        throw PreviewException("Invalid zip file content")
    }

    private fun ZipInputStream.toByteArrayInputStream(): ByteArrayInputStream {
        val buffer = ByteArray(8192)
        val bos = ByteArrayOutputStream()
        var total = 0L
        var len: Int
        while (read(buffer).also { len = it } > 0) {
            total += len
            if (total > maxFileSizeBytes) {
                throw PreviewException("File is too large to process")
            }
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

        val maxRowsToProcess = getMaxNumberOfRows(rows) * 2
        val startTime = System.currentTimeMillis()
        val tableRows = arrayListOf<TableRow>()
        var lastCellNum = 0

        val tempFile = inputStream.copyToTempFile(maxFileSizeBytes)
        try {
            OPCPackage.open(tempFile.toFile(), PackageAccess.READ).use { pkg ->
                val sharedStrings = ReadOnlySharedStringsTable(pkg)
                val reader = XSSFReader(pkg)
                val styles: StylesTable? = try {
                    reader.stylesTable
                } catch (_: Exception) {
                    null
                }
                val sheets = reader.sheetsData
                if (!sheets.hasNext()) {
                    throw PreviewException("Invalid Excel file content")
                }

                sheets.next().use { sheetStream ->
                    val sheetHandler = object : XSSFSheetXMLHandler.SheetContentsHandler {
                        private var currentRow = arrayListOf<String>()

                        override fun startRow(rowNum: Int) {
                            if (System.currentTimeMillis() - startTime > maxProcessingTimeSeconds * 1000) {
                                throw PreviewException("File processing timeout exceeded")
                            }
                            if (tableRows.size >= maxRowsToProcess) {
                                logDebug("Excel processing limited to $maxRowsToProcess rows for security")
                                throw SheetParseLimitReached()
                            }
                            currentRow = arrayListOf()
                        }

                        override fun endRow(rowNum: Int) {
                            if (currentRow.size > MAX_COLUMNS) {
                                currentRow = ArrayList(currentRow.subList(0, MAX_COLUMNS))
                            }

                            lastCellNum = when {
                                currentRow.size <= lastCellNum -> lastCellNum
                                currentRow.lastOrNull()?.isNotEmpty() == true -> currentRow.size
                                else -> lastCellNum
                            }

                            tableRows.add(TableRow(currentRow.toList()))
                        }

                        override fun cell(cellReference: String?, formattedValue: String?, comment: XSSFComment?) {
                            if (cellReference == null) return
                            val col = CellReference(cellReference).col.toInt()
                            if (col < 0 || col >= MAX_COLUMNS) return

                            while (currentRow.size <= col) {
                                currentRow.add("")
                            }
                            currentRow[col] = ContentSanitizer.sanitizeCellContent(formattedValue ?: "")
                        }
                    }

                    val formatter = DataFormatter()
                    val xmlHandler = XSSFSheetXMLHandler(
                        styles,
                        sharedStrings,
                        sheetHandler,
                        formatter,
                        false
                    )
                    val xmlReader = XMLHelper.newXMLReader()
                    xmlReader.contentHandler = xmlHandler
                    try {
                        xmlReader.parse(InputSource(sheetStream))
                    } catch (e: Exception) {
                        when (val root = e.unwrapCause()) {
                            is SheetParseLimitReached -> {
                                // Expected once we have enough preview rows
                            }
                            is PreviewException -> throw root
                            else -> throw e
                        }
                    }
                }
            }
        } catch (e: PreviewException) {
            throw e
        } catch (e: Exception) {
            when (val root = e.unwrapCause()) {
                is PreviewException -> throw root
                else -> {
                    logDebug("Failed to parse Excel", e)
                    throw PreviewException("Failed to parse Excel file")
                }
            }
        } finally {
            tempFile.deleteIfExists()
        }

        if (tableRows.isEmpty()) {
            throw PreviewException("Invalid Excel file content")
        }

        val headerIndex = if (lastCellNum > 0) {
            tableRows.indexOfFirst {
                it.columns.size == lastCellNum && it.columns[lastCellNum - 1].isNotEmpty()
            }
        } else {
            -1
        }

        val startHeaderIndex = if (headerIndex == -1) 0 else headerIndex
        val endHeaderIndex = startHeaderIndex + 1
        val endRowsIndex =
            if (endHeaderIndex + getMaxNumberOfRows(rows) <= tableRows.size - 1)
                endHeaderIndex + getMaxNumberOfRows(rows)
            else
                tableRows.size - 1

        val header = TableHeader(tableRows.subList(startHeaderIndex, endHeaderIndex)[0].columns)
        header.beautify()

        val table = Table(header, tableRows.subList(endHeaderIndex, endRowsIndex))
        return Preview(table = table, plain = null)
    }

    private fun InputStream.copyToTempFile(maxBytes: Long): Path {
        val tempFile = Files.createTempFile("fdk-preview-", ".xlsx")
        try {
            tempFile.outputStream().use { out ->
                val buffer = ByteArray(8192)
                var total = 0L
                var read: Int
                while (read(buffer).also { read = it } != -1) {
                    total += read
                    if (total > maxBytes) {
                        throw PreviewException("File is too large to process")
                    }
                    out.write(buffer, 0, read)
                }
            }
            return tempFile
        } catch (e: Exception) {
            tempFile.deleteIfExists()
            throw e
        }
    }

    private fun Throwable.unwrapCause(): Throwable {
        var current: Throwable = this
        while (current.cause != null && current.cause !== current) {
            when (current) {
                is PreviewException, is SheetParseLimitReached -> return current
                else -> current = current.cause!!
            }
        }
        return current
    }

    private fun csvPreview(rows: Int?, inputStream: InputStream, secondInputStream: InputStream?, charset: Charset?): Preview {
        logDebug("Parsing CSV")

        val delimiter = detectDelimiter(inputStream)
        logDebug("Detected delimiter $delimiter")

        CSVFormat.DEFAULT.builder()
            .setDelimiter(delimiter)
            .setEscape('\\')
            .get()
            .parse( InputStreamReader( BOMInputStream.Builder()
                .setInputStream(secondInputStream ?: inputStream)
                .get(),
                charset ?: Charset.forName("UTF-8")))
            .use { return  Preview(table = parseCSVToTable(it, getMaxNumberOfRows(rows)), plain = null) }
    }

    private fun plainPreview(inputStream: InputStream, mediaType: String?, charset: Charset?): Preview {
        logDebug("Fetch plain content")
        
        val content = IOUtils.toString(inputStream, charset ?: Charset.forName("UTF-8"))
        val sanitizedContent = ContentSanitizer.removeDangerousContent(content) // Remove dangerous HTML/script content
        val plain = Plain(sanitizedContent, mediaType ?: "")
        return Preview(table=null, plain=plain)
    }

    private fun parseCSVToTable(parser: CSVParser, maxNumberOfRows: Int): Table {
        var tableHeader = TableHeader(arrayListOf())
        val tableRows = arrayListOf<TableRow>()
        val it = parser.iterator()
        if(it.hasNext()) {
            tableHeader = TableHeader(it.next().map { headerValue ->
                ContentSanitizer.sanitizeCellContent(headerValue) // Sanitize CSV header content to prevent XSS
            })
            tableHeader.beautify()
        }

        while (it.hasNext() && (maxNumberOfRows <= 0 || tableRows.size < maxNumberOfRows)) {
            tableRows.add(TableRow(it.next().map { cellValue -> 
                ContentSanitizer.sanitizeCellContent(cellValue) // Sanitize CSV cell content to prevent XSS
            }))
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
