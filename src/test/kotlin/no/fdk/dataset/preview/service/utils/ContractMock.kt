package no.fdk.dataset.preview.service.utils

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.*
import org.springframework.beans.factory.ObjectFactory

private val mockserver = WireMockServer(5050)

fun startMockServer() {
    if(!mockserver.isRunning) {
        mockserver.stubFor(get(urlEqualTo("/download/csv"))
                .willReturn(aResponse()
                    .withStatus(200)
                    .withHeader("Content-Type", "text/csv")
                    .withBody(ObjectFactory::class.java.classLoader
                        .getResourceAsStream("test.csv")?.readAllBytes())
                )
        )
        mockserver.stubFor(get(urlEqualTo("/download/csv-zip"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/zip")
                .withBody(ObjectFactory::class.java.classLoader
                    .getResourceAsStream("test.csv.zip")?.readAllBytes())
            )
        )
        mockserver.stubFor(get(urlEqualTo("/download/xlsx-zip"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/zip")
                .withBody(ObjectFactory::class.java.classLoader
                    .getResourceAsStream("test.xlsx.zip")?.readAllBytes())
            )
        )
        mockserver.stubFor(get(urlEqualTo("/download/json-zip"))
            .willReturn(aResponse()
                .withStatus(200)
                .withHeader("Content-Type", "application/zip")
                .withBody(ObjectFactory::class.java.classLoader
                    .getResourceAsStream("test.json.zip")?.readAllBytes())
            )
        )
        mockserver.start()
    }
}

fun stopMockServer() {
    if (mockserver.isRunning) mockserver.stop()
}
