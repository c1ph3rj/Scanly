package `in`.c1ph3rj.scanly.feature.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage

class PageImagePreviewSelectionResolverTest {

    @Test
    fun opensTheRequestedPage() {
        assertEquals(
            "page-2",
            resolvePreviewPageId(
                currentSelectedPageId = null,
                openedPageId = "page-2",
                pages = pages(),
            ),
        )
    }

    @Test
    fun keepsThePageSelectedBySwiping() {
        assertEquals(
            "page-3",
            resolvePreviewPageId(
                currentSelectedPageId = "page-3",
                openedPageId = "page-1",
                pages = pages(),
            ),
        )
    }

    @Test
    fun fallsBackWhenTheSelectedPageIsRemoved() {
        assertEquals(
            "page-1",
            resolvePreviewPageId(
                currentSelectedPageId = "page-3",
                openedPageId = "page-1",
                pages = pages().dropLast(1),
            ),
        )
    }

    @Test
    fun returnsNullWhenTheDocumentHasNoPages() {
        assertNull(
            resolvePreviewPageId(
                currentSelectedPageId = "page-2",
                openedPageId = "page-1",
                pages = emptyList(),
            ),
        )
    }

    private fun pages() = listOf(
        page(id = "page-1", pageIndex = 0),
        page(id = "page-2", pageIndex = 1),
        page(id = "page-3", pageIndex = 2),
    )

    private fun page(
        id: String,
        pageIndex: Int,
    ) = ScanPage(
        id = id,
        documentId = "document-1",
        pageIndex = pageIndex,
        rawImagePath = null,
        processedImagePath = null,
        thumbnailPath = null,
        rotationDegrees = 0,
        cropQuad = null,
        filterPreset = PageFilterPreset.ORIGINAL,
        processingState = PageProcessingState.PROCESSED,
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )
}
