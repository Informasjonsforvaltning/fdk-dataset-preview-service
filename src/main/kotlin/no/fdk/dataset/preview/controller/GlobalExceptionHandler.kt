package no.fdk.dataset.preview.controller

import no.fdk.dataset.preview.model.ErrorResponse
import no.fdk.dataset.preview.model.ErrorType
import no.fdk.dataset.preview.service.DownloadException
import no.fdk.dataset.preview.service.DownloadUrlException
import no.fdk.dataset.preview.service.PreviewException
import no.fdk.dataset.preview.service.UrlException
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.HttpRequestMethodNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.MissingServletRequestParameterException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.NoHandlerFoundException
import java.net.URISyntaxException
import java.util.UUID

/**
 * Global exception handler to provide consistent error responses and prevent information leakage
 */
@ControllerAdvice
class GlobalExceptionHandler {
    private val logger: Logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(PreviewException::class)
    fun handlePreviewException(ex: PreviewException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Preview processing failed: ${ex.message}", ex)
        return errorResponse(HttpStatus.BAD_REQUEST, ex.errorType, request)
    }

    @ExceptionHandler(DownloadUrlException::class)
    fun handleDownloadUrlException(
        ex: DownloadUrlException,
        request: jakarta.servlet.http.HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Download failed: ${ex.message}", ex)

        return errorResponse(HttpStatus.BAD_REQUEST, ErrorType.DOWNLOAD_FAILED, request)
    }

    @ExceptionHandler(DownloadException::class)
    fun handleDownloadException(ex: DownloadException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Download error: ${ex.message}", ex)

        return errorResponse(HttpStatus.BAD_REQUEST, ErrorType.DOWNLOAD_FAILED, request)
    }

    @ExceptionHandler(UrlException::class)
    fun handleUrlException(ex: UrlException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("URL validation failed: ${ex.message}", ex)

        return errorResponse(HttpStatus.BAD_REQUEST, ErrorType.SECURITY_VIOLATION, request)
    }

    @ExceptionHandler(URISyntaxException::class)
    fun handleURISyntaxException(ex: URISyntaxException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid URI syntax: ${ex.message}", ex)

        return errorResponse(HttpStatus.BAD_REQUEST, ErrorType.INVALID_URL, request)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(
        ex: MethodArgumentNotValidException,
        request: jakarta.servlet.http.HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Validation failed: ${ex.message}", ex)

        val errors =
            ex.bindingResult.allErrors
                .map { error ->
                    when (error) {
                        is FieldError -> "${error.field}: ${error.defaultMessage}"
                        else -> error.defaultMessage
                    }
                }.joinToString(", ")

        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            errorType = ErrorType.INVALID_REQUEST,
            request = request,
            message = "Validation failed: $errors",
        )
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(
        ex: HttpMessageNotReadableException,
        request: jakarta.servlet.http.HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid request body: ${ex.message}", ex)

        return errorResponse(HttpStatus.BAD_REQUEST, ErrorType.INVALID_REQUEST, request)
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameterException(
        ex: MissingServletRequestParameterException,
        request: jakarta.servlet.http.HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Missing request parameter: ${ex.message}", ex)

        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            errorType = ErrorType.INVALID_REQUEST,
            request = request,
            message = "Missing required parameter: ${ex.parameterName}",
        )
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(
        ex: MethodArgumentTypeMismatchException,
        request: jakarta.servlet.http.HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Type mismatch: ${ex.message}", ex)

        return errorResponse(
            status = HttpStatus.BAD_REQUEST,
            errorType = ErrorType.INVALID_REQUEST,
            request = request,
            message = "Invalid parameter type for: ${ex.name}",
        )
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupportedException(
        ex: HttpRequestMethodNotSupportedException,
        request: jakarta.servlet.http.HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("Method not supported: ${ex.message}", ex)

        return errorResponse(
            status = HttpStatus.METHOD_NOT_ALLOWED,
            error = "METHOD_NOT_ALLOWED",
            message = "HTTP method '${ex.method}' is not supported for this endpoint",
            request = request,
        )
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFoundException(
        ex: NoHandlerFoundException,
        request: jakarta.servlet.http.HttpServletRequest,
    ): ResponseEntity<ErrorResponse> {
        logger.warn("No handler found: ${ex.message}", ex)

        return errorResponse(
            status = HttpStatus.NOT_FOUND,
            error = "NOT_FOUND",
            message = "The requested resource was not found",
            request = request,
        )
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", ex)

        return errorResponse(HttpStatus.INTERNAL_SERVER_ERROR, ErrorType.INTERNAL_ERROR, request)
    }

    private fun errorResponse(
        status: HttpStatus,
        errorType: ErrorType,
        request: jakarta.servlet.http.HttpServletRequest,
        message: String = errorType.message,
    ): ResponseEntity<ErrorResponse> = errorResponse(status, errorType.code, message, request)

    private fun errorResponse(
        status: HttpStatus,
        error: String,
        message: String,
        request: jakarta.servlet.http.HttpServletRequest,
    ): ResponseEntity<ErrorResponse> = ResponseEntity
        .status(status)
        .body(
            ErrorResponse(
                error = error,
                message = message,
                path = request.requestURI,
                requestId = generateRequestId(),
            ),
        )

    private fun generateRequestId(): String = UUID.randomUUID().toString().substring(0, 8)
}
