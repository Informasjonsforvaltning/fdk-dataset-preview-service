package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.ErrorType
import no.fdk.dataset.preview.model.Preview
import no.fdk.dataset.preview.model.Table
import no.fdk.dataset.preview.model.TableHeader
import no.fdk.dataset.preview.model.TableRow
import no.fdk.dataset.preview.util.ContentSanitizer
import org.apache.poi.hssf.usermodel.HSSFWorkbook
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import java.io.FileInputStream
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream

@Component
class ExcelPreviewParser(private val limits: PreviewLimits) {
    private val logger: Logger = LoggerFactory.getLogger(ExcelPreviewParser::class.java)

    private class SheetParseLimitReached : SAXException("Sheet parse row limit reached")

    fun parseXlsx(rows: Int?, inputStream: InputStream): Preview {
        logger.logDebug("Parsing Excel")

        val maxRowsToProcess = limits.getMaxNumberOfRows(rows) * 2
        val startTime = System.currentTimeMillis()
        val tableRows = arrayListOf<TableRow>()
        var lastCellNum = 0

        val tempFile = inputStream.copyToTempFile(limits.maxFileSizeBytes)
        try {
            OPCPackage.open(tempFile.toFile(), PackageAccess.READ).use { pkg ->
                val sharedStrings = ReadOnlySharedStringsTable(pkg)
                val reader = XSSFReader(pkg)
                val styles: StylesTable? =
                    try {
                        reader.stylesTable
                    } catch (_: Exception) {
                        null
                    }
                val sheets = reader.sheetsData
                if (!sheets.hasNext()) {
                    throw PreviewException("Invalid Excel file content", ErrorType.PARSE_ERROR)
                }

                sheets.next().use { sheetStream ->
                    val sheetHandler =
                        object : XSSFSheetXMLHandler.SheetContentsHandler {
                            private var currentRow = arrayListOf<String>()

                            override fun startRow(rowNum: Int) {
                                if (System.currentTimeMillis() - startTime > limits.maxProcessingTimeSeconds * 1000) {
                                    throw PreviewException("File processing timeout exceeded", ErrorType.PROCESSING_TIMEOUT)
                                }
                                if (tableRows.size >= maxRowsToProcess) {
                                    logger.logDebug("Excel processing limited to $maxRowsToProcess rows for security")
                                    throw SheetParseLimitReached()
                                }
                                currentRow = arrayListOf()
                            }

                            override fun endRow(rowNum: Int) {
                                if (currentRow.size > PreviewLimits.MAX_COLUMNS) {
                                    currentRow = ArrayList(currentRow.subList(0, PreviewLimits.MAX_COLUMNS))
                                }

                                lastCellNum =
                                    when {
                                        currentRow.size <= lastCellNum -> lastCellNum
                                        currentRow.lastOrNull()?.isNotEmpty() == true -> currentRow.size
                                        else -> lastCellNum
                                    }

                                tableRows.add(TableRow(currentRow.toList()))
                            }

                            override fun cell(cellReference: String?, formattedValue: String?, comment: XSSFComment?) {
                                if (cellReference == null) return
                                val col = CellReference(cellReference).col.toInt()
                                if (col < 0 || col >= PreviewLimits.MAX_COLUMNS) return

                                while (currentRow.size <= col) {
                                    currentRow.add("")
                                }
                                currentRow[col] = ContentSanitizer.sanitizeCellContent(formattedValue ?: "")
                            }
                        }

                    val formatter = DataFormatter()
                    val xmlHandler =
                        XSSFSheetXMLHandler(
                            styles,
                            sharedStrings,
                            sheetHandler,
                            formatter,
                            false,
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

                            is PreviewException -> {
                                throw root
                            }

                            else -> {
                                throw e
                            }
                        }
                    }
                }
            }
        } catch (e: PreviewException) {
            throw e
        } catch (e: Exception) {
            when (val root = e.unwrapCause()) {
                is PreviewException -> {
                    throw root
                }

                else -> {
                    logger.logDebug("Failed to parse Excel", e)
                    throw PreviewException("Failed to parse Excel file", ErrorType.PARSE_ERROR)
                }
            }
        } finally {
            tempFile.deleteIfExists()
        }

        if (tableRows.isEmpty()) {
            throw PreviewException("Invalid Excel file content", ErrorType.PARSE_ERROR)
        }

