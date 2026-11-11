package `in`.c1ph3rj.scanly.feature.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentSplitPartsTest {

    @Test
    fun cutsBetweenPagesMakeConsecutiveParts() {
        assertEquals(
            listOf(listOf(0, 1), listOf(2, 3, 4), listOf(5, 6, 7)),
            partsFromCuts(pageCount = 8, cuts = setOf(1, 4)),
        )
    }

    @Test
    fun noCutsIsOnePartAndACutAfterTheLastPageIsIgnored() {
        assertEquals(listOf(listOf(0, 1, 2)), partsFromCuts(pageCount = 3, cuts = emptySet()))
        assertEquals(listOf(listOf(0, 1, 2)), partsFromCuts(pageCount = 3, cuts = setOf(2)))
        assertEquals(emptyList<List<Int>>(), partsFromCuts(pageCount = 0, cuts = setOf(0)))
    }

    @Test
    fun everyPageCutsGiveOnePagePerPart() {
        assertEquals(setOf(0, 1, 2), everyPageCuts(4))
        assertEquals(4, partsFromCuts(4, everyPageCuts(4)).size)
        assertEquals(emptySet<Int>(), everyPageCuts(1))
    }

    @Test
    fun splittingNeedsAtLeastTwoParts() {
        assertFalse(DocumentSplitUiState(cuts = setOf(1)).canSplit)
        // Stale cuts past the end (pages were removed elsewhere) don't count.
        val pages = List(2) { fakePage(it) }
        val state = DocumentSplitUiState(document = fakeDocument(), pages = pages, cuts = setOf(5))
        assertFalse(state.canSplit)
        assertTrue(state.copy(cuts = setOf(0)).canSplit)
    }

    private fun fakeDocument() = `in`.c1ph3rj.scanly.domain.model.ScanDocument(
        id = "doc",
        title = "Lease",
        pageCount = 2,
        coverThumbnailPath = null,
        rootDirectoryPath = "/tmp/doc",
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )

    private fun fakePage(index: Int) = `in`.c1ph3rj.scanly.domain.model.ScanPage(
        id = "p$index",
        documentId = "doc",
        pageIndex = index,
        rawImagePath = null,
        processedImagePath = null,
        thumbnailPath = null,
        rotationDegrees = 0,
        cropQuad = null,
        filterPreset = `in`.c1ph3rj.scanly.domain.model.PageFilterPreset.entries.first(),
        processingState = `in`.c1ph3rj.scanly.domain.model.PageProcessingState.entries.first(),
        createdAtMillis = 0L,
        updatedAtMillis = 0L,
    )
}
