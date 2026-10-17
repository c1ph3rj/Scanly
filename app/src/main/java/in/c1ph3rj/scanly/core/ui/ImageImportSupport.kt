package `in`.c1ph3rj.scanly.core.ui

import androidx.activity.result.contract.ActivityResultContracts

object ImageImportSupport {
    /**
     * Pages are imported one at a time (decoded, downscaled and recycled per image), so peak
     * memory does not grow with the count; the cap bounds total import time and storage.
     */
    const val MAX_IMAGES_PER_IMPORT = 50

    private val IMAGE_MIME_TYPES = arrayOf("image/*")

    /** MIME filter passed to the system file picker so only images are selectable. */
    fun createPickRequest(): Array<String> = IMAGE_MIME_TYPES

    /**
     * Opens the system Files picker (browse any folder, Downloads, Drive, etc.) limited to
     * images. The picker has no selection limit, so results go through [capSelection].
     */
    fun pickImagesContract(): ActivityResultContracts.OpenMultipleDocuments =
        ActivityResultContracts.OpenMultipleDocuments()

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
