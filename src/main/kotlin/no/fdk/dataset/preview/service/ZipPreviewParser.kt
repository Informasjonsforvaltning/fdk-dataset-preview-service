package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.ErrorType
import no.fdk.dataset.preview.model.Preview
import org.apache.tika.Tika
import org.apache.tika.metadata.Metadata
import org.apache.tika.mime.MediaType
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream

@Component
class ZipPreviewParser(
    private val limits: PreviewLimits,
    private val excelPreviewParser: ExcelPreviewParser,
    private val csvPreviewParser: CsvPreviewParser,
    private val plainPreviewParser: PlainPreviewParser,
) {
    private val logger: Logger = LoggerFactory.getLogger(ZipPreviewParser::class.java)

    fun parse(
        rows: Int?,
        inputStream: InputStream,
    ): Preview {
        logDebug("Extracting zip")

        val zis = ZipInputStream(inputStream)

        try {
            var zipEntry = zis.nextEntry
            while (zipEntry != null) {
                val entryFormat =
                    if (zipEntry.isDirectory) {
                        null
                    } else {
                        PreviewFormat.fromFileName(zipEntry.name)
                    }

                if (entryFormat != null) {
                    if (zipEntry.size > limits.maxFileSizeBytes) {
                        throw PreviewException("File is too large to process", ErrorType.FILE_TOO_LARGE)
                    }

                    when (entryFormat) {
                        PreviewFormat.XLSX -> {
                            return excelPreviewParser.parseXlsx(rows, zis)
                        }

                        PreviewFormat.XLS -> {
                            return excelPreviewParser.parseXls(rows, zis)
                        }

                        PreviewFormat.CSV -> {
                            val bis = zis.toByteArrayInputStream()
                            return csvPreviewParser.parse(
                                rows,
                                bis,
                                null,
                                bis.getMediaType().getCharset(),
                            )
                        }

                        PreviewFormat.PLAIN -> {
                            val bis = zis.toByteArrayInputStream()
                            val mediaType = bis.getMediaType()
                            return plainPreviewParser.parse(
                                bis,
                                mediaType.toString(),
                                mediaType.getCharset(),
                            )
                        }

                        PreviewFormat.ZIP -> {
                            // Zip entries are only considered when they contain previewable files.
                        }
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

        throw PreviewException("Invalid zip file content", ErrorType.PARSE_ERROR)
    }

    private fun ZipInputStream.toByteArrayInputStream(): ByteArrayInputStream {
        val buffer = ByteArray(8192)
        val bos = ByteArrayOutputStream()
        var total = 0L
        var len: Int
        while (read(buffer).also { len = it } > 0) {
            total += len
            if (total > limits.maxFileSizeBytes) {
                throw PreviewException("File is too large to process", ErrorType.FILE_TOO_LARGE)
            }
            bos.write(buffer, 0, len)
        }
        bos.close()

        return ByteArrayInputStream(bos.toByteArray())
    }

    private fun InputStream.getMediaType(): MediaType = Tika().detector.detect(this, Metadata())

    private fun MediaType.getCharset(): Charset = Charset.forName(parameters.getOrDefault("charset", "UTF-8"))

    private fun logDebug(message: String) {
        if (logger.isDebugEnabled) {
            logger.debug(message)
        }
    }
}
