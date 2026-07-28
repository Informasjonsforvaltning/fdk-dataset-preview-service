package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.Plain
import no.fdk.dataset.preview.model.Preview
import no.fdk.dataset.preview.model.Table
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@Tag("unit")
class PreviewServiceTest {
    private val downloader: FileDownloader = mock()
    private val previewService = PreviewService(downloader)

    private fun mockDownload(
        resourceUrl: String,
        contentType: String,
        vararg resourceStreams: String,
        contentLength: Long = 0,
    ) {
        val responseBody: ResponseBody = mock()
        val streams = resourceStreams.map(::resourceStream).toTypedArray()
        whenever(responseBody.byteStream()).thenReturn(streams.first(), *streams.drop(1).toTypedArray())
        whenever(responseBody.contentType()).thenReturn(contentType.toMediaTypeOrNull())
        whenever(responseBody.contentLength()).thenReturn(contentLength)

        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }
    }

    private fun resourceStream(resourceName: String) = requireNotNull(javaClass.classLoader.getResourceAsStream(resourceName))

    private fun resourceText(resourceName: String) =
        requireNotNull(javaClass.classLoader.getResource(resourceName)).readText(Charsets.UTF_8)

    private fun parsePreview(
        resourceUrl: String,
        rows: Int = 10,
    ): Preview = previewService.readAndParseResource(resourceUrl, rows)

    private fun assertCsvLikePreview(table: Table) {
        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("981397290", table.rows[0].columns[0])
        Assertions.assertEquals("565.6", table.rows[6].columns[26])
    }

    private fun assertPlainPreview(
        plain: Plain?,
        expectedResource: String,
        expectedContentType: String,
    ) {
        Assertions.assertEquals(resourceText(expectedResource), plain?.value)
        Assertions.assertEquals(expectedContentType, plain?.contentType)
    }

    @Test
    fun test_if_csv_resource_parses_as_valid_table() {
        val resourceUrl = "http://domain.com/test.csv"
        mockDownload(resourceUrl, "text/csv; charset=utf-8", "test.csv", "test.csv")

        val preview = parsePreview(resourceUrl)
        assertCsvLikePreview(preview.table!!)
    }

    @Test
    fun test_if_zip_resource_parses_as_valid_table() {
        val resourceUrl = "http://domain.com/test.csv.zip"
        mockDownload(resourceUrl, "application/zip", "test.csv.zip", "test.csv.zip")

        val preview = parsePreview(resourceUrl)
        assertCsvLikePreview(preview.table!!)
    }

    @Test
    fun test_if_msexcel_with_additional_chars_in_contenttype_resource_parses_as_valid_table() {
        val resourceUrl = "http://domain.com/test.csv"
        mockDownload(resourceUrl, "application~/vnd.ms-excel~; charset=utf-8", "test.csv", "test.csv")

        val preview = parsePreview(resourceUrl)
        assertCsvLikePreview(preview.table!!)
    }

    @Test
    fun test_if_xlsx_resource_parses_as_valid_table() {
        val resourceUrl = "http://domain.com/test.xlsx"
        mockDownload(resourceUrl, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; charset=utf-8", "test.xlsx")

        val preview = parsePreview(resourceUrl)
        val table = preview.table!!

        Assertions.assertEquals("Ansvar:", table.header.columns[0])
        Assertions.assertEquals("2013", table.header.columns[6])
        Assertions.assertEquals("100", table.rows[0].columns[0])
        Assertions.assertEquals("80000", table.rows[9].columns[6])
    }

    @Test
    fun test_if_xls_resource_parses_as_valid_table() {
        val resourceUrl = "http://domain.com/test.xls"
        mockDownload(resourceUrl, "application/octet-stream", "test.xls")

        val preview = parsePreview(resourceUrl)
        val table = preview.table!!

        Assertions.assertEquals("Id", table.header.columns[0])
        Assertions.assertEquals("Name", table.header.columns[1])
        Assertions.assertEquals("Value", table.header.columns[2])
        Assertions.assertEquals(3, table.rows.size)
        Assertions.assertEquals("1", table.rows[0].columns[0])
        Assertions.assertEquals("Alpha", table.rows[0].columns[1])
        Assertions.assertEquals("100", table.rows[0].columns[2])
        Assertions.assertEquals("Gamma", table.rows[2].columns[1])
        Assertions.assertNull(preview.plain)
    }

    @Test
    fun test_if_xls_with_ms_excel_content_type_parses_as_valid_table() {
        val resourceUrl = "http://domain.com/test.xls"
        mockDownload(resourceUrl, "application/vnd.ms-excel", "test.xls")

        val preview = parsePreview(resourceUrl)
        val table = preview.table!!

        Assertions.assertEquals("Id", table.header.columns[0])
        Assertions.assertEquals("Alpha", table.rows[0].columns[1])
        Assertions.assertNull(preview.plain)
    }

    @Test
    fun test_if_oversized_xlsx_is_rejected() {
        val resourceUrl = "http://domain.com/large.xlsx"
        mockDownload(
            resourceUrl,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "test.xlsx",
            contentLength = 20_000_000,
        )

        val exception =
            Assertions.assertThrows(PreviewException::class.java) {
                parsePreview(resourceUrl)
            }
        Assertions.assertEquals("File is too large to process", exception.message)
    }

    @Test
    fun test_if_resource_parses_as_valid_plain() {
        val resourceUrl = "http://domain.com/test.xml"
        mockDownload(resourceUrl, "application/xml; charset=utf-8", "test.xml")

        val preview = parsePreview(resourceUrl)
        Assertions.assertNull(preview.table)
        assertPlainPreview(preview.plain, "test.xml", "application/xml; charset=utf-8")
    }

    @Test
    fun test_if_resource_with_extended_xml_parses_as_valid_plain() {
        val resourceUrl = "http://domain.com/test.xml"
        mockDownload(resourceUrl, "application/3gpp-ims+xml; charset=utf-8", "test.xml")

        val preview = parsePreview(resourceUrl)
        Assertions.assertNull(preview.table)
        assertPlainPreview(preview.plain, "test.xml", "application/3gpp-ims+xml; charset=utf-8")
    }

    @Test
    fun test_if_resource_with_extended_json_parses_as_valid_plain() {
        val resourceUrl = "http://domain.com/test.json"
        mockDownload(resourceUrl, "application/alto-costmap+json; charset=utf-8", "test.json")

        val preview = parsePreview(resourceUrl)
        Assertions.assertNull(preview.table)
        assertPlainPreview(preview.plain, "test.json", "application/alto-costmap+json; charset=utf-8")
    }

    @Test
    fun test_if_resource_parses_as_invalid_content_type() {
        val resourceUrl = "http://domain.com/test.ttl"
        mockDownload(resourceUrl, "text/turtle; charset=utf-8", "test.xml")

        Assertions.assertThrows(Exception::class.java) {
            parsePreview(resourceUrl)
        }
    }

    @Test
    fun test_if_parser_handles_iso_charset() {
        val resourceUrl = "http://domain.com/iso-charset.csv"
        mockDownload(resourceUrl, "text/csv; charset=iso-8859-1", "iso-charset.csv", "iso-charset.csv")

        val preview = parsePreview(resourceUrl)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("TRØNDSEN TERJE", table.rows[0].columns[1])
    }

    @Test
    fun test_if_parser_handles_utf8_charset() {
        val resourceUrl = "http://domain.com/utf8-charset.csv"
        mockDownload(resourceUrl, "text/csv; charset=utf-8", "utf8-charset.csv", "utf8-charset.csv")

        val preview = parsePreview(resourceUrl)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("TRØNDSEN TERJE", table.rows[0].columns[1])
    }

    @Test
    fun test_if_parser_handles_utf16_charset() {
        val resourceUrl = "http://domain.com/utf16-charset.csv"
        mockDownload(resourceUrl, "text/csv; charset=utf-16", "utf16-charset.csv", "utf16-charset.csv")

        val preview = parsePreview(resourceUrl)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("TRØNDSEN TERJE", table.rows[0].columns[1])
    }
}
