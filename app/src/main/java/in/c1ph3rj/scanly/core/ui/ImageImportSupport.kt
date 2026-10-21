package `in`.c1ph3rj.scanly.core.ui

import androidx.activity.result.contract.ActivityResultContracts

object ImageImportSupport {
    /**
     * Pages are imported one at a time (decoded, downscaled and recycled per image), so peak
     * memory does not grow with the count; the cap bounds total import time and storage.
     */
    const val MAX_IMAGES_PER_IMPORT = 50

    private const val IMAGE_MIME_TYPE = "image/*"

    /** MIME filter passed to the picker so only images are selectable. */
    fun createPickRequest(): String = IMAGE_MIME_TYPE

    /**
     * Opens the system picker with `ACTION_GET_CONTENT` for images: the Files browser (Downloads,
     * Drive, other storage) plus gallery apps such as Gallery and Google Photos, which
     * `ACTION_OPEN_DOCUMENT` does not list. The picker has no selection limit, so results go
     * through [capSelection].
     */
    fun pickImagesContract(): ActivityResultContracts.GetMultipleContents =
        ActivityResultContracts.GetMultipleContents()

    fun <T> capSelection(items: List<T>): CappedSelection<T> {
        if (items.size <= MAX_IMAGES_PER_IMPORT) {
            return CappedSelection(items = items, truncated = false)
        }
        return CappedSelection(
            items = items.take(MAX_IMAGES_PER_IMPORT),
            truncated = true,
        )
    }

    fun importResultMessage(importedCount: Int, truncated: Boolean, skippedCount: Int = 0): String {
        val parts = mutableListOf(
            if (importedCount == 1) "Imported 1 image." else "Imported $importedCount images.",
        )
        if (skippedCount > 0) {
            parts += if (skippedCount == 1) {
                "Skipped 1 file that couldn't be opened."
            } else {
                "Skipped $skippedCount files that couldn't be opened."
            }
        }
        if (truncated) {
            parts += "Only $MAX_IMAGES_PER_IMPORT images can be added at a time."
        }
        return parts.joinToString(" ")
    }
}

data class CappedSelection<T>(
    val items: List<T>,
    val truncated: Boolean,
)
