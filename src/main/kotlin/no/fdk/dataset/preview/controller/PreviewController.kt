package no.fdk.dataset.preview.controller

import no.fdk.dataset.preview.model.PreviewRequest
import no.fdk.dataset.preview.service.PreviewService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/preview")
class PreviewController(
    private val previewService: PreviewService
) {
    @GetMapping()
    fun preview(): ResponseEntity<Any> {
        return ResponseEntity.ok().build()
    }

    @PostMapping(consumes = ["application/json"])
    fun preview(@RequestBody previewRequest: PreviewRequest): ResponseEntity<Any> {
        val preview = previewService.readAndParseResource(previewRequest.url, previewRequest.rows)
        return ResponseEntity.ok(preview)
    }
}
