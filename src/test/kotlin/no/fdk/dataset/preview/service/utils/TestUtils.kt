package no.fdk.dataset.preview.service.utils

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.MediaType
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.RestTemplate
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.Reader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

class CsrfTestException(message: String) : Exception(message)

private fun apiGetCSRF(url: String, token: String? = null): Pair<String, String?> {
    val connection = URL(url).openConnection() as HttpURLConnection
    token?.let { connection.setRequestProperty("X-API-KEY", it) }
    connection.connect()

    if(connection.responseCode != 200) {
        throw CsrfTestException("Unable to fetch CSRF token")
    }

    val br: BufferedReader = BufferedReader(InputStreamReader(connection.getInputStream()))
    val sb = StringBuilder()
    var output: String?
    while (br!!.readLine().also { output = it } != null) {
        sb.append(output)
    }
    val csrfTokenJson = ObjectMapper().readTree(sb.toString())
    val csrfTokenJsonToken = csrfTokenJson["token"].asText()

    val setCookieValue = connection.headerFields["Set-Cookie"]
    val csrfToken = setCookieValue?.find { s -> s.startsWith("DATASET-PREVIEW-CSRF-TOKEN")}
    return Pair(csrfTokenJsonToken, csrfToken?.split(";")?.get(0))
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

    val csrfResponse = apiGetCSRF("http://localhost:$port/csrf", token)

    val headers = HttpHeaders()
    headers.accept = listOf(accept)
    headers.set("Cookie", csrfResponse.second)
    headers.set("X-XSRF-TOKEN", csrfResponse.first)
    token?.let { headers.set("X-API-KEY", it) }
    headers.contentType = MediaType.APPLICATION_JSON
    val entity: HttpEntity<String> = HttpEntity(body, headers)

    return try {
        val response = request.exchange("http://localhost:$port$path", httpMethod, entity, String::class.java)
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

