package no.fdk.dataset.preview.service

internal enum class PreviewFormat {
    ZIP,
    XLSX,
    XLS,
    CSV,
    PLAIN,
    ;

    companion object {
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

        private fun isZipContentType(contentType: String?): Boolean = contentType == "application/zip"

        private fun isXlsxContentType(contentType: String?): Boolean =
            contentType != null &&
                """application/vnd\.openxmlformats-officedocument\.spreadsheetml\.sheet"""
                    .toRegex()
                    .containsMatchIn(contentType)

        private fun isCsvContentType(contentType: String?): Boolean =
            contentType != null &&
                (
                    """\+?csv""".toRegex().containsMatchIn(contentType) ||
                        """\+?vnd\.ms-excel""".toRegex().containsMatchIn(contentType)
                )

        private fun isPlainContentType(contentType: String?): Boolean =
            contentType != null &&
                (
                    """\+?xml""".toRegex().containsMatchIn(contentType) ||
                        """\+?json""".toRegex().containsMatchIn(contentType)
                )

        private fun String.hasExtension(vararg extensions: String): Boolean = extensions.any { endsWith(it) }
    }
}
