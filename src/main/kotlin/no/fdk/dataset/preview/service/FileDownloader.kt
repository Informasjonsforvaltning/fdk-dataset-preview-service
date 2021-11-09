package no.fdk.dataset.preview.service

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit

class FileDownloader(okHttpClient: OkHttpClient) {

    companion object {
        private const val HTTP_TIMEOUT = 30
    }

    private var okHttpClient: OkHttpClient

    init {
        val okHttpBuilder = okHttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT.toLong(), TimeUnit.SECONDS)
            .readTimeout(HTTP_TIMEOUT.toLong(), TimeUnit.SECONDS)
        this.okHttpClient = okHttpBuilder.build()
    }

    fun download(url: String): ResponseBody {
        val request = Request.Builder().url(url).build()
        val response = okHttpClient.newCall(request).execute()
        val body = response.body
        val responseCode = response.code
        if (responseCode >= HttpURLConnection.HTTP_OK &&
            responseCode < HttpURLConnection.HTTP_MULT_CHOICE &&
            body != null) {

            return body
        } else {
            throw IllegalArgumentException("Error occurred when do http get $url")
        }
    }
}