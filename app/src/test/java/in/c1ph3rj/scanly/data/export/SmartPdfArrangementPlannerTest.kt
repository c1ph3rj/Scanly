package `in`.c1ph3rj.scanly.data.export

import `in`.c1ph3rj.scanly.domain.model.IdCardSide
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartPdfArrangementPlannerTest {
    @Test
    fun completeIdPairBecomesOneSheetInFrontBackOrder() {
        val pages = listOf(
            page(1, ScanMode.ID_CARD, "pair-1", IdCardSide.FRONT),
            page(2, ScanMode.ID_CARD, "pair-1", IdCardSide.BACK),
        )

        val sheet = SmartPdfArrangementPlanner.plan(pages).single()

        assertTrue(sheet is SmartPdfSheet.IdCardPair)
        sheet as SmartPdfSheet.IdCardPair
        assertEquals("page-1.jpg", sheet.front.imagePath)
        assertEquals("page-2.jpg", sheet.back.imagePath)
    }

    @Test
    fun mixedModesPreserveFirstOccurrenceOrder() {
        val pages = listOf(
            page(1, ScanMode.DOCUMENT),
            page(2, ScanMode.ID_CARD, "pair-1", IdCardSide.FRONT),
            page(3, ScanMode.BOOK),
            page(4, ScanMode.ID_CARD, "pair-1", IdCardSide.BACK),
        )

        val sheets = SmartPdfArrangementPlanner.plan(pages)

        assertTrue(sheets[0] is SmartPdfSheet.Standard)
        assertTrue(sheets[1] is SmartPdfSheet.IdCardPair)
        assertTrue(sheets[2] is SmartPdfSheet.BookSpread)
    }

    @Test
    fun incompleteIdPairBlocksSmartExportWithActionableMessage() {
        val failure = runCatching {
            SmartPdfArrangementPlanner.plan(
                listOf(page(3, ScanMode.ID_CARD, "pair-1", IdCardSide.FRONT)),
            )
        }.exceptionOrNull()

        assertTrue(failure is IllegalStateException)
        assertTrue(failure?.message.orEmpty().contains("page 3"))
        assertTrue(failure?.message.orEmpty().contains("Standard layout"))
    }

    private fun page(
        number: Int,
        mode: ScanMode,
        pairId: String? = null,
        side: IdCardSide? = null,
    ) = ExportPageSource(
        imagePath = "page-$number.jpg",
        scanMode = mode,
        idCardPairId = pairId,
        idCardSide = side,
        sourcePageNumber = number,
    )
}
