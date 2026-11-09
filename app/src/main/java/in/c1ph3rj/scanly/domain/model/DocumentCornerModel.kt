package `in`.c1ph3rj.scanly.domain.model

import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

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
    @param:StringRes val displayNameRes: Int,
) {
    LITE("lite", R.string.document_corner_model_lite),
    STANDARD("standard", R.string.document_corner_model_standard),
    HIGH("accurate", R.string.document_corner_model_high),
    ACCURATE("legacy", R.string.document_corner_model_accurate),
    ;

    val displayName: String get() = appString(displayNameRes)


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
