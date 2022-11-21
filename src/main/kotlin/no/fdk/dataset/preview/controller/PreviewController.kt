package no.fdk.dataset.preview.controller

import no.fdk.dataset.preview.model.PreviewRequest
import no.fdk.dataset.preview.service.PreviewException
import no.fdk.dataset.preview.service.PreviewService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/preview")
class PreviewController(
    private val previewService: PreviewService
) {
    @GetMapping()
    fun preview(): ResponseEntity<Any> {
        return try {
            ResponseEntity.ok().build()
        } catch(e: PreviewException) {
            ResponseEntity<Any>(e.message, HttpStatus.BAD_REQUEST)
        }
    }
    @PostMapping(consumes = ["application/json"])
    fun preview(@RequestBody previewRequest: PreviewRequest): ResponseEntity<Any> {
        return try {
            val preview = previewService.readAndParseResource(previewRequest.url, previewRequest.rows)
            ResponseEntity.ok(preview)
        } catch(e: PreviewException) {
            ResponseEntity<Any>(e.message, HttpStatus.BAD_REQUEST)
        }
    }
}
