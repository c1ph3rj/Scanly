package `in`.c1ph3rj.scanly.domain.model

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad

data class ScanPage(
    val id: String,
    val documentId: String,
    val pageIndex: Int,
    val rawImagePath: String?,
    val processedImagePath: String?,
    val thumbnailPath: String?,
    val rotationDegrees: Int,
    val cropQuad: DocumentCornerQuad?,
    val filterPreset: PageFilterPreset,
    val filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
    val processingState: PageProcessingState,
    val createdAtMillis: Long,
    val updatedAtMillis: Long,
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    val idCardPairId: String? = null,
    val idCardSide: IdCardSide? = null,
)
