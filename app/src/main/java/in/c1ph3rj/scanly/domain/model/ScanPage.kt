package `in`.c1ph3rj.scanly.domain.model

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad

data class ScanPage(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val rawAsset: LibraryAssetRef?,
    val processedAsset: LibraryAssetRef?,
    val thumbnailAsset: LibraryAssetRef?,
    val rotationDegrees: Int,
    val cropQuad: DocumentCornerQuad?,
    val filterPreset: PageFilterPreset,
    val processingState: PageProcessingState,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
) {
    @Deprecated("Use rawAsset")
    val rawImagePath: String? get() = rawAsset?.relativePath
    @Deprecated("Use processedAsset")
    val processedImagePath: String? get() = processedAsset?.relativePath
    @Deprecated("Use thumbnailAsset")
    val thumbnailPath: String? get() = thumbnailAsset?.relativePath
}
