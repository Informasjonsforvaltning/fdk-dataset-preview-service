package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.Table
import no.fdk.dataset.preview.model.TableHeader
import no.fdk.dataset.preview.model.TableRow
import okhttp3.OkHttpClient
import org.apache.commons.csv.CSVFormat
import org.apache.commons.io.input.BOMInputStream
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.*


private val LOGGER: Logger = LoggerFactory.getLogger(PreviewService::class.java)

@Service
class PreviewService {
    private val DELIMITERS = arrayOf(';', ',')
    private val NO_DELIMITER = '\u0000' //empty char

    @Throws(IOException::class)
    private fun detectDelimiter(inputStream: InputStream): Char {
        BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8), 500).use { reader ->
            val line = reader.readLine()
            return Arrays.stream(DELIMITERS)
                .filter { s -> line.contains(s.toString()) }
                .findFirst()
                .orElse(NO_DELIMITER)
        }
    }

    @Throws(Exception::class)
    fun readCSV(resourceUrl: String, numberOfRows: Int): Table {
        val downloader = FileDownloader(OkHttpClient())
        var body = downloader.download(resourceUrl)
        if(!isCsv(body.contentType()?.subtype)) {
            throw Exception("Invalid content type ${body.contentType().toString()}")
        }

        val delimiter = detectDelimiter(body.byteStream());

        return CSVFormat.DEFAULT.builder()
            .setDelimiter(delimiter)
            .build()
            .parse( InputStreamReader( BOMInputStream(
                downloader.download(resourceUrl).byteStream()), StandardCharsets.UTF_8)).use { parser ->
                var tableHeader = TableHeader(arrayListOf())
                val tableRows = arrayListOf<TableRow>()
                val it = parser.iterator()
                if(it.hasNext()) {
                    tableHeader = TableHeader(it.next().toList())
                    tableHeader.beautify()
                }

                while (it.hasNext() && (numberOfRows <= 0 || tableRows.size < numberOfRows)) {
                    tableRows.add(TableRow(it.next().toList()))
                }

                return Table(tableHeader, tableRows)
            }
    }

    fun isCsv(subType: String?): Boolean {
        if (subType != null) {
            return subType.contains("csv") || subType.contains("vnd.ms-excel")
        }
        return false
    }

    @Throws(Exception::class)
    fun parseToTable(resourceUrl: String, numberOfRows: Int?): Table {
        return readCSV(resourceUrl, numberOfRows ?: -1)
    }
}
