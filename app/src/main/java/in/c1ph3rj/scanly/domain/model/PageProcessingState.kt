package `in`.c1ph3rj.scanly.domain.model

enum class PageProcessingState(
    val storageValue: String,
) {
    CAPTURED("captured"),
    PROCESSED("processed"),
    NEEDS_REVIEW("needs_review");

    companion object {
        fun fromStorage(value: String): PageProcessingState =
            entries.firstOrNull { it.storageValue == value } ?: CAPTURED
    }
}
