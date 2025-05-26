package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.Preview
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

    @Test
    fun test_if_csv_resource_parses_as_valid_table() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.csv"),
            javaClass.classLoader.getResourceAsStream("test.csv"))
        whenever(responseBody.contentType()).thenReturn(
            "text/csv; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.csv"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("981397290", table.rows[0].columns[0])
        Assertions.assertEquals("565.6", table.rows[6].columns[26])
    }

    @Test
    fun test_if_zip_resource_parses_as_valid_table() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.csv.zip"),
            javaClass.classLoader.getResourceAsStream("test.csv.zip"))
        whenever(responseBody.contentType()).thenReturn(
            "application/zip".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.csv.zip"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("981397290", table.rows[0].columns[0])
        Assertions.assertEquals("565.6", table.rows[6].columns[26])
    }

    @Test
    fun test_if_msexcel_with_additional_chars_in_contenttype_resource_parses_as_valid_table() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.csv"),
            javaClass.classLoader.getResourceAsStream("test.csv"))
        whenever(responseBody.contentType()).thenReturn(
            "application~/vnd.ms-excel~; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.csv"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("981397290", table.rows[0].columns[0])
        Assertions.assertEquals("565.6", table.rows[6].columns[26])
    }

    @Test
    fun test_if_xlsx_resource_parses_as_valid_table() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.xlsx"))
        whenever(responseBody.contentType()).thenReturn(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.xlsx"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table!!

        Assertions.assertEquals("Ansvar:", table.header.columns[0])
        Assertions.assertEquals("2013", table.header.columns[6])
        Assertions.assertEquals("100", table.rows[0].columns[0])
        Assertions.assertEquals("80000", table.rows[9].columns[6])
    }

    @Test
    fun test_if_resource_parses_as_valid_plain() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.xml"))
        whenever(responseBody.contentType()).thenReturn(
            "application/xml; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.xml"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table
        val resource = javaClass.classLoader.getResource("test.xml")!!

        Assertions.assertNull(table)
        Assertions.assertEquals(resource
            .readText(Charsets.UTF_8), preview.plain?.value)
        Assertions.assertEquals("application/xml; charset=utf-8", preview.plain?.contentType)
    }

    @Test
    fun test_if_resource_with_extended_xml_parses_as_valid_plain() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.xml"))
        whenever(responseBody.contentType()).thenReturn(
            "application/3gpp-ims+xml; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.xml"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table
        val resource = javaClass.classLoader.getResource("test.xml")!!

        Assertions.assertNull(table)
        Assertions.assertEquals(resource
            .readText(Charsets.UTF_8), preview.plain?.value)
        Assertions.assertEquals("application/3gpp-ims+xml; charset=utf-8", preview.plain?.contentType)
    }

    @Test
    fun test_if_resource_with_extended_json_parses_as_valid_plain() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.json"))
        whenever(responseBody.contentType()).thenReturn(
            "application/alto-costmap+json; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.json"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table
        val resource = javaClass.classLoader.getResource("test.json")!!

        Assertions.assertNull(table)
        Assertions.assertEquals(resource
            .readText(Charsets.UTF_8), preview.plain?.value)
        Assertions.assertEquals("application/alto-costmap+json; charset=utf-8", preview.plain?.contentType)
    }

    @Test
    fun test_if_resource_parses_as_invalid_content_type() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.xml"))
        whenever(responseBody.contentType()).thenReturn(
            "text/turtle; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.ttl"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        Assertions.assertThrows(Exception::class.java) {
            previewService.readAndParseResource(resourceUrl, 10)
        }
    }

    @Test
    fun test_if_parser_handles_iso_charset() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("iso-charset.csv"),
            javaClass.classLoader.getResourceAsStream("iso-charset.csv"))
        whenever(responseBody.contentType()).thenReturn(
            "text/csv; charset=iso-8859-1".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/iso-charset.csv"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("TRØNDSEN TERJE", table.rows[0].columns[1])
    }

    @Test
    fun test_if_parser_handles_utf8_charset() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("utf8-charset.csv"),
            javaClass.classLoader.getResourceAsStream("utf8-charset.csv"))
        whenever(responseBody.contentType()).thenReturn(
            "text/csv; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/utf8-charset.csv"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("TRØNDSEN TERJE", table.rows[0].columns[1])
    }

    @Test
    fun test_if_parser_handles_utf16_charset() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("utf16-charset.csv"),
            javaClass.classLoader.getResourceAsStream("utf16-charset.csv"))
        whenever(responseBody.contentType()).thenReturn(
            "text/csv; charset=utf-16".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/utf16-charset.csv"
        whenever(downloader.download(eq(resourceUrl), any<(ResponseBody) -> Preview>())).thenAnswer { invocation ->
            val block = invocation.getArgument<(ResponseBody) -> Preview>(1)
            block(responseBody)
        }

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("TRØNDSEN TERJE", table.rows[0].columns[1])
    }
}
