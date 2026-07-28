package no.fdk.dataset.preview.model

data class TableHeader(
    val columns: List<String>,
) {
    fun beautified(): TableHeader =
        copy(
            columns =
                columns.map { column ->
                    column
                        .replaceFirstChar { it.uppercaseChar() }
                        .replace("_", " ")
                },
        )
}
