package `in`.c1ph3rj.scanly.data.document

import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import `in`.c1ph3rj.scanly.domain.model.LibraryAssetRef

class DocumentPreviewPathResolverTest {
    @Test
    fun renamePrefersExistingThumbnailWithoutReplacingPagePreview() {
        val page = page(
            thumbnailPath = "thumb.jpg",
            processedImagePath = "processed.jpg",
            rawImagePath = "raw.jpg",
        )

        val result = resolveDocumentPreviewAsset(page)

        assertEquals("thumb.jpg", result?.relativePath)
    }

    @Test
    fun renameFallsBackToProcessedThenRawPageImage() {
        val page = page(
            thumbnailPath = "missing.jpg",
            processedImagePath = "processed.jpg",
            rawImagePath = "raw.jpg",
        )

        val result = resolveDocumentPreviewAsset(page.copy(thumbnailAsset = null))

        assertEquals("processed.jpg", result?.relativePath)
    }

    @Test
    fun emptyDocumentHasNoPagePreviewAndUsesGeneratedCoverFallback() {
        assertNull(resolveDocumentPreviewAsset(firstPage = null))
    }

    private fun page(
        thumbnailPath: String?,
        processedImagePath: String?,
        rawImagePath: String?,
    ) = ScanPageEntity(
        id = "page-1",
        documentId = "document-1",
        pageIndex = 0,
        rawAsset = rawImagePath?.let(::asset),
        processedAsset = processedImagePath?.let(::asset),
        thumbnailAsset = thumbnailPath?.let(::asset),
        rotationDegrees = 0,
        cropTopLeftX = null,
        cropTopLeftY = null,
        cropTopRightX = null,
        cropTopRightY = null,
        cropBottomRightX = null,
        cropBottomRightY = null,
        cropBottomLeftX = null,
        cropBottomLeftY = null,
        filterPreset = "original",
        processingState = "ready",
        createdAtMillis = 1L,
        updatedAtMillis = 1L,
    )

    private fun asset(path: String) = LibraryAssetRef(path, 1L, 1L, "0".repeat(64))
}
