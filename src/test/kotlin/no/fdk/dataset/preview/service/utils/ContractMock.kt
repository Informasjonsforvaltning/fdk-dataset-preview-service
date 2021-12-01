package no.fdk.dataset.preview.service.utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.springframework.beans.factory.ObjectFactory

private val mockserver = WireMockServer(5000)

fun startMockServer() {
    if(!mockserver.isRunning) {
        mockserver.stubFor(get(urlEqualTo("/download"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/csv")
                    .withBody(ObjectFactory::class.java.classLoader
                        .getResourceAsStream("test.csv")?.readAllBytes())
                )
        )
        mockserver.start()
    }
}

fun stopMockServer() {
    if (mockserver.isRunning) mockserver.stop()
}
