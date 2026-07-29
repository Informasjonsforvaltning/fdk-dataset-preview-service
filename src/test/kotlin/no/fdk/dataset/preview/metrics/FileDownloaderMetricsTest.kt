package no.fdk.dataset.preview.metrics

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.fdk.dataset.preview.service.DownloadException
import no.fdk.dataset.preview.service.FileDownloader
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.TestPropertySource

@Tag("unit")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
)
@TestPropertySource(
    properties = [
        "application.allowLocalhost=true",
    ],
)
class FileDownloaderMetricsTest {
    @Autowired
    private lateinit var fileDownloader: FileDownloader

    private lateinit var mockServer: MockWebServer
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setup() {
        mockServer = MockWebServer()
        mockServer.start()

        meterRegistry = SimpleMeterRegistry()
        Metrics.globalRegistry.add(meterRegistry)
    }

    @AfterEach
    fun teardown() {
        mockServer.shutdown()
        Metrics.globalRegistry.remove(meterRegistry)
        meterRegistry.close()
    }

    @Test
    fun `download success records download metrics`() {
        val csvBody = "name,age\nJohn,30\n"
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "text/csv")
                .setBody(csvBody),
        )

        val url = mockServer.url("/test.csv").toString()
        fileDownloader.download(url) { responseBody -> responseBody.string() }

        val downloadCounter =
            meterRegistry
                .find("preview_download_count")
                .tag("status", "success")
                .tag("status_code", "200")
                .tag("resource_url", url)
                .counter()

        assertEquals(1.0, downloadCounter?.count())

        val bytesCounter =
            meterRegistry
                .find("preview_download_bytes_total")
                .tag("status_code", "200")
                .tag("resource_url", url)
                .counter()

        val notNullBytesCounter = requireNotNull(bytesCounter)
        assertEquals(
            csvBody.toByteArray(Charsets.UTF_8).size.toDouble(),
            notNullBytesCounter.count(),
        )

        val timer =
            meterRegistry
                .find("preview_download_duration")
                .tag("status_code", "200")
                .tag("resource_url", url)
                .timer()

        assertNotNull(timer)
        assertEquals(1, timer?.count())
    }

    @Test
    fun `download failure records error metrics once for non-2xx response`() {
        mockServer.enqueue(MockResponse().setResponseCode(404))

        val url = mockServer.url("/missing.csv").toString()

        assertThrows(DownloadException::class.java) {
            fileDownloader.download(url) { responseBody -> responseBody.string() }
        }

        val downloadCounter =
            meterRegistry
                .find("preview_download_count")
                .tag("status", "error")
                .tag("status_code", "404")
                .tag("resource_url", url)
                .counter()

        assertEquals(1.0, downloadCounter?.count())
    }
}
