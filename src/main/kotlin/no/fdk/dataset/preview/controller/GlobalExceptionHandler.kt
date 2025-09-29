package no.fdk.dataset.preview.controller

import no.fdk.dataset.preview.model.ErrorResponse
import no.fdk.dataset.preview.model.ErrorType
import no.fdk.dataset.preview.service.*
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
import java.util.*

/**
 * Global exception handler to provide consistent error responses and prevent information leakage
 */
@ControllerAdvice
class GlobalExceptionHandler {

    private val logger: Logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(PreviewException::class)
    fun handlePreviewException(ex: PreviewException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Preview processing failed: ${ex.message}", ex)
        
        val errorType = when {
            ex.message?.contains("timeout", ignoreCase = true) == true -> ErrorType.PROCESSING_TIMEOUT
            ex.message?.contains("too large", ignoreCase = true) == true -> ErrorType.FILE_TOO_LARGE
            ex.message?.contains("invalid content type", ignoreCase = true) == true -> ErrorType.UNSUPPORTED_FORMAT
            ex.message?.contains("parse", ignoreCase = true) == true -> ErrorType.PARSE_ERROR
            else -> ErrorType.INTERNAL_ERROR
        }
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = errorType.code,
                message = errorType.message,
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(DownloadUrlException::class)
    fun handleDownloadUrlException(ex: DownloadUrlException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Download failed: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = ErrorType.DOWNLOAD_FAILED.code,
                message = ErrorType.DOWNLOAD_FAILED.message,
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(DownloadException::class)
    fun handleDownloadException(ex: DownloadException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Download error: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = ErrorType.DOWNLOAD_FAILED.code,
                message = ErrorType.DOWNLOAD_FAILED.message,
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(UrlException::class)
    fun handleUrlException(ex: UrlException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("URL validation failed: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = ErrorType.SECURITY_VIOLATION.code,
                message = ErrorType.SECURITY_VIOLATION.message,
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(URISyntaxException::class)
    fun handleURISyntaxException(ex: URISyntaxException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid URI syntax: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = ErrorType.INVALID_URL.code,
                message = ErrorType.INVALID_URL.message,
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationException(ex: MethodArgumentNotValidException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Validation failed: ${ex.message}", ex)
        
        val errors = ex.bindingResult.allErrors.map { error ->
            when (error) {
                is FieldError -> "${error.field}: ${error.defaultMessage}"
                else -> error.defaultMessage
            }
        }.joinToString(", ")
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = ErrorType.INVALID_REQUEST.code,
                message = "Validation failed: $errors",
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun handleHttpMessageNotReadableException(ex: HttpMessageNotReadableException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Invalid request body: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = ErrorType.INVALID_REQUEST.code,
                message = ErrorType.INVALID_REQUEST.message,
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(MissingServletRequestParameterException::class)
    fun handleMissingParameterException(ex: MissingServletRequestParameterException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Missing request parameter: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = ErrorType.INVALID_REQUEST.code,
                message = "Missing required parameter: ${ex.parameterName}",
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatchException(ex: MethodArgumentTypeMismatchException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Type mismatch: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(
                error = ErrorType.INVALID_REQUEST.code,
                message = "Invalid parameter type for: ${ex.name}",
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException::class)
    fun handleMethodNotSupportedException(ex: HttpRequestMethodNotSupportedException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("Method not supported: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
            .body(ErrorResponse(
                error = "METHOD_NOT_ALLOWED",
                message = "HTTP method '${ex.method}' is not supported for this endpoint",
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(NoHandlerFoundException::class)
    fun handleNoHandlerFoundException(ex: NoHandlerFoundException, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.warn("No handler found: ${ex.message}", ex)
        
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(
                error = "NOT_FOUND",
                message = "The requested resource was not found",
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(ex: Exception, request: jakarta.servlet.http.HttpServletRequest): ResponseEntity<ErrorResponse> {
        logger.error("Unexpected error occurred", ex)
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ErrorResponse(
                error = ErrorType.INTERNAL_ERROR.code,
                message = ErrorType.INTERNAL_ERROR.message,
                path = request.requestURI,
                requestId = generateRequestId()
            ))
    }

    private fun generateRequestId(): String {
        return UUID.randomUUID().toString().substring(0, 8)
    }
}
