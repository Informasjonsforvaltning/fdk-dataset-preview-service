package no.fdk.dataset.preview.controller

import no.fdk.dataset.preview.model.Preview
import no.fdk.dataset.preview.model.PreviewRequest
import no.fdk.dataset.preview.service.PreviewService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@CrossOrigin
@RequestMapping("/preview")
class PreviewController(
    private val previewService: PreviewService
) {
    @PostMapping(consumes = ["application/json"])
    fun preview(@RequestBody previewRequest: PreviewRequest): ResponseEntity<Preview> {
        val table = previewService.parseToTable(previewRequest.url, previewRequest.rows)
        return ResponseEntity.ok(Preview(table, ""))
    }
}
