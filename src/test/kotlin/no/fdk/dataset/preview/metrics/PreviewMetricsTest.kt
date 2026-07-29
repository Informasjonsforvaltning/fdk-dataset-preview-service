package no.fdk.dataset.preview.metrics

import io.micrometer.core.instrument.Metrics
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.fdk.dataset.preview.model.ErrorType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@Tag("unit")
class PreviewMetricsTest {
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
    fun `recordSuccess increments counter with correct tags`() {
        val url = "https://example.com/data.csv"

        PreviewMetrics.recordSuccess("csv", url, 150.milliseconds)

        val counter =
            meterRegistry
                .find("preview_count")
                .tag("status", "success")
                .tag("format", "csv")
                .tag("resource_url", url)
                .tag("error_type", "none")
                .counter()

        assertEquals(1.0, counter?.count())

        val timer =
            meterRegistry
                .find("preview_duration")
                .tag("format", "csv")
                .tag("resource_url", url)
                .timer()

        assertNotNull(timer)
        assertEquals(1, timer?.count())
        assertEquals(0.15, timer?.totalTime(java.util.concurrent.TimeUnit.SECONDS))
    }

    @Test
    fun `recordSuccess increments for each format`() {
        val url = "https://example.com/data"

        PreviewMetrics.recordSuccess("csv", url, 100.milliseconds)
        PreviewMetrics.recordSuccess("xlsx", url, 200.milliseconds)
        PreviewMetrics.recordSuccess("zip", url, 300.milliseconds)

        val csvCounter =
            meterRegistry
                .find("preview_count")
                .tag("status", "success")
                .tag("format", "csv")
                .tag("resource_url", url)
                .tag("error_type", "none")
                .counter()
        val xlsxCounter =
            meterRegistry
                .find("preview_count")
                .tag("status", "success")
                .tag("format", "xlsx")
                .tag("resource_url", url)
                .tag("error_type", "none")
                .counter()
        val zipCounter =
            meterRegistry
                .find("preview_count")
                .tag("status", "success")
                .tag("format", "zip")
                .tag("resource_url", url)
                .tag("error_type", "none")
                .counter()

        assertEquals(1.0, csvCounter?.count())
        assertEquals(1.0, xlsxCounter?.count())
        assertEquals(1.0, zipCounter?.count())
    }

    @Test
    fun `recordFailure increments counter with correct tags`() {
        val url = "https://example.com/data.csv"

        PreviewMetrics.recordFailure(ErrorType.DOWNLOAD_FAILED, url)

        val counter =
            meterRegistry
                .find("preview_count")
                .tag("status", "error")
                .tag("format", "none")
                .tag("error_type", "DOWNLOAD_FAILED")
                .tag("resource_url", url)
                .counter()

        assertEquals(1.0, counter?.count())
    }

    @Test
    fun `recordFailure increments for each error type`() {
        val url = "https://example.com/data.csv"

        PreviewMetrics.recordFailure(ErrorType.DOWNLOAD_FAILED, url)
        PreviewMetrics.recordFailure(ErrorType.UNSUPPORTED_FORMAT, url)
        PreviewMetrics.recordFailure(ErrorType.FILE_TOO_LARGE, url)

        val downloadCounter =
            meterRegistry
                .find("preview_count")
                .tag("status", "error")
                .tag("format", "none")
                .tag("error_type", "DOWNLOAD_FAILED")
                .tag("resource_url", url)
                .counter()
        val formatCounter =
            meterRegistry
                .find("preview_count")
                .tag("status", "error")
                .tag("format", "none")
                .tag("error_type", "UNSUPPORTED_FORMAT")
                .tag("resource_url", url)
                .counter()
        val sizeCounter =
            meterRegistry
                .find("preview_count")
                .tag("status", "error")
                .tag("format", "none")
                .tag("error_type", "FILE_TOO_LARGE")
                .tag("resource_url", url)
                .counter()

        assertEquals(1.0, downloadCounter?.count())
        assertEquals(1.0, formatCounter?.count())
        assertEquals(1.0, sizeCounter?.count())
    }

    @Test
    fun `recordFailure accumulates for same error type and url`() {
        val url = "https://example.com/data.csv"

        PreviewMetrics.recordFailure(ErrorType.DOWNLOAD_FAILED, url)
        PreviewMetrics.recordFailure(ErrorType.DOWNLOAD_FAILED, url)

        val counter =
            meterRegistry
                .find("preview_count")
                .tag("status", "error")
                .tag("format", "none")
                .tag("error_type", "DOWNLOAD_FAILED")
                .tag("resource_url", url)
                .counter()

        assertEquals(2.0, counter?.count())
    }

    @Test
    fun `recordFileSize records distribution summary`() {
        PreviewMetrics.recordFileSize(1234L)

        val summary =
            meterRegistry
                .find("preview_file_size_bytes")
                .summary()

        assertNotNull(summary)
        assertEquals(1L, summary?.count())
        assertEquals(1234.0, summary?.totalAmount())
    }

    @Test
    fun `recordRequestSuccess increments counter with correct tags`() {
        PreviewMetrics.recordRequestSuccess(method = "POST", path = "/preview")

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
    fun `recordRequestFailure increments counter with correct tags`() {
        PreviewMetrics.recordRequestFailure(
            method = "POST",
            path = "/preview",
            errorType = ErrorType.PARSE_ERROR,
        )

        val counter =
            meterRegistry
                .find("preview_request_count")
                .tag("method", "POST")
                .tag("path", "/preview")
                .tag("status", "error")
                .tag("error_type", "PARSE_ERROR")
                .counter()

        assertEquals(1.0, counter?.count())
    }
}
