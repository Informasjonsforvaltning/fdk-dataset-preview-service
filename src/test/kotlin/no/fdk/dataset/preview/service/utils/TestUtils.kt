package no.fdk.dataset.preview.service.utils

import org.springframework.http.*
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

fun apiGet(port: Int, endpoint: String, acceptHeader: MediaType): Map<String, Any> {

    return try {
        val connection = URL("http://localhost:$port$endpoint").openConnection() as HttpURLConnection
        connection.setRequestProperty("Accept", acceptHeader.toString())
        connection.connect()

        if (isOK(connection.responseCode)) {
            val responseBody = connection.inputStream.bufferedReader().use(BufferedReader::readText)
            mapOf(
                "body" to responseBody,
                "header" to connection.headerFields.toString(),
                "status" to connection.responseCode
            )
        } else {
            mapOf(
                "status" to connection.responseCode,
                "header" to " ",
                "body" to " "
            )
        }
    } catch (e: Exception) {
        mapOf(
            "status" to e.toString(),
            "header" to " ",
            "body" to " "
        )
    }
}

private fun isOK(response: Int?): Boolean =
    if (response == null) false
    else HttpStatus.resolve(response)?.is2xxSuccessful == true

fun authorizedRequest(
    path: String,
    port: Int,
    body: String? = null,
    token: String? = null,
    httpMethod: HttpMethod,
    accept: MediaType = MediaType.APPLICATION_JSON
): Map<String, Any> {
    val request = RestTemplate()
    request.requestFactory = HttpComponentsClientHttpRequestFactory()
    val url = "http://localhost:$port$path"
    val headers = HttpHeaders()
    headers.accept = listOf(accept)
    token?.let { headers.set("X-API-KEY", it) }
    headers.contentType = MediaType.APPLICATION_JSON
    val entity: HttpEntity<String> = HttpEntity(body, headers)

    return try {
        val response = request.exchange(url, httpMethod, entity, String::class.java)
        mapOf(
            "body" to response.body,
            "header" to response.headers.toString(),
            "status" to response.statusCode.value()
        )

    } catch (e: HttpClientErrorException) {
        mapOf(
            "status" to e.rawStatusCode,
            "header" to " ",
            "body" to e.toString()
        )
    } catch (e: Exception) {
        mapOf(
            "status" to e.toString(),
            "header" to " ",
            "body" to " "
        )
    }

}

class TestResponseReader {
    private fun resourceAsReader(resourceName: String): Reader {
        return InputStreamReader(javaClass.classLoader.getResourceAsStream(resourceName)!!, StandardCharsets.UTF_8)
    }
}

