package `in`.c1ph3rj.scanly.data.export

import `in`.c1ph3rj.scanly.domain.model.IdCardExportLayout
import `in`.c1ph3rj.scanly.domain.model.IdCardSide
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.PdfPageOrientation
import `in`.c1ph3rj.scanly.domain.model.PdfPageSize
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IdCardCopyLayoutTest {
    // ── Sheet planning ──────────────────────────────────────────────────────────

    @Test
    fun separatePagesKeepsEveryPageOnItsOwnSheet() {
        val pages = listOf(doc("d1"), front("f", "p1"), back("b", "p1"))
        assertEquals(
            listOf(PdfSheet.Single("d1"), PdfSheet.Single("f"), PdfSheet.Single("b")),
            PdfSheetPlanner.plan(pages, IdCardExportLayout.SEPARATE_PAGES),
        )
    }

    @Test
    fun idCopyJoinsBothSidesWhereTheCardStartsFrontFirst() {
        // Back captured (or moved) before the front still prints front on top.
        val pages = listOf(doc("d1"), back("b1", "p1"), doc("d2"), front("f1", "p1"))
        assertEquals(
            listOf(PdfSheet.Single("d1"), PdfSheet.IdCardCopy(listOf("f1", "b1")), PdfSheet.Single("d2")),
            PdfSheetPlanner.plan(pages, IdCardExportLayout.ID_COPY),
        )
    }

    @Test
    fun aCardWithOnlyItsFrontGetsASingleCardSheet() {
        val pages = listOf(front("f1", "p1"), front("f2", "p2"), back("b2", "p2"))
        assertEquals(
            listOf(PdfSheet.IdCardCopy(listOf("f1")), PdfSheet.IdCardCopy(listOf("f2", "b2"))),
            PdfSheetPlanner.plan(pages, IdCardExportLayout.ID_COPY),
        )
    }

    // ── Real-size placement ─────────────────────────────────────────────────────

    @Test
    fun cardsPrintAtId1SizeOnA4WhenPageSizeIsAutoFit() {
        val placement = IdCardCopyLayout.place(
            imageSizes = listOf(1600 to 1009, 1600 to 1009),
            options = PdfExportOptions(pageSize = PdfPageSize.FIT),
            marginPoints = 0,
            footerPoints = 0,
        )
        assertEquals(595, placement.pageWidthPoints)
        assertEquals(842, placement.pageHeightPoints)
        placement.cards.forEach { card ->
            assertEquals(242.65f, card.width, 0.05f)
            assertEquals(153.01f, card.height, 0.05f)
            assertEquals((595f - card.width) / 2f, card.left, 0.01f)
        }
        val (front, back) = placement.cards
        assertEquals(IdCardCopyLayout.GAP_POINTS, back.top - (front.top + front.height), 0.01f)
        // The pair is centred vertically.
        val stackHeight = back.top + back.height - front.top
        assertEquals((842f - stackHeight) / 2f, front.top, 0.01f)
    }

    @Test
    fun uprightCardIsPlacedUpright() {
        val placement = IdCardCopyLayout.place(
            imageSizes = listOf(1009 to 1600),
            options = PdfExportOptions(pageSize = PdfPageSize.US_LETTER),
            marginPoints = 24,
            footerPoints = 0,
        )
        val card = placement.cards.single()
        assertEquals(612, placement.pageWidthPoints)
        assertTrue(card.height > card.width)
        assertEquals(242.65f, card.height, 0.05f)
    }

    @Test
    fun cardsShrinkOnlyWhenTheyCannotFit() {
        // Two upright cards (2 x 242.6 + gap) on a landscape A5 page (420 x 297 pt) must shrink.
        val placement = IdCardCopyLayout.place(
            imageSizes = listOf(1009 to 1600, 1009 to 1600),
            options = PdfExportOptions(pageSize = PdfPageSize.A5, orientation = PdfPageOrientation.LANDSCAPE),
            marginPoints = 24,
            footerPoints = 0,
        )
        assertEquals(595, placement.pageWidthPoints)
        assertEquals(420, placement.pageHeightPoints)
        val last = placement.cards.last()
        assertTrue(last.top + last.height <= 420f - 24f + 0.01f)
        assertTrue(placement.cards.first().top >= 24f - 0.01f)
    }

    private fun doc(path: String) = ExportPageSource(path)
    private fun front(path: String, pair: String) = ExportPageSource(path, pair, IdCardSide.FRONT)
    private fun back(path: String, pair: String) = ExportPageSource(path, pair, IdCardSide.BACK)
}
