package `in`.c1ph3rj.scanly.domain.model

enum class ScanMode(
    val storageValue: String,
    val displayName: String,
) {
    DOCUMENT("document", "Document"),
    ID_CARD("id_card", "ID"),
    BOOK("book", "Book");

    companion object {
        fun fromStorage(value: String?): ScanMode =
            entries.firstOrNull { it.storageValue == value } ?: DOCUMENT
    }
}

enum class IdCardSide(
    val storageValue: String,
) {
    FRONT("front"),
    BACK("back");

    companion object {
        fun fromStorage(value: String?): IdCardSide? =
            entries.firstOrNull { it.storageValue == value }
    }
}
