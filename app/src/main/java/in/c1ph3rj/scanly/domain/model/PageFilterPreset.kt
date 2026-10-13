package `in`.c1ph3rj.scanly.domain.model

enum class PageFilterPreset(
    val storageValue: String,
) {
    ORIGINAL("original"),
    AUTO("auto"),
    MAGIC_COLOR("magic_color"),
    NATURAL_COLOR("natural_color"),
    GRAYSCALE("grayscale"),
    BLACK_AND_WHITE("black_and_white"),
    WHITEBOARD("whiteboard"),
    FADED("faded");

    companion object {
        /**
         * Presets removed in the v1.1 filter overhaul, mapped to the closest new look so
         * existing pages, documents and settings keep a sensible filter.
         */
        private val legacyStorageValues: Map<String, PageFilterPreset> = mapOf(
            "enhanced_color" to NATURAL_COLOR,
            "shadow_reduction" to MAGIC_COLOR,
            "clean" to GRAYSCALE,
            "soft_black_and_white" to FADED,
            "receipt" to FADED,
        )

        fun fromStorage(value: String): PageFilterPreset =
            entries.firstOrNull { it.storageValue == value }
                ?: legacyStorageValues[value]
                ?: ORIGINAL

        /** Legacy storage value → replacement storage value (used by the Room migration). */
        val legacyStorageMigrations: Map<String, String>
            get() = legacyStorageValues.mapValues { (_, preset) -> preset.storageValue }
    }
}
