package `in`.c1ph3rj.scanly.feature.document

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
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

    @Test
    fun reorderTargetInsertsBeforeFirstRemainingPage() {
        assertEquals(
            0,
            resolvePageReorderTargetIndex(
                pageIds = listOf("page-1", "page-2", "page-3"),
                pageBounds = verticalBounds("page-1", "page-3"),
                draggedPageId = "page-2",
                dragCenter = Offset(50f, 20f),
                visibleBounds = null,
            ),
        )
    }

    @Test
    fun reorderTargetInsertsBetweenRemainingPages() {
        assertEquals(
            1,
            resolvePageReorderTargetIndex(
                pageIds = listOf("page-1", "page-2", "page-3", "page-4"),
                pageBounds = verticalBounds("page-1", "page-3", "page-4"),
                draggedPageId = "page-2",
                dragCenter = Offset(50f, 120f),
                visibleBounds = null,
            ),
        )
    }

    @Test
    fun reorderTargetInsertsAfterLastRemainingPage() {
        assertEquals(
            3,
            resolvePageReorderTargetIndex(
                pageIds = listOf("page-1", "page-2", "page-3", "page-4"),
                pageBounds = verticalBounds("page-1", "page-2", "page-3"),
                draggedPageId = "page-4",
                dragCenter = Offset(50f, 420f),
                visibleBounds = null,
            ),
        )
    }

    @Test
    fun reorderTargetUsesHorizontalPositionInsideSameRow() {
        val bounds = mapOf(
            "page-2" to Rect(left = 100f, top = 0f, right = 180f, bottom = 100f),
            "page-3" to Rect(left = 0f, top = 120f, right = 80f, bottom = 220f),
        )

        assertEquals(
            1,
            resolvePageReorderTargetIndex(
                pageIds = listOf("page-1", "page-2", "page-3"),
                pageBounds = bounds,
                draggedPageId = "page-1",
                dragCenter = Offset(160f, 50f),
                visibleBounds = null,
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

    private fun verticalBounds(vararg pageIds: String): Map<String, Rect> =
        pageIds.mapIndexed { index, pageId ->
            val top = index * 120f
            pageId to Rect(left = 0f, top = top, right = 100f, bottom = top + 100f)
        }.toMap()
}
