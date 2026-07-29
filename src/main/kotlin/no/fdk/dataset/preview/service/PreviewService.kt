package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.ErrorType
import no.fdk.dataset.preview.model.Preview
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.InputStream
import java.nio.charset.Charset

@Service
class PreviewService(
    private val downloader: FileDownloader,
    private val limits: PreviewLimits,
    private val zipPreviewParser: ZipPreviewParser,
    private val excelPreviewParser: ExcelPreviewParser,
    private val csvPreviewParser: CsvPreviewParser,
    private val plainPreviewParser: PlainPreviewParser,
) {
    fun readAndParseResource(
        resourceUrl: String,
        rows: Int?,
    ): Preview {
        LOGGER.logDebug("Read and parse resource $resourceUrl")

        try {
            return downloader.download(resourceUrl, { body ->
                val contentLength = body.contentLength()
                if (contentLength > limits.maxFileSizeBytes) {
                    throw PreviewException("File is too large to process", ErrorType.FILE_TOO_LARGE)
                }

                body.byteStream().use { inputStream ->
                    when (PreviewFormat.detect(body.contentType().toString(), resourceUrl)) {
                        PreviewFormat.ZIP -> {
                            zipPreviewParser.parse(rows, inputStream)
                        }

                        PreviewFormat.XLSX -> {
                            excelPreviewParser.parseXlsx(rows, inputStream)
                        }

                        PreviewFormat.XLS -> {
                            excelPreviewParser.parseXls(rows, inputStream)
                        }

                        PreviewFormat.CSV -> {
                            csvPreviewFromResource(
                                resourceUrl = resourceUrl,
                                rows = rows,
                                inputStream = inputStream,
                                charset = body.contentType()?.charset(),
                            )
                        }

                        PreviewFormat.PLAIN -> {
                            plainPreviewParser.parse(
                                inputStream,
                                body.contentType().toString(),
                                body.contentType()?.charset(),
                            )
                        }

                        null -> {
                            throw PreviewException("Unsupported file format", ErrorType.UNSUPPORTED_FORMAT)
                        }
                    }
                }
            })
        } catch (e: DownloadException) {
            LOGGER.logDebug("Unable to download resource $resourceUrl", e)
            throw PreviewException("Failed to download file", ErrorType.DOWNLOAD_FAILED)
        }
    }

    private fun csvPreviewFromResource(
        resourceUrl: String,
        rows: Int?,
        inputStream: InputStream,
        charset: Charset?,
    ): Preview =
        if (!inputStream.markSupported()) {
            downloader.download(resourceUrl) { secondBody ->
                secondBody.byteStream().use { secondInputStream ->
                    csvPreviewParser.parse(
                        rows,
                        inputStream,
                        secondInputStream,
                        charset,
                    )
                }
            }
        } else {
            csvPreviewParser.parse(
                rows,
                inputStream,
                null,
                charset,
            )
        }

    companion object {
        private val LOGGER: Logger = LoggerFactory.getLogger(PreviewService::class.java)
    }
}
