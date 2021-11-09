package no.fdk.dataset.preview.model

import java.io.InputStream

data class DownloadResponse(val inputStream: InputStream, val contentType: String)