        return buildExcelPreview(tableRows, lastCellNum, rows)
    }

    fun parseXls(rows: Int?, inputStream: InputStream): Preview {
        logger.logDebug("Parsing legacy Excel (.xls)")

        val maxRowsToProcess = limits.getMaxNumberOfRows(rows) * 2
        val startTime = System.currentTimeMillis()
        val tableRows = arrayListOf<TableRow>()
        var lastCellNum = 0
        val formatter = DataFormatter()

        val tempFile = inputStream.copyToTempFile(limits.maxFileSizeBytes, ".xls")
        try {
            FileInputStream(tempFile.toFile()).use { fileInputStream ->
                HSSFWorkbook(fileInputStream).use { workbook ->
                    if (workbook.numberOfSheets == 0) {
                        throw PreviewException("Invalid Excel file content", ErrorType.PARSE_ERROR)
                    }

                    val sheet = workbook.getSheetAt(0)
                    for (rowIndex in 0..sheet.lastRowNum) {
                        if (System.currentTimeMillis() - startTime > limits.maxProcessingTimeSeconds * 1000) {
                            throw PreviewException("File processing timeout exceeded", ErrorType.PROCESSING_TIMEOUT)
                        }
                        if (tableRows.size >= maxRowsToProcess) {
                            logger.logDebug("Excel processing limited to $maxRowsToProcess rows for security")
                            break
                        }

                        val row = sheet.getRow(rowIndex) ?: continue
                        val lastCellInRow = row.lastCellNum.toInt().coerceAtMost(PreviewLimits.MAX_COLUMNS)
                        if (lastCellInRow <= 0) {
                            tableRows.add(TableRow(emptyList()))
                            continue
                        }

                        val currentRow = ArrayList<String>(lastCellInRow)
                        for (col in 0 until lastCellInRow) {
                            val cell = row.getCell(col)
                            currentRow.add(
                                ContentSanitizer.sanitizeCellContent(
                                    if (cell != null) formatter.formatCellValue(cell) else "",
                                ),
                            )
                        }

                        lastCellNum =
                            when {
                                currentRow.size <= lastCellNum -> lastCellNum
                                currentRow.lastOrNull()?.isNotEmpty() == true -> currentRow.size
                                else -> lastCellNum
                            }

                        tableRows.add(TableRow(currentRow.toList()))
                    }
                }
            }
        } catch (e: PreviewException) {
            throw e
        } catch (e: Exception) {
            logger.logDebug("Failed to parse Excel", e)
            throw PreviewException("Failed to parse Excel file", ErrorType.PARSE_ERROR)
        } finally {
            tempFile.deleteIfExists()
        }

        if (tableRows.isEmpty()) {
            throw PreviewException("Invalid Excel file content", ErrorType.PARSE_ERROR)
        }

        return buildExcelPreview(tableRows, lastCellNum, rows)
    }

    private fun buildExcelPreview(tableRows: List<TableRow>, lastCellNum: Int, rows: Int?): Preview {
        val headerIndex =
            if (lastCellNum > 0) {
                tableRows.indexOfFirst {
                    it.columns.size == lastCellNum && it.columns[lastCellNum - 1].isNotEmpty()
                }
            } else {
                -1
            }

        val startHeaderIndex = if (headerIndex == -1) 0 else headerIndex
        val endHeaderIndex = startHeaderIndex + 1
        val maxDataRows = limits.getMaxNumberOfRows(rows)
        val endRowsIndex = minOf(endHeaderIndex + maxDataRows, tableRows.size)

        val header = TableHeader(tableRows.subList(startHeaderIndex, endHeaderIndex)[0].columns).beautified()

        val table = Table(header, tableRows.subList(endHeaderIndex, endRowsIndex))
        return Preview(table = table, plain = null)
    }

    private fun InputStream.copyToTempFile(maxBytes: Long, suffix: String = ".xlsx"): Path {
        val tempFile = Files.createTempFile("fdk-preview-", suffix)
        try {
            tempFile.outputStream().use { out ->
                val buffer = ByteArray(8192)
                var total = 0L
                var read: Int
                while (read(buffer).also { read = it } != -1) {
                    total += read
                    if (total > maxBytes) {
                        throw PreviewException("File is too large to process", ErrorType.FILE_TOO_LARGE)
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
}
