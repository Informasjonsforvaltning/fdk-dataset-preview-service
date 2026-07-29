package no.fdk.dataset.preview.metrics

import io.micrometer.core.instrument.DistributionSummary
import io.micrometer.core.instrument.Metrics
import no.fdk.dataset.preview.model.ErrorType
import kotlin.time.Duration
import kotlin.time.toJavaDuration

object PreviewMetrics {
    private const val NONE = "none"

    fun recordSuccess(
        format: String,
        resourceUrl: String,
        duration: Duration,
    ) {
        Metrics
            .counter(
                "preview_count",
                "status",
                "success",
                "format",
                format,
                "resource_url",
                resourceUrl,
                "error_type",
                NONE,
            ).increment()

        Metrics
            .timer(
                "preview_duration",
                "format",
                format,
                "resource_url",
                resourceUrl,
            ).record(duration.toJavaDuration())
    }

    fun recordFailure(
        errorType: ErrorType,
        resourceUrl: String,
    ) {
        Metrics
            .counter(
                "preview_count",
                "status",
                "error",
                "format",
                NONE,
                "resource_url",
                resourceUrl,
                "error_type",
                errorType.code,
            ).increment()
    }

    fun recordDownloadSuccess(
        resourceUrl: String,
        statusCode: Int,
        duration: Duration,
        bytes: Long?,
    ) {
        val statusCodeString = statusCode.toString()

        Metrics
            .counter(
                "preview_download_count",
                "status",
                "success",
                "status_code",
                statusCodeString,
                "resource_url",
                resourceUrl,
            ).increment()

        Metrics
            .timer(
                "preview_download_duration",
                "status_code",
                statusCodeString,
                "resource_url",
                resourceUrl,
            ).record(duration.toJavaDuration())

        if (bytes != null && bytes >= 0) {
            Metrics
                .counter(
                    "preview_download_bytes_total",
                    "status_code",
                    statusCodeString,
                    "resource_url",
                    resourceUrl,
                ).increment(bytes.toDouble())
        }
    }

    fun recordDownloadFailure(
        resourceUrl: String,
        statusCode: Int?,
        duration: Duration,
    ) {
        val statusCodeString = statusCode?.toString() ?: "unknown"

        Metrics
            .counter(
                "preview_download_count",
                "status",
                "error",
                "status_code",
                statusCodeString,
                "resource_url",
                resourceUrl,
            ).increment()

        Metrics
            .timer(
                "preview_download_duration",
                "status_code",
                statusCodeString,
                "resource_url",
                resourceUrl,
            ).record(duration.toJavaDuration())
    }

    fun recordFileSize(bytes: Long) {
        if (bytes < 0) return // content length may be unknown

        DistributionSummary
            .builder("preview_file_size_bytes")
            .register(Metrics.globalRegistry)
            .record(bytes.toDouble())
    }

    fun recordRequestSuccess(
        method: String,
        path: String,
    ) {
        Metrics
            .counter(
                "preview_request_count",
                "method",
                method,
                "path",
                path,
                "status",
                "success",
                "error_type",
                NONE,
            ).increment()
    }

    fun recordRequestFailure(
        method: String,
        path: String,
        errorType: ErrorType,
    ) {
        Metrics
            .counter(
                "preview_request_count",
                "method",
                method,
                "path",
                path,
                "status",
                "error",
                "error_type",
                errorType.code,
            ).increment()
    }
}
