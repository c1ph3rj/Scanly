package `in`.c1ph3rj.scanly.data.library

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.data.library.manifest.CropManifest
import `in`.c1ph3rj.scanly.data.library.manifest.DocumentManifest
import `in`.c1ph3rj.scanly.data.library.manifest.PageManifest
import `in`.c1ph3rj.scanly.data.local.db.entity.DocumentEntity
import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage

fun DocumentEntity.toDomain(): ScanDocument = ScanDocument(
    id = id,
    title = title,
    pageCount = pageCount,
    coverThumbnail = coverThumbnail,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    groupId = groupId,
)

fun ScanPageEntity.toDomain(): ScanPage = ScanPage(
    id = id,
    documentId = documentId,
    pageIndex = pageIndex,
    rawAsset = rawAsset,
    processedAsset = processedAsset,
    thumbnailAsset = thumbnailAsset,
    rotationDegrees = rotationDegrees,
    cropQuad = cropQuad(),
    filterPreset = PageFilterPreset.fromStorage(filterPreset),
    processingState = PageProcessingState.fromStorage(processingState),
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

fun DocumentEntity.toManifest(
    pages: List<ScanPageEntity>,
    nextRevision: Long = revision + 1L,
    title: String = this.title,
    groupId: String? = this.groupId,
    preferredFilterPreset: String? = this.preferredFilterPreset,
    updatedAtMillis: Long = System.currentTimeMillis(),
): DocumentManifest = DocumentManifest(
    id = id,
    revision = nextRevision,
    title = title,
    preferredFilterPreset = preferredFilterPreset,
    groupId = groupId,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
    pages = pages.sortedBy(ScanPageEntity::pageIndex).map(ScanPageEntity::toManifest),
)

fun ScanPageEntity.toManifest(): PageManifest = PageManifest(
    id = id,
    pageIndex = pageIndex,
    rawAsset = rawAsset,
    processedAsset = processedAsset,
    thumbnailAsset = thumbnailAsset,
    rotationDegrees = rotationDegrees,
    crop = cropTopLeftX?.let {
        CropManifest(
            topLeftX = it,
            topLeftY = requireNotNull(cropTopLeftY),
            topRightX = requireNotNull(cropTopRightX),
            topRightY = requireNotNull(cropTopRightY),
            bottomRightX = requireNotNull(cropBottomRightX),
            bottomRightY = requireNotNull(cropBottomRightY),
            bottomLeftX = requireNotNull(cropBottomLeftX),
            bottomLeftY = requireNotNull(cropBottomLeftY),
        )
    },
    filterPreset = filterPreset,
    processingState = processingState,
    createdAtMillis = createdAtMillis,
    updatedAtMillis = updatedAtMillis,
)

private fun ScanPageEntity.cropQuad(): DocumentCornerQuad? = cropTopLeftX?.let {
    DocumentCornerQuad(
        topLeft = NormalizedPoint(it, requireNotNull(cropTopLeftY)),
        topRight = NormalizedPoint(requireNotNull(cropTopRightX), requireNotNull(cropTopRightY)),
        bottomRight = NormalizedPoint(requireNotNull(cropBottomRightX), requireNotNull(cropBottomRightY)),
        bottomLeft = NormalizedPoint(requireNotNull(cropBottomLeftX), requireNotNull(cropBottomLeftY)),
    )
}

