package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.Preview
import no.fdk.dataset.preview.model.PreviewRequest
import no.fdk.dataset.preview.service.utils.ApiTestContext
import no.fdk.dataset.preview.service.utils.CsrfTestException
import no.fdk.dataset.preview.service.utils.authorizedRequest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ContextConfiguration
import tools.jackson.module.kotlin.jacksonObjectMapper
import kotlin.test.assertEquals

private val mapper = jacksonObjectMapper()

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    properties = [
        "spring.profiles.active=integration-test",
        "logging.level.no.fdk=DEBUG",
    ],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
)
@ContextConfiguration(initializers = [ApiTestContext.Initializer::class])
@Tag("integration")
class PreviewContractTest : ApiTestContext() {
    private fun postPreview(
        resourceUrl: String,
        rows: Int,
        apiKey: String? = "my-api-key",
    ) = authorizedRequest(
        "/preview",
        port,
        mapper.writeValueAsString(PreviewRequest(resourceUrl, rows)),
        apiKey,
        HttpMethod.POST,
    )

    private fun previewFrom(response: Map<String, Any?>): Preview = mapper.readValue("${response["body"]}", Preview::class.java)

    @Test
    fun `Unauthorized when api token is not included`() {
        assertThrows<CsrfTestException> {
            postPreview("http://localhost:5050/download", 5, apiKey = null)
        }
    }

    @Test
    fun ok_csv() {
        val rsp = postPreview("http://localhost:5050/download/csv", 5)
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = previewFrom(rsp)
        assertEquals("Orgnr", preview.table!!.header.columns[0])
        assertEquals(5, preview.table!!.rows.size)
        assertNull(preview.plain)
    }

    @Test
    fun ok_csv_zip() {
        val rsp = postPreview("http://localhost:5050/download/csv-zip", 5)
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = previewFrom(rsp)
        assertEquals("Orgnr", preview.table!!.header.columns[0])
        assertEquals(5, preview.table!!.rows.size)
        assertNull(preview.plain)
    }

    @Test
    fun ok_xlsx_zip() {
        val rsp = postPreview("http://localhost:5050/download/xlsx-zip", 10)
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = previewFrom(rsp)
        val table = preview.table!!
        Assertions.assertEquals("Ansvar:", table.header.columns[0])
        Assertions.assertEquals("2013", table.header.columns[6])
        Assertions.assertEquals("100", table.rows[0].columns[0])
        Assertions.assertEquals("80000", table.rows[9].columns[6])
    }

    @Test
    fun ok_xls() {
        val rsp = postPreview("http://localhost:5050/download/test.xls", 10)
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = previewFrom(rsp)
        val table = preview.table!!
        Assertions.assertEquals("Id", table.header.columns[0])
        Assertions.assertEquals("Name", table.header.columns[1])
        Assertions.assertEquals(3, table.rows.size)
        Assertions.assertEquals("Alpha", table.rows[0].columns[1])
        Assertions.assertEquals("300", table.rows[2].columns[2])
        assertNull(preview.plain)
    }

    @Test
    fun ok_xls_zip() {
        val rsp = postPreview("http://localhost:5050/download/xls-zip", 10)
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = previewFrom(rsp)
        val table = preview.table!!
        Assertions.assertEquals("Id", table.header.columns[0])
        Assertions.assertEquals("Beta", table.rows[1].columns[1])
        assertNull(preview.plain)
    }

    @Test
    fun ok_json_zip() {
        val rsp = postPreview("http://localhost:5050/download/json-zip", 10)
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = previewFrom(rsp)
        val table = preview.table
        val resource = javaClass.classLoader.getResource("test.json")!!

        Assertions.assertNull(table)
        Assertions.assertEquals(
            resource
                .readText(Charsets.UTF_8),
            preview.plain?.value,
        )
        Assertions.assertEquals("text/plain", preview.plain?.contentType)
    }

    @Test
    fun `Bad request`() {
        val rsp = postPreview("http://localhost:5050/download-link-does-not-exist", 5)
        assertEquals(HttpStatus.BAD_REQUEST.value(), rsp["status"])
    }

    @Test
    fun `Invalid url`() {
        val rsp = postPreview("https://local", 5)
        assertEquals(HttpStatus.BAD_REQUEST.value(), rsp["status"])
    }

    @Test
    fun `Illegal url`() {
        val rsp = postPreview("https://kubernetes.default.svc", 5)
        assertEquals(HttpStatus.BAD_REQUEST.value(), rsp["status"])
    }
}
