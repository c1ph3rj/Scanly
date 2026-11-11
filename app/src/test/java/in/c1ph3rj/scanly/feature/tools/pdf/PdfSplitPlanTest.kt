package `in`.c1ph3rj.scanly.feature.tools.pdf

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfSplitPlanTest {

    private fun choices(
        pageCount: Int = 8,
        mode: PdfSplitMode = PdfSplitMode.Custom,
        customRanges: List<PdfPageRange> = emptyList(),
        mergeRanges: Boolean = false,
        pagesPerPart: Int = 3,
        selectedPages: Set<Int> = emptySet(),
        extractSeparately: Boolean = false,
        keepRest: Boolean = false,
    ) = PdfSplitChoices(pageCount, mode, customRanges, mergeRanges, pagesPerPart, selectedPages, extractSeparately, keepRest)

    // ── Parsing ──────────────────────────────────────────────────────────────────────────

    @Test
    fun parsesRangesAndSinglePagesWithLooseSpacingAndDashes() {
        assertEquals(
            listOf(PdfPageRange(1, 3), PdfPageRange(6, 6), PdfPageRange(7, 8)),
            parsePageRanges(" 1-3,6 , 7 – 8 ", pageCount = 8),
        )
    }

    @Test
    fun rejectsPagesPastTheEndReversedRangesAndGarbage() {
        assertNull(parsePageRanges("1-9", pageCount = 8))
        assertNull(parsePageRanges("5-2", pageCount = 8))
        assertNull(parsePageRanges("0", pageCount = 8))
        assertNull(parsePageRanges("1-3, abc", pageCount = 8))
        assertNull(parsePageRanges("1-", pageCount = 8))
        assertNull(parsePageRanges("", pageCount = 8))
    }

    @Test
    fun formatRoundTripsThroughParse() {
        val ranges = listOf(PdfPageRange(1, 3), PdfPageRange(5, 5))
        assertEquals("1-3, 5", formatPageRanges(ranges))
        assertEquals(ranges, parsePageRanges(formatPageRanges(ranges), pageCount = 8))
    }

    @Test
    fun compressesPagesIntoRuns() {
        assertEquals(
            listOf(PdfPageRange(1, 3), PdfPageRange(6, 6), PdfPageRange(8, 9)),
            compressPages(listOf(9, 2, 1, 3, 6, 8, 2)),
        )
        assertEquals("1–3, 6", formatPageRangesForDisplay(compressPages(listOf(1, 2, 3, 6))))
    }

    // ── Custom ───────────────────────────────────────────────────────────────────────────

    @Test
    fun customRangesBecomeOnePdfEachAndMayOverlapOrSkipPages() {
        val plan = planSplitOutputs(
            choices(customRanges = listOf(PdfPageRange(1, 3), PdfPageRange(3, 4), PdfPageRange(8, 8))),
        )
        assertEquals(listOf(listOf(0, 1, 2), listOf(2, 3), listOf(7)), plan.map { it.pageIndexes })
    }

    @Test
    fun mergedCustomRangesBecomeOnePdfInRangeOrder() {
        val plan = planSplitOutputs(
            choices(customRanges = listOf(PdfPageRange(6, 8), PdfPageRange(1, 2)), mergeRanges = true),
        )
        assertEquals(listOf(listOf(5, 6, 7, 0, 1)), plan.map { it.pageIndexes })
    }

    @Test
    fun anInvalidCustomRangeBlocksTheSplit() {
        assertTrue(planSplitOutputs(choices(customRanges = listOf(PdfPageRange(1, 3), PdfPageRange(5, 2)))).isEmpty())
        assertTrue(planSplitOutputs(choices(customRanges = listOf(PdfPageRange(0, 3)))).isEmpty())
        assertTrue(planSplitOutputs(choices(customRanges = emptyList())).isEmpty())
    }

    @Test
    fun uncoveredPagesListsPagesInNoValidRange() {
        val ranges = listOf(PdfPageRange(1, 3), PdfPageRange(6, 8), PdfPageRange(9, 2))
        assertEquals(listOf(4, 5), uncoveredPages(ranges, pageCount = 8))
        assertEquals(1, rangeIndexForPage(ranges, page = 7, pageCount = 8))
        assertEquals(-1, rangeIndexForPage(ranges, page = 4, pageCount = 8))
    }

    @Test
    fun addRangeStartsAfterTheLastRangeAndRunsToTheEnd() {
        assertEquals(PdfPageRange(4, 8), nextRange(listOf(PdfPageRange(1, 3)), pageCount = 8))
        assertEquals(PdfPageRange(8, 8), nextRange(listOf(PdfPageRange(1, 8)), pageCount = 8))
        assertEquals(PdfPageRange(1, 8), nextRange(emptyList(), pageCount = 8))
    }

    @Test
    fun defaultCustomRangesSplitTheFileInHalf() {
        assertEquals(listOf(PdfPageRange(1, 4), PdfPageRange(5, 8)), defaultCustomRanges(8))
        assertEquals(listOf(PdfPageRange(1, 1), PdfPageRange(2, 3)), defaultCustomRanges(3))
    }

    // ── Every N ──────────────────────────────────────────────────────────────────────────

    @Test
    fun everyNMakesEqualChunksWithAShorterLastOne() {
        assertEquals(
            listOf(PdfPageRange(1, 3), PdfPageRange(4, 6), PdfPageRange(7, 8)),
            everyNRanges(pageCount = 8, pagesPerPart = 3),
        )
        assertEquals(8, everyNRanges(pageCount = 8, pagesPerPart = 1).size)
    }

    @Test
    fun everyNNeverCollapsesIntoASingleCopyOfTheFile() {
        assertEquals(7, maxPagesPerPart(8))
        val plan = planSplitOutputs(choices(mode = PdfSplitMode.EveryN, pagesPerPart = 50))
        assertEquals(listOf(listOf(0, 1, 2, 3, 4, 5, 6), listOf(7)), plan.map { it.pageIndexes })
    }

    // ── Extract ──────────────────────────────────────────────────────────────────────────

    @Test
    fun extractTogetherSortsPickedPagesIntoOnePdf() {
        val plan = planSplitOutputs(choices(mode = PdfSplitMode.Extract, selectedPages = setOf(5, 1, 2)))
        assertEquals(listOf(listOf(1, 2, 5)), plan.map { it.pageIndexes })
    }

    @Test
    fun extractSeparatelyAddsTheRestWhenAsked() {
        val plan = planSplitOutputs(
            choices(
                pageCount = 4,
                mode = PdfSplitMode.Extract,
                selectedPages = setOf(0, 2),
                extractSeparately = true,
                keepRest = true,
            ),
        )
        assertEquals(listOf(listOf(0), listOf(2), listOf(1, 3)), plan.map { it.pageIndexes })
    }

    @Test
    fun extractNeedsAPickAndWontCopyTheWholeFileAsOne() {
        assertTrue(planSplitOutputs(choices(mode = PdfSplitMode.Extract)).isEmpty())
        val all = (0 until 8).toSet()
        assertTrue(planSplitOutputs(choices(mode = PdfSplitMode.Extract, selectedPages = all)).isEmpty())
        assertEquals(8, planSplitOutputs(choices(mode = PdfSplitMode.Extract, selectedPages = all, extractSeparately = true)).size)
    }

    @Test
    fun singlePageFilesCannotBeSplit() {
        assertTrue(planSplitOutputs(choices(pageCount = 1, customRanges = listOf(PdfPageRange(1, 1)))).isEmpty())
    }

    @Test
    fun outputRunsDescribeThePagesForNames() {
        val plan = PdfSplitOutputPlan(listOf(0, 1, 2, 5))
        assertEquals(listOf(PdfPageRange(1, 3), PdfPageRange(6, 6)), plan.runs)
    }
}
