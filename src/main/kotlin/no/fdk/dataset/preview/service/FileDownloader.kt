package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.util.validate
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.net.HttpURLConnection
import java.net.URI
import java.util.concurrent.TimeUnit

@Component
class FileDownloader {

    @Value("\${application.allowLocalhost}")
    private val allowLocalhost: Boolean = false

    companion object {
        private const val HTTP_TIMEOUT = 30
    }

    private var okHttpClient: OkHttpClient

    init {
        val okHttpBuilder = OkHttpClient().newBuilder()
            .connectTimeout(HTTP_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(HTTP_TIMEOUT.toLong(), TimeUnit.SECONDS)
        this.okHttpClient = okHttpBuilder.build()
    }

    fun download(url: String): ResponseBody {
        val uri = URI(url)

        if (!allowLocalhost) {
            uri.validate()
        }

        try {
            val request = Request.Builder().url(uri.toURL()).build()
            val response = okHttpClient.newCall(request).execute()
            val body = response.body
            val responseCode = response.code
            if (responseCode >= HttpURLConnection.HTTP_OK &&
                responseCode < HttpURLConnection.HTTP_MULT_CHOICE &&
                body != null
            ) {
                return body
            } else {
                throw DownloadException("Error occurred when do http get $url (status $responseCode)")
            }
        } catch (e: Exception) {
            throw DownloadException(e.message)
        }
    }
}