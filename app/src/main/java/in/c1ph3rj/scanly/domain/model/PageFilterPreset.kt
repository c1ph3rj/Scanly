package `in`.c1ph3rj.scanly.domain.model

enum class PageFilterPreset(
    val storageValue: String,
) {
    ORIGINAL("original"),
    ENHANCED_COLOR("enhanced_color"),
    GRAYSCALE("grayscale"),
    BLACK_AND_WHITE("black_and_white"),
    CLEAN("clean"),
    MAGIC_COLOR("magic_color"),
    RECEIPT("receipt"),
    SOFT_BLACK_AND_WHITE("soft_black_and_white");

    companion object {
        fun fromStorage(value: String): PageFilterPreset =
            entries.firstOrNull { it.storageValue == value } ?: ORIGINAL
    }
}
