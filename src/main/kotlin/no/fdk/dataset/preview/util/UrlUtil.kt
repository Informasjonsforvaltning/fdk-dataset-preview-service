package no.fdk.dataset.preview.util

import java.net.URI

class UrlUtil {
    companion object {
        fun getDomainName(url: String?): String {
            if (url == null) {
                return ""
            }

            val uri = URI(url)
            val domain: String = uri.host ?: ""
            return domain.removePrefix("www.")
        }
    }
}
