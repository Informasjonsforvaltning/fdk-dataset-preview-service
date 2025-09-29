package no.fdk.dataset.preview.controller

import no.fdk.dataset.preview.model.ErrorResponse
import no.fdk.dataset.preview.model.ErrorType
import no.fdk.dataset.preview.service.*
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

@Tag("unit")
class GlobalExceptionHandlerTest {

    private val exceptionHandler = GlobalExceptionHandler()
    private val request = MockHttpServletRequest()

    @Test
    fun `handlePreviewException should return standardized error response`() {
        val exception = PreviewException("File is too large to process")
        val response = exceptionHandler.handlePreviewException(exception, request)
        
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val errorResponse = response.body as ErrorResponse
        assertEquals(ErrorType.FILE_TOO_LARGE.code, errorResponse.error)
        assertEquals(ErrorType.FILE_TOO_LARGE.message, errorResponse.message)
        assertNotNull(errorResponse.timestamp)
        assertNotNull(errorResponse.requestId)
    }

    @Test
    fun `handleDownloadUrlException should return standardized error response`() {
        val exception = DownloadUrlException("Invalid URL format")
        val response = exceptionHandler.handleDownloadUrlException(exception, request)
        
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val errorResponse = response.body as ErrorResponse
        assertEquals(ErrorType.DOWNLOAD_FAILED.code, errorResponse.error)
        assertEquals(ErrorType.DOWNLOAD_FAILED.message, errorResponse.message)
    }

    @Test
    fun `handleDownloadException should return standardized error response`() {
        val exception = DownloadException("Download failed")
        val response = exceptionHandler.handleDownloadException(exception, request)
        
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val errorResponse = response.body as ErrorResponse
        assertEquals(ErrorType.DOWNLOAD_FAILED.code, errorResponse.error)
        assertEquals(ErrorType.DOWNLOAD_FAILED.message, errorResponse.message)
    }

    @Test
    fun `handleUrlException should return security violation error`() {
        val exception = UrlException("Unsafe URL scheme not allowed")
        val response = exceptionHandler.handleUrlException(exception, request)
        
        assertEquals(HttpStatus.BAD_REQUEST, response.statusCode)
        val errorResponse = response.body as ErrorResponse
        assertEquals(ErrorType.SECURITY_VIOLATION.code, errorResponse.error)
        assertEquals(ErrorType.SECURITY_VIOLATION.message, errorResponse.message)
    }

    @Test
    fun `handleGenericException should return internal error`() {
        val exception = RuntimeException("Unexpected error")
        val response = exceptionHandler.handleGenericException(exception, request)
        
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.statusCode)
        val errorResponse = response.body as ErrorResponse
        assertEquals(ErrorType.INTERNAL_ERROR.code, errorResponse.error)
        assertEquals(ErrorType.INTERNAL_ERROR.message, errorResponse.message)
    }

    @Test
    fun `error response should not contain sensitive information`() {
        val exception = PreviewException("File /sensitive/path/file.xlsx is too large (500MB, max: 10MB)")
        val response = exceptionHandler.handlePreviewException(exception, request)
        
        val errorResponse = response.body as ErrorResponse
        // Should not contain file paths or specific sizes
        assertEquals(ErrorType.FILE_TOO_LARGE.message, errorResponse.message)
        assertEquals(false, errorResponse.message.contains("/sensitive/path"))
        assertEquals(false, errorResponse.message.contains("500MB"))
        assertEquals(false, errorResponse.message.contains("10MB"))
    }
}

