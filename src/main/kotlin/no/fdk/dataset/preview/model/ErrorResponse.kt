package no.fdk.dataset.preview.model

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * Standardized error response to prevent information leakage
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ErrorResponse(
    val error: String,
    val message: String,
    val timestamp: String = Instant.now().toString(),
    val path: String? = null,
    val requestId: String? = null
)

/**
 * Error types that can be safely exposed to clients
 */
enum class ErrorType(val code: String, val message: String) {
    INVALID_URL("INVALID_URL", "The provided URL is invalid or not accessible"),
    FILE_TOO_LARGE("FILE_TOO_LARGE", "The file is too large to process"),
    UNSUPPORTED_FORMAT("UNSUPPORTED_FORMAT", "The file format is not supported"),
    PROCESSING_TIMEOUT("PROCESSING_TIMEOUT", "File processing timed out"),
    DOWNLOAD_FAILED("DOWNLOAD_FAILED", "Failed to download the file"),
    PARSE_ERROR("PARSE_ERROR", "Failed to parse the file content"),
    SECURITY_VIOLATION("SECURITY_VIOLATION", "Security policy violation"),
    INTERNAL_ERROR("INTERNAL_ERROR", "An internal error occurred"),
    INVALID_REQUEST("INVALID_REQUEST", "Invalid request parameters")
}

