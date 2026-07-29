package no.fdk.dataset.preview.controller

import no.fdk.dataset.preview.metrics.PreviewMetrics
import no.fdk.dataset.preview.model.ErrorType
import no.fdk.dataset.preview.model.PreviewRequest
import no.fdk.dataset.preview.service.PreviewException
import no.fdk.dataset.preview.service.PreviewService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/preview")
class PreviewController(
    private val previewService: PreviewService,
) {
    @GetMapping()
    fun preview(): ResponseEntity<Any> {
        PreviewMetrics.recordRequestSuccess(method = "GET", path = "/preview")
        return ResponseEntity.ok().build()
    }

    @PostMapping(consumes = ["application/json"])
    fun preview(
        @RequestBody previewRequest: PreviewRequest,
    ): ResponseEntity<Any> =
        try {
            val preview =
                previewService.readAndParseResource(previewRequest.url, previewRequest.rows)

            PreviewMetrics.recordRequestSuccess(method = "POST", path = "/preview")
            ResponseEntity.ok(preview)
        } catch (e: PreviewException) {
            PreviewMetrics.recordRequestFailure(method = "POST", path = "/preview", errorType = e.errorType)
            throw e
        } catch (e: Exception) {
            PreviewMetrics.recordRequestFailure(
                method = "POST",
                path = "/preview",
                errorType = ErrorType.INTERNAL_ERROR,
            )
            throw e
        }
}
