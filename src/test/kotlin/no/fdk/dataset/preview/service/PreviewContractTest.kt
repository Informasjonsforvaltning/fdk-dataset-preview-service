package no.fdk.dataset.preview.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.fdk.dataset.preview.model.Preview
import no.fdk.dataset.preview.model.PreviewRequest
import no.fdk.dataset.preview.service.utils.ApiTestContext
import no.fdk.dataset.preview.service.utils.CsrfTestException
import no.fdk.dataset.preview.service.utils.authorizedRequest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertNull
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ContextConfiguration
import kotlin.test.assertEquals

private val mapper = jacksonObjectMapper()

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    properties = [
        "spring.profiles.active=integration-test",
        "logging.level.no.fdk=DEBUG"],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = [ApiTestContext.Initializer::class])
@Tag("integration")
class PreviewContractTest : ApiTestContext() {

    @Test
    fun `Unauthorized when api token is not included`() {
        assertThrows<CsrfTestException> {
            val previewRequest = PreviewRequest("http://localhost:5000/download", 5)
            val rsp = authorizedRequest("/preview", port, mapper.writeValueAsString(previewRequest),
                null, HttpMethod.POST)
        }
    }

    @Test
    fun ok_csv() {
        val previewRequest = PreviewRequest("http://localhost:5000/download/csv", 5)
        val rsp = authorizedRequest(
            "/preview", port, mapper.writeValueAsString(previewRequest),
            "my-api-key", HttpMethod.POST
        )
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = mapper.readValue("${rsp["body"]}", Preview::class.java)
        assertEquals("Orgnr", preview.table!!.header.columns[0])
        assertEquals(5, preview.table!!.rows.size)
        assertNull(preview.plain)
    }

    @Test
    fun ok_csv_zip() {
        val previewRequest = PreviewRequest("http://localhost:5000/download/csv-zip", 5)
        val rsp = authorizedRequest(
            "/preview", port, mapper.writeValueAsString(previewRequest),
            "my-api-key", HttpMethod.POST
        )
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = mapper.readValue("${rsp["body"]}", Preview::class.java)
        assertEquals("Orgnr", preview.table!!.header.columns[0])
        assertEquals(5, preview.table!!.rows.size)
        assertNull(preview.plain)
    }

    @Test
    fun ok_xlsx_zip() {
        val previewRequest = PreviewRequest("http://localhost:5000/download/xlsx-zip", 10)
        val rsp = authorizedRequest(
            "/preview", port, mapper.writeValueAsString(previewRequest),
            "my-api-key", HttpMethod.POST
        )
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = mapper.readValue("${rsp["body"]}", Preview::class.java)
        val table = preview.table!!
        Assertions.assertEquals("Ansvar:", table.header.columns[0])
        Assertions.assertEquals("2013", table.header.columns[6])
        Assertions.assertEquals("100", table.rows[0].columns[0])
        Assertions.assertEquals("80000", table.rows[9].columns[6])
    }

    @Test
    fun ok_json_zip() {
        val previewRequest = PreviewRequest("http://localhost:5000/download/json-zip", 10)
        val rsp = authorizedRequest(
            "/preview", port, mapper.writeValueAsString(previewRequest),
            "my-api-key", HttpMethod.POST
        )
        assertEquals(HttpStatus.OK.value(), rsp["status"])

        val preview = mapper.readValue("${rsp["body"]}", Preview::class.java)
        val table = preview.table
        val resource = javaClass.classLoader.getResource("test.json")!!

        Assertions.assertNull(table)
        Assertions.assertEquals(resource
            .readText(Charsets.UTF_8), preview.plain?.value)
        Assertions.assertEquals("text/plain", preview.plain?.contentType)
    }

    @Test
    fun `Bad request`() {
        val previewRequest = PreviewRequest("http://localhost:5000/download-link-does-not-exist", 5)
        val rsp = authorizedRequest(
            "/preview", port, mapper.writeValueAsString(previewRequest),
            "my-api-key", HttpMethod.POST
        )
        assertEquals(HttpStatus.BAD_REQUEST.value(), rsp["status"])
    }
}
