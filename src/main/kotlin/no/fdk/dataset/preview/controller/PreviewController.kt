package no.fdk.dataset.preview.controller

import no.fdk.dataset.preview.model.PreviewRequest
import no.fdk.dataset.preview.service.DownloadUrlException
import no.fdk.dataset.preview.service.PreviewException
import no.fdk.dataset.preview.service.PreviewService
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

private val LOGGER: Logger = LoggerFactory.getLogger(PreviewController::class.java)

@RestController
@RequestMapping("/preview")
class PreviewController(
    private val previewService: PreviewService
) {
    @GetMapping()
    fun preview(): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok().build()
        } catch (e: PreviewException) {
            LOGGER.warn("Bad request - Failed to create preview", e)
            ResponseEntity<Any>(HttpStatus.BAD_REQUEST)
        }
    }

    @PostMapping(consumes = ["application/json"])
    fun preview(@RequestBody previewRequest: PreviewRequest): ResponseEntity<Any> {
        return try {
            val preview = previewService.readAndParseResource(previewRequest.url, previewRequest.rows)
            ResponseEntity.ok(preview)
        } catch (e: PreviewException) {
            LOGGER.warn("Bad request - Failed to create preview", e)
            ResponseEntity<Any>(HttpStatus.BAD_REQUEST)
        }
    }

    @ExceptionHandler
    fun handleDownloadUrldException(ex: DownloadUrlException): ResponseEntity<String> {
        LOGGER.warn("Bad request - Failed to download remote file", ex)
        return ResponseEntity.badRequest().build()
    }
}
