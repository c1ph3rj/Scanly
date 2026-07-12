package `in`.c1ph3rj.scanly.domain.model

enum class DocumentCornerModel(
    val storageValue: String,
    val displayName: String,
) {
    LEGACY("legacy", "Legacy"),
    LITE("lite", "Lite"),
    STANDARD("standard", "Standard"),
    ACCURATE("accurate", "Accurate"),
    ;

    companion object {
        fun fromStorage(value: String?): DocumentCornerModel =
            entries.firstOrNull { it.storageValue == value } ?: LEGACY
    }
}
