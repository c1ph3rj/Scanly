package `in`.c1ph3rj.scanly.core.ui

import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts

object ImageImportSupport {
    const val MAX_IMAGES_PER_IMPORT = 10

    fun createPickRequest(): PickVisualMediaRequest =
        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)

    fun pickMultipleVisualMediaContract(): ActivityResultContracts.PickMultipleVisualMedia =
        ActivityResultContracts.PickMultipleVisualMedia(MAX_IMAGES_PER_IMPORT)

    fun <T> capSelection(items: List<T>): CappedSelection<T> {
        if (items.size <= MAX_IMAGES_PER_IMPORT) {
            return CappedSelection(items = items, truncated = false)
        }
        return CappedSelection(
            items = items.take(MAX_IMAGES_PER_IMPORT),
            truncated = true,
        )
    }

    fun importResultMessage(importedCount: Int, truncated: Boolean): String {
        val base = if (importedCount == 1) {
            "Imported 1 image."
        } else {
            "Imported $importedCount images."
        }
        return if (truncated) {
            "$base Only $MAX_IMAGES_PER_IMPORT images can be added at a time."
        } else {
            base
        }
    }
}

data class CappedSelection<T>(
    val items: List<T>,
    val truncated: Boolean,
)
