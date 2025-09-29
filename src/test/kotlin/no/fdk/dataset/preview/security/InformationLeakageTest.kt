package no.fdk.dataset.preview.security

import no.fdk.dataset.preview.service.FileDownloader
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@SpringBootTest
@TestPropertySource(properties = [
    "application.security.sanitizeHeaders=true",
    "application.security.userAgent=Test-Agent/1.0",
    "application.allowLocalhost=true"
])
class InformationLeakageTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var fileDownloader: FileDownloader

    @Value("\${application.security.userAgent}")
    private lateinit var expectedUserAgent: String

    @BeforeEach
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        
        // Create FileDownloader with test configuration
        fileDownloader = FileDownloader()
    }

    @AfterEach
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `should send minimal headers and avoid information leakage`() {
        // Setup mock response
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/csv")
            .setBody("name,age\nJohn,30\nJane,25"))

        // Make request
        val url = mockServer.url("/test.csv").toString()
        fileDownloader.download(url) { responseBody ->
            responseBody.string()
        }

        // Verify request was made
        val request: RecordedRequest = mockServer.takeRequest()
        assertNotNull(request)

        // Verify minimal headers are sent
        val userAgent = request.getHeader("User-Agent")
        assertEquals(expectedUserAgent, userAgent)

        // Verify only necessary headers are present
        val accept = request.getHeader("Accept")
        assertTrue(accept?.contains("text/csv") == true)
        assertTrue(accept?.contains("application/zip") == true)

        // Verify information-leaking headers are NOT present
        assertFalse(request.headers.names().contains("Accept-Encoding"))
        assertFalse(request.headers.names().contains("Accept-Language"))
        assertFalse(request.headers.names().contains("DNT"))
        assertFalse(request.headers.names().contains("Upgrade-Insecure-Requests"))
        assertFalse(request.headers.names().contains("Sec-Fetch-Dest"))
        assertFalse(request.headers.names().contains("Sec-Fetch-Mode"))
        assertFalse(request.headers.names().contains("Sec-Fetch-Site"))
        assertFalse(request.headers.names().contains("Sec-Fetch-User"))
        assertFalse(request.headers.names().contains("Sec-Ch-Ua"))
        assertFalse(request.headers.names().contains("Sec-Ch-Ua-Mobile"))
        assertFalse(request.headers.names().contains("Sec-Ch-Ua-Platform"))
        assertFalse(request.headers.names().contains("X-Forwarded-For"))
        assertFalse(request.headers.names().contains("X-Real-IP"))
        assertFalse(request.headers.names().contains("X-Forwarded-Proto"))
        assertFalse(request.headers.names().contains("X-Forwarded-Host"))
        assertFalse(request.headers.names().contains("X-Forwarded-Port"))
        assertFalse(request.headers.names().contains("Via"))
        assertFalse(request.headers.names().contains("X-Request-ID"))
        assertFalse(request.headers.names().contains("X-Correlation-ID"))

        // Verify security headers are present
        assertTrue(request.headers.names().contains("Connection"))
        assertTrue(request.headers.names().contains("Cache-Control"))
    }

    @Test
    fun `should not reveal service details in User-Agent`() {
        mockServer.enqueue(MockResponse()
            .setResponseCode(200)
            .setHeader("Content-Type", "text/csv")
            .setBody("test,data"))

        val url = mockServer.url("/test.csv").toString()
        fileDownloader.download(url) { responseBody ->
            responseBody.string()
        }

        val request: RecordedRequest = mockServer.takeRequest()
        val userAgent = request.getHeader("User-Agent")

        // Should not contain service-specific information
        assertFalse(userAgent?.contains("FDK") == true)
        assertFalse(userAgent?.contains("Dataset") == true)
        assertFalse(userAgent?.contains("Preview") == true)
        assertFalse(userAgent?.contains("Service") == true)
        assertFalse(userAgent?.contains("Spring") == true)
        assertFalse(userAgent?.contains("Boot") == true)
        assertFalse(userAgent?.contains("Kotlin") == true)
    }
}



