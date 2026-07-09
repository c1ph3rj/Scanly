package `in`.c1ph3rj.scanly.domain.processing

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState

interface PageImageProcessor {
    suspend fun processCapture(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        filterPreset: PageFilterPreset = PageFilterPreset.AUTO,
        filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
    ): ProcessedPageArtifacts

    suspend fun reprocessPage(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        cropQuad: DocumentCornerQuad?,
        rotationDegrees: Int,
        filterPreset: PageFilterPreset,
        filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
        detectDocumentWhenCropQuadMissing: Boolean = true,
    ): ProcessedPageArtifacts
}

data class ProcessedPageArtifacts(
    val processedImagePath: String,
    val thumbnailPath: String,
    val cropQuad: DocumentCornerQuad?,
    val rotationDegrees: Int,
    val filterPreset: PageFilterPreset,
    val filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
    val processingState: PageProcessingState,
)
