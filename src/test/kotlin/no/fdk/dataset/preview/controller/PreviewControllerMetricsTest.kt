package no.fdk.dataset.preview.controller

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.fdk.dataset.preview.model.ErrorType
import no.fdk.dataset.preview.model.Preview
import no.fdk.dataset.preview.model.PreviewRequest
import no.fdk.dataset.preview.service.PreviewException
import no.fdk.dataset.preview.service.PreviewService
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import kotlin.test.assertFailsWith

@Tag("unit")
class PreviewControllerMetricsTest {
    private lateinit var meterRegistry: SimpleMeterRegistry

    @BeforeEach
    fun setup() {
        meterRegistry = SimpleMeterRegistry()
        Metrics.globalRegistry.add(meterRegistry)
    }

    @AfterEach
    fun teardown() {
        Metrics.globalRegistry.remove(meterRegistry)
        meterRegistry.close()
    }

    @Test
    fun `POST preview increments success counter`() {
        val previewService: PreviewService = mock()
        val controller = PreviewController(previewService)

        val url = "https://example.com/test.csv"
        val rows = 5
        val preview = Preview(table = null, plain = null)

        whenever(previewService.readAndParseResource(url, rows)).thenReturn(preview)

        val response = controller.preview(PreviewRequest(url, rows))
        assertEquals(200, response.statusCode.value())

        val counter =
            meterRegistry
                .find("preview_request_count")
                .tag("method", "POST")
                .tag("path", "/preview")
                .tag("status", "success")
                .tag("error_type", "none")
                .counter()

        assertEquals(1.0, counter?.count())
    }

    @Test
    fun `POST preview increments error counter for PreviewException`() {
        val previewService: PreviewService = mock()
        val controller = PreviewController(previewService)

        val url = "https://example.com/test.csv"
        val rows = 5

        whenever(previewService.readAndParseResource(url, rows))
            .thenAnswer { throw PreviewException("bad file", ErrorType.PARSE_ERROR) }

        assertFailsWith<PreviewException> { controller.preview(PreviewRequest(url, rows)) }

        val counter =
            meterRegistry
                .find("preview_request_count")
                .tag("method", "POST")
                .tag("path", "/preview")
                .tag("status", "error")
                .tag("error_type", ErrorType.PARSE_ERROR.code)
                .counter()

        assertEquals(1.0, counter?.count())
    }

    @Test
    fun `GET preview increments success counter`() {
        val previewService: PreviewService = mock()
        val controller = PreviewController(previewService)

        val response = controller.preview()
        assertEquals(200, response.statusCode.value())

        val counter =
            meterRegistry
                .find("preview_request_count")
                .tag("method", "GET")
                .tag("path", "/preview")
                .tag("status", "success")
                .tag("error_type", "none")
                .counter()

        assertEquals(1.0, counter?.count())
    }
}
