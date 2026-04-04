package `in`.c1ph3rj.scanly.domain.model

enum class PageFilterPreset(
    val storageValue: String,
) {
    ORIGINAL("original"),
    ENHANCED_COLOR("enhanced_color"),
    GRAYSCALE("grayscale"),
    BLACK_AND_WHITE("black_and_white");

    companion object {
        fun fromStorage(value: String): PageFilterPreset =
            entries.firstOrNull { it.storageValue == value } ?: ORIGINAL
    }
}
