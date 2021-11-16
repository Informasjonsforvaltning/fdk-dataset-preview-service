package no.fdk.dataset.preview.service

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.ResponseBody
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test

@Tag("unit")
class PreviewServiceTest {

    private val downloader: FileDownloader = mock()
    private val previewService = PreviewService(downloader)

    @Test
    fun test_if_resource_parses_as_valid_table() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.csv"),
            javaClass.classLoader.getResourceAsStream("test.csv"))
        whenever(responseBody.contentType()).thenReturn(
            "text/csv; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.csv"
        whenever(downloader.download(resourceUrl)).thenReturn(responseBody)

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table!!

        Assertions.assertEquals("Orgnr", table.header.columns[0])
        Assertions.assertEquals("Ull kg", table.header.columns[26])
        Assertions.assertEquals("981397290", table.rows[0].columns[0])
        Assertions.assertEquals("565.6", table.rows[6].columns[26])
    }

    @Test
    fun test_if_resource_parses_as_valid_plain() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.xml"))
        whenever(responseBody.contentType()).thenReturn(
            "application/xml; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.xml"
        whenever(downloader.download(resourceUrl)).thenReturn(responseBody)

        val preview = previewService.readAndParseResource(resourceUrl, 10)
        val table = preview.table
        val resource = javaClass.classLoader.getResource("test.xml")!!

        Assertions.assertNull(table)
        Assertions.assertEquals(resource
            .readText(Charsets.UTF_8), preview.plain?.value)
        Assertions.assertEquals("application/xml; charset=utf-8", preview.plain?.contentType)
    }

    @Test
    fun test_if_resource_parses_as_invalid_content_type() {
        val responseBody: ResponseBody = mock()
        whenever(responseBody.byteStream()).thenReturn(
            javaClass.classLoader.getResourceAsStream("test.xml"))
        whenever(responseBody.contentType()).thenReturn(
            "text/turtle; charset=utf-8".toMediaTypeOrNull())

        val resourceUrl = "http://domain.com/test.xml"
        whenever(downloader.download(resourceUrl)).thenReturn(responseBody)

        Assertions.assertThrows(Exception::class.java) {
            previewService.readAndParseResource(resourceUrl, 10)
        }
    }

}
