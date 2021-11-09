package no.fdk.dataset.preview.model

data class TableHeader(var columns: List<String>) {

    fun beautify() {
        columns = columns.map { column ->
            column
                .replaceFirstChar { it.uppercaseChar() }
                .replace("_", " ")
        }
    }
}
