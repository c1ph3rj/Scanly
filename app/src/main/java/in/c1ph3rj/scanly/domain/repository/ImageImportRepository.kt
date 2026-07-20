package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ImportImagesProgress

/**
 * Imports gallery images as document pages (decode + finalizeCapture pipeline).
 * [imageUriStrings] are Android content/file URI strings resolved in the data layer.
 */
interface ImageImportRepository {
    suspend fun importImages(
        documentId: String,
        imageUriStrings: List<String>,
        onProgress: suspend (ImportImagesProgress) -> Unit = {},
    ): ScanlyResult<Unit>
}
