package no.fdk.dataset.preview.service

import no.fdk.dataset.preview.model.Plain
import no.fdk.dataset.preview.model.Preview
import no.fdk.dataset.preview.util.ContentSanitizer
import org.apache.commons.io.IOUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.InputStream
import java.nio.charset.Charset

@Component
class PlainPreviewParser {
    private val logger: Logger = LoggerFactory.getLogger(PlainPreviewParser::class.java)

    fun parse(
        inputStream: InputStream,
        mediaType: String?,
        charset: Charset?,
    ): Preview {
        logDebug("Fetch plain content")

        val content = IOUtils.toString(inputStream, charset ?: Charset.forName("UTF-8"))
        val sanitizedContent = ContentSanitizer.removeDangerousContent(content)
        val plain = Plain(sanitizedContent, mediaType ?: "")
        return Preview(table = null, plain = plain)
    }

    private fun logDebug(message: String) {
        if (logger.isDebugEnabled) {
            logger.debug(message)
        }
    }
}
