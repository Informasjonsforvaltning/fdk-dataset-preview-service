package no.fdk.dataset.preview.service

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.fdk.dataset.preview.model.Preview
import no.fdk.dataset.preview.model.PreviewRequest
import no.fdk.dataset.preview.model.Table
import no.fdk.dataset.preview.service.utils.ApiTestContext
import no.fdk.dataset.preview.service.utils.authorizedRequest
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ContextConfiguration
import java.nio.charset.Charset
import kotlin.test.assertEquals

private val mapper = jacksonObjectMapper()

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    properties = ["spring.profiles.active=contract-test", "application.apiKey=my-api-key"],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = [ApiTestContext.Initializer::class])
@Tag("contract")
class PreviewContractTest : ApiTestContext() {

    @Test
    fun `Unauthorized when api token is not included`() {
        val previewRequest = PreviewRequest("http://localhost:5000/download", 5)
        val rsp = authorizedRequest("/preview", port, mapper.writeValueAsString(previewRequest),
            null, HttpMethod.POST)

        assertEquals(HttpStatus.FORBIDDEN.value(), rsp["status"])
    }

    @Test
    fun ok() {
        val previewRequest = PreviewRequest("http://localhost:5000/download", 5)
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
    fun `Bad request`() {
        val previewRequest = PreviewRequest("http://localhost:5000/download-link-does-not-exist", 5)
        val rsp = authorizedRequest(
            "/preview", port, mapper.writeValueAsString(previewRequest),
            "my-api-key", HttpMethod.POST
        )
        assertEquals(HttpStatus.BAD_REQUEST.value(), rsp["status"])
    }
}
