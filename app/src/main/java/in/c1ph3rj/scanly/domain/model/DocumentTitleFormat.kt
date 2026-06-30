package `in`.c1ph3rj.scanly.domain.model

enum class DocumentTitleFormat(
    val label: String,
    val shortLabel: String,
    val isDefault: Boolean = false,
) {
    ScanDateTime(
        label = "Scan with date and time",
        shortLabel = "Scan · date & time",
        isDefault = true,
    ),
    DocumentDateTime(
        label = "Document with date and time",
        shortLabel = "Document · date & time",
    ),
    ScanDate(
        label = "Scan with date",
        shortLabel = "Scan · date",
    ),
    ScanIsoDate(
        label = "Scan with ISO date",
        shortLabel = "Scan · YYYY-MM-DD",
    ),
    ;

    fun next(): DocumentTitleFormat {
        val all = entries
        return all[(all.indexOf(this) + 1) % all.size]
    }

    companion object {
        val default: DocumentTitleFormat = entries.first { it.isDefault }
    }
}