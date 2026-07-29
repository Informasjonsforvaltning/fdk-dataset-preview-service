package no.fdk.dataset.preview.service

import java.util.Locale

internal enum class PreviewFormat {
    ZIP,
    XLSX,
    XLS,
    CSV,
    PLAIN,
    ;

    companion object {
        private val XLSX_CONTENT_TYPE_REGEX: Regex =
            Regex(
                """application/vnd\.openxmlformats-officedocument\.spreadsheetml\.sheet""",
            )
        private val CSV_CONTENT_TYPE_REGEX: Regex = Regex("""\+?csv""")
        private val EXCEL_CONTENT_TYPE_REGEX: Regex = Regex("""\+?vnd\.ms-excel""")
        private val XML_CONTENT_TYPE_REGEX: Regex = Regex("""\+?xml""")
        private val JSON_CONTENT_TYPE_REGEX: Regex = Regex("""\+?json""")

        fun detect(
            contentType: String?,
            resourceName: String,
        ): PreviewFormat? =
            when {
                isZipContentType(contentType) -> ZIP
                isXlsxContentType(contentType) || resourceName.hasExtension(".xlsx") -> XLSX
                resourceName.hasExtension(".xls") -> XLS
                isCsvContentType(contentType) || resourceName.hasExtension(".csv") -> CSV
                isPlainContentType(contentType) || resourceName.hasExtension(".xml", ".json") -> PLAIN
                else -> null
            }

        fun fromFileName(fileName: String): PreviewFormat? =
            when {
                fileName.hasExtension(".xlsx") -> XLSX
                fileName.hasExtension(".xls") -> XLS
                fileName.hasExtension(".csv") -> CSV
                fileName.hasExtension(".xml", ".json") -> PLAIN
                else -> null
            }

        private fun isZipContentType(contentType: String?): Boolean {
            if (contentType == null) return false
            val baseType = contentType.substringBefore(';').trim().lowercase(Locale.ROOT)
            return baseType == "application/zip"
        }

        private fun isXlsxContentType(contentType: String?): Boolean =
            contentType != null && XLSX_CONTENT_TYPE_REGEX.containsMatchIn(contentType)

        private fun isCsvContentType(contentType: String?): Boolean =
            contentType != null &&
                (
                    CSV_CONTENT_TYPE_REGEX.containsMatchIn(contentType) ||
                        EXCEL_CONTENT_TYPE_REGEX.containsMatchIn(contentType)
                )

        private fun isPlainContentType(contentType: String?): Boolean =
            contentType != null &&
                (
                    XML_CONTENT_TYPE_REGEX.containsMatchIn(contentType) ||
                        JSON_CONTENT_TYPE_REGEX.containsMatchIn(contentType)
                )

        private fun String.hasExtension(vararg extensions: String): Boolean = extensions.any { endsWith(it) }
    }
}
