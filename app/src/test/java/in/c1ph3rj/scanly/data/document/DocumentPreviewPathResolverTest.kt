package `in`.c1ph3rj.scanly.data.document

import `in`.c1ph3rj.scanly.data.local.db.entity.ScanPageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentPreviewPathResolverTest {
    @Test
    fun renamePrefersExistingThumbnailWithoutReplacingPagePreview() {
        val page = page(
            thumbnailPath = "thumb.jpg",
            processedImagePath = "processed.jpg",
            rawImagePath = "raw.jpg",
        )

        val result = resolveDocumentPreviewPath(page) { path -> path != "missing.jpg" }

        assertEquals("thumb.jpg", result)
    }

    @Test
    fun renameFallsBackToProcessedThenRawPageImage() {
        val page = page(
            thumbnailPath = "missing.jpg",
            processedImagePath = "processed.jpg",
            rawImagePath = "raw.jpg",
        )

        val result = resolveDocumentPreviewPath(page) { path -> path != "missing.jpg" }

        assertEquals("processed.jpg", result)
    }

    @Test
    fun emptyDocumentHasNoPagePreviewAndUsesGeneratedCoverFallback() {
        assertNull(resolveDocumentPreviewPath(firstPage = null) { true })
    }

    private fun page(
        thumbnailPath: String?,
        processedImagePath: String?,
        rawImagePath: String?,
    ) = ScanPageEntity(
        id = "page-1",
        documentId = "document-1",
        pageIndex = 0,
        rawImagePath = rawImagePath,
        processedImagePath = processedImagePath,
        thumbnailPath = thumbnailPath,
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
}
