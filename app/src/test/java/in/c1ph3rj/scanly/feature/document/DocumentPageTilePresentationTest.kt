package `in`.c1ph3rj.scanly.feature.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentPageTilePresentationTest {

    @Test
    fun pageTileAspectIsPortraitPaperNotSquare() {
        val aspect = documentPageTileAspectRatio()
        assertEquals(DOCUMENT_PAGE_TILE_ASPECT_RATIO, aspect, 0.0001f)
        assertTrue("page tiles must be taller than they are wide", aspect < 1f)
        assertFalse("page tiles must not be 1:1 thumbs", aspect == 1f)
    }

    @Test
    fun pageNumberLabelIsOneBasedAndClamped() {
        assertEquals("1", formatDocumentPageNumberLabel(0, 8))
        assertEquals("8", formatDocumentPageNumberLabel(7, 8))
        assertEquals("8", formatDocumentPageNumberLabel(99, 8))
        assertEquals("0", formatDocumentPageNumberLabel(0, 0))
    }

    @Test
    fun pageIndexLabelIsVisiblePageCopy() {
        assertEquals("Page 1", formatDocumentPageIndexLabel(0, 4))
        assertEquals("Page 4", formatDocumentPageIndexLabel(3, 4))
    }
}
