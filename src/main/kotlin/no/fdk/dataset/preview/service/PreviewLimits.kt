package no.fdk.dataset.preview.service

import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

@Component
class PreviewLimits(
    @param:Value("\${application.security.maxFileSize:10485760}")
    val maxFileSizeBytes: Long = 10485760,
    @param:Value("\${application.security.maxProcessingTime:30}")
    val maxProcessingTimeSeconds: Long = 30L,
) {
    fun getMaxNumberOfRows(rows: Int?): Int = when {
        rows == null -> DEFAULT_ROWS
        rows > MAX_ROWS -> MAX_ROWS
        else -> rows
    }

    companion object {
        val DELIMITERS = arrayOf(';', ',')
        const val NO_DELIMITER = '\u0000' // empty char
        const val DEFAULT_ROWS = 100
        const val MAX_ROWS = 1000
        const val MAX_COLUMNS = 100
    }
}
