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

private fun apiGetCSRF(url: String, token: String? = null): Pair<Int, String?> {
    val connection = URL(url).openConnection() as HttpURLConnection
    token?.let { connection.setRequestProperty("X-API-KEY", it) }
    connection.setRequestProperty("X-XSRF-TOKEN", "DATASET-PREVIEW-CSRF-TOKEN")
    connection.setRequestProperty("referer", url)
    connection.connect()

    return Pair(connection.responseCode, connection.headerFields["Set-Cookie"]?.get(0)?.split(";")?.get(0))
}

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

    val csrfResponse = apiGetCSRF(url, token)
    if (csrfResponse.first != 200) {
        return mapOf("status" to csrfResponse.first, "header" to " ", "body" to " ")
    }

    val headers = HttpHeaders()
    headers.accept = listOf(accept)
    headers.set("Cookie", csrfResponse.second)
    headers.set("X-XSRF-TOKEN", csrfResponse.second?.split("=")?.get(1))
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

