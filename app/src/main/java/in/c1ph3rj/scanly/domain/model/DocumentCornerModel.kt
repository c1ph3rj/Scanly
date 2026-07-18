package `in`.c1ph3rj.scanly.domain.model

/**
 * On-device document corner models, ordered from fastest to highest accuracy.
 *
 * Storage values stay tied to the underlying asset identity so existing
 * preferences continue to resolve to the same weights after renames:
 * - [HIGH] was previously labelled "Accurate" (`accurate` → 384 px regression)
 * - [ACCURATE] was previously labelled "Legacy" (`legacy` → YOLO-pose float16)
 */
enum class DocumentCornerModel(
    val storageValue: String,
    val displayName: String,
) {
    LITE("lite", "Lite"),
    STANDARD("standard", "Standard"),
    HIGH("accurate", "High"),
    ACCURATE("legacy", "Accurate"),
    ;

    companion object {
        fun fromStorage(value: String?): DocumentCornerModel =
            entries.firstOrNull { it.storageValue == value }
                ?: when (value) {
                    // Accept the product-name storage key if written by newer code paths.
                    "high" -> HIGH
                    else -> ACCURATE
                }
    }
}
