package no.fdk.dataset.preview.controller

import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.CrossOrigin
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@CrossOrigin
class ApplicationStatusController {

    @GetMapping("/ping")
    fun ping(): ResponseEntity<Void> =
        ResponseEntity.ok().build()

    @GetMapping("/ready")
    fun ready(): ResponseEntity<Void> =
        ResponseEntity.ok().build()

}
