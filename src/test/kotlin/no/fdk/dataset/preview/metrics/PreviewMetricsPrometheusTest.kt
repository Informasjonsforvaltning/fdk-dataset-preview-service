package no.fdk.dataset.preview.metrics

import io.micrometer.core.instrument.Metrics
import io.micrometer.prometheusmetrics.PrometheusConfig
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import no.fdk.dataset.preview.model.ErrorType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.milliseconds

@Tag("unit")
class PreviewMetricsPrometheusTest {
    private lateinit var prometheusRegistry: PrometheusMeterRegistry

    @BeforeEach
    fun setup() {
        prometheusRegistry = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)
        Metrics.globalRegistry.add(prometheusRegistry)
    }

    @AfterEach
    fun teardown() {
        Metrics.globalRegistry.remove(prometheusRegistry)
        prometheusRegistry.close()
    }

    @Test
    fun `preview_count success and error both appear in Prometheus scrape`() {
        PreviewMetrics.recordSuccess("csv", "https://example.com/u1", 100.milliseconds)
        PreviewMetrics.recordFailure(ErrorType.PARSE_ERROR, "https://example.com/u2")

        val scrape = prometheusRegistry.scrape()

        assertTrue(scrape.contains("preview_count_total{error_type=\"none\",format=\"csv\""))
        assertTrue(scrape.contains("preview_count_total{error_type=\"PARSE_ERROR\",format=\"none\""))
    }

    @Test
    fun `preview_request_count success and error both appear in Prometheus scrape`() {
        PreviewMetrics.recordRequestSuccess(method = "POST", path = "/preview")
        PreviewMetrics.recordRequestFailure(
            method = "POST",
            path = "/preview",
            errorType = ErrorType.PARSE_ERROR,
        )

        val scrape = prometheusRegistry.scrape()

        assertTrue(scrape.contains("preview_request_count_total{error_type=\"none\""))
        assertTrue(scrape.contains("preview_request_count_total{error_type=\"PARSE_ERROR\""))
    }
}
