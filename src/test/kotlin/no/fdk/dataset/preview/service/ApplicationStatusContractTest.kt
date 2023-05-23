package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.service.utils.ApiTestContext
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.test.context.ContextConfiguration
import org.springframework.web.client.RestTemplate
import kotlin.test.assertEquals

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@SpringBootTest(
    properties = [
        "spring.profiles.active=integration-test",
        "logging.level.no.fdk=DEBUG"],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@ContextConfiguration(initializers = [ApiTestContext.Initializer::class])
@Tag("integration")
class ApplicationStatusContractTest : ApiTestContext() {

    @Test
    fun `Ping should return http status OK, without authentication`() {
        val request = RestTemplate()
        val response = request.exchange("http://localhost:$port/ping", HttpMethod.GET, null, String::class.java)

        assertEquals(HttpStatus.OK.value(), response.statusCode.value())
    }

    @Test
    fun `Ready should return http status OK, without authentication`() {
        val request = RestTemplate()
        val response = request.exchange("http://localhost:$port/ready", HttpMethod.GET, null, String::class.java)

        assertEquals(HttpStatus.OK.value(), response.statusCode.value())
    }
}