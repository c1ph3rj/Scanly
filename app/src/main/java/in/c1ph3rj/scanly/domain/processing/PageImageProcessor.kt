package `in`.c1ph3rj.scanly.domain.processing

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState

interface PageImageProcessor {
    suspend fun processCapture(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        filterPreset: PageFilterPreset = PageFilterPreset.AUTO,
        detectedCropQuad: DocumentCornerQuad? = null,
    ): ProcessedPageArtifacts

    suspend fun warmUp()

    suspend fun reprocessPage(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        cropQuad: DocumentCornerQuad?,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        detectDocumentWhenCropQuadMissing: Boolean = true,
    ): ProcessedPageArtifacts
}

data class ProcessedPageArtifacts(
    val processedImagePath: String,
    val thumbnailPath: String,
    val cropQuad: DocumentCornerQuad?,
    val rotationDegrees: Int,
    val filterPreset: PageFilterPreset,
    val processingState: PageProcessingState,
)
