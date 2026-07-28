package no.fdk.dataset.preview.controller

import no.fdk.dataset.preview.model.ErrorResponse
import no.fdk.dataset.preview.model.ErrorType
import no.fdk.dataset.preview.service.DownloadException
import no.fdk.dataset.preview.service.DownloadUrlException
import no.fdk.dataset.preview.service.PreviewException
import no.fdk.dataset.preview.service.UrlException
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tag("unit")
class GlobalExceptionHandlerTest {
    private val exceptionHandler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest()

    @Test
    fun `handlePreviewException should return standardized error response`() {
        val exception = PreviewException("File is too large to process", ErrorType.FILE_TOO_LARGE)
        val response = exceptionHandler.handlePreviewException(exception, request)

        val errorResponse = assertErrorResponse(response, HttpStatus.BAD_REQUEST)
        assertEquals(ErrorType.FILE_TOO_LARGE.code, errorResponse.error)
        assertEquals(ErrorType.FILE_TOO_LARGE.message, errorResponse.message)
        assertNotNull(errorResponse.timestamp)
        assertNotNull(errorResponse.requestId)
    }

    @Test
    fun `handleDownloadUrlException should return standardized error response`() {
        val exception = DownloadUrlException("Invalid URL format")
        val response = exceptionHandler.handleDownloadUrlException(exception, request)

        val errorResponse = assertErrorResponse(response, HttpStatus.BAD_REQUEST)
        assertEquals(ErrorType.DOWNLOAD_FAILED.code, errorResponse.error)
        assertEquals(ErrorType.DOWNLOAD_FAILED.message, errorResponse.message)
    }

    @Test
    fun `handleDownloadException should return standardized error response`() {
        val exception = DownloadException("Download failed")
        val response = exceptionHandler.handleDownloadException(exception, request)

        val errorResponse = assertErrorResponse(response, HttpStatus.BAD_REQUEST)
        assertEquals(ErrorType.DOWNLOAD_FAILED.code, errorResponse.error)
        assertEquals(ErrorType.DOWNLOAD_FAILED.message, errorResponse.message)
    }

    @Test
    fun `handleUrlException should return security violation error`() {
        val exception = UrlException("Unsafe URL scheme not allowed")
        val response = exceptionHandler.handleUrlException(exception, request)

        val errorResponse = assertErrorResponse(response, HttpStatus.BAD_REQUEST)
        assertEquals(ErrorType.SECURITY_VIOLATION.code, errorResponse.error)
        assertEquals(ErrorType.SECURITY_VIOLATION.message, errorResponse.message)
    }

    @Test
    fun `handleGenericException should return internal error`() {
        val exception = RuntimeException("Unexpected error")
        val response = exceptionHandler.handleGenericException(exception, request)

        val errorResponse = assertErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR)
        assertEquals(ErrorType.INTERNAL_ERROR.code, errorResponse.error)
        assertEquals(ErrorType.INTERNAL_ERROR.message, errorResponse.message)
    }

    @Test
    fun `error response should not contain sensitive information`() {
        val exception =
            PreviewException(
                "File /sensitive/path/file.xlsx is too large (500MB, max: 10MB)",
                ErrorType.FILE_TOO_LARGE,
            )
        val response = exceptionHandler.handlePreviewException(exception, request)

        val errorResponse = assertErrorResponse(response, HttpStatus.BAD_REQUEST)
        // Should not contain file paths or specific sizes
        assertEquals(ErrorType.FILE_TOO_LARGE.message, errorResponse.message)
        assertEquals(false, errorResponse.message.contains("/sensitive/path"))
        assertEquals(false, errorResponse.message.contains("500MB"))
        assertEquals(false, errorResponse.message.contains("10MB"))
    }

    private fun assertErrorResponse(
        response: org.springframework.http.ResponseEntity<ErrorResponse>,
        expectedStatus: HttpStatus,
    ): ErrorResponse {
        assertEquals(expectedStatus, response.statusCode)
        return requireNotNull(response.body)
    }

    @Test
    fun `handlePreviewException should use explicit error type instead of message matching`() {
        val exception = PreviewException("Some custom message", ErrorType.PARSE_ERROR)

        val response = exceptionHandler.handlePreviewException(exception, request)

        val errorResponse = assertErrorResponse(response, HttpStatus.BAD_REQUEST)
        assertEquals(ErrorType.PARSE_ERROR.code, errorResponse.error)
        assertEquals(ErrorType.PARSE_ERROR.message, errorResponse.message)
    }
}
