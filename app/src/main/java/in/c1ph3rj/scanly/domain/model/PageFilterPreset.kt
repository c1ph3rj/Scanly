package `in`.c1ph3rj.scanly.domain.model

enum class PageFilterPreset(
    val storageValue: String,
) {
    ORIGINAL("original"),
    AUTO("auto"),
    ENHANCED_COLOR("enhanced_color"),
    PHOTO("photo"),
    GRAYSCALE("grayscale"),
    BLACK_AND_WHITE("black_and_white"),
    CLEAN("clean"),
    SHADOW_REDUCTION("shadow_reduction"),
    MAGIC_COLOR("magic_color"),
    RECEIPT("receipt"),
    SOFT_BLACK_AND_WHITE("soft_black_and_white"),
    HIGH_CONTRAST("high_contrast");

    companion object {
        fun fromStorage(value: String): PageFilterPreset =
            entries.firstOrNull { it.storageValue == value } ?: ORIGINAL
    }
}
