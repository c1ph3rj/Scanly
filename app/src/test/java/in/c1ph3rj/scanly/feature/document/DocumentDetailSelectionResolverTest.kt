package `in`.c1ph3rj.scanly.feature.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage

class DocumentDetailSelectionResolverTest {

    @Test
    fun returnsNullWhenNoPagesExist() {
        assertNull(resolveSelectedPageId(currentSelectedPageId = "page-2", pages = emptyList()))
    }

    @Test
    fun keepsCurrentSelectionWhenThatPageStillExists() {
        val pages = listOf(
            page(id = "page-1", index = 0),
            page(id = "page-2", index = 1),
            page(id = "page-3", index = 2),
        )

        assertEquals(
            "page-2",
            resolveSelectedPageId(
                currentSelectedPageId = "page-2",
                pages = pages,
            ),
        )
    }

    @Test
    fun fallsBackToFirstPageWhenSelectionIsGone() {
        val pages = listOf(
            page(id = "page-3", index = 0),
            page(id = "page-1", index = 1),
        )

        assertEquals(
            "page-3",
            resolveSelectedPageId(
                currentSelectedPageId = "page-2",
                pages = pages,
            ),
        )
    }

    private fun page(
        id: String,
        index: Int,
    ) = ScanPage(
        id = id,
        documentId = "document-1",
        pageIndex = index,
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
