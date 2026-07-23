package `in`.c1ph3rj.scanly.domain.processing

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanMode

interface PageImageProcessor {
    suspend fun processCapture(
        rawImagePath: String,
        processedImagePath: String,
        thumbnailPath: String,
        filterPreset: PageFilterPreset = PageFilterPreset.AUTO,
        filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
        scanMode: ScanMode = ScanMode.DOCUMENT,
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
        scanMode: ScanMode = ScanMode.DOCUMENT,
    ): ProcessedPageArtifacts

    /**
     * Runs still-image document corner detection on [rawImagePath] after EXIF + user
     * rotation, returning a normalized crop quad or null when none is found.
     */
    suspend fun detectDocumentCorners(
        rawImagePath: String,
        rotationDegrees: Int,
        scanMode: ScanMode = ScanMode.DOCUMENT,
    ): DocumentCornerQuad?
}

data class ProcessedPageArtifacts(
    val processedImagePath: String,
    val thumbnailPath: String,
    val cropQuad: DocumentCornerQuad?,
    val rotationDegrees: Int,
    val filterPreset: PageFilterPreset,
    val processingState: PageProcessingState,
)
