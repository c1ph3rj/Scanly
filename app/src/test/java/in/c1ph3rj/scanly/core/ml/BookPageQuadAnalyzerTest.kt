package `in`.c1ph3rj.scanly.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookPageQuadAnalyzerTest {
    @Test
    fun strongOffCenterGutterRemovesSmallAdjacentPageSliver() {
        val refinement = BookPageQuadAnalyzer.refinementForEvidence(
            quad = spreadQuad(),
            evidence = evidence(fraction = 0.20f),
        )

        assertEquals(BookPageRefinementKind.DOMINANT_PAGE, refinement.kind)
        assertNotNull(refinement.quad)
        val page = checkNotNull(refinement.quad)
        assertTrue(page.topLeft.x > spreadQuad().topLeft.x)
        assertEquals(spreadQuad().topRight, page.topRight)
    }

    @Test
    fun strongCenteredGutterRejectsAmbiguousTwoPageSpread() {
        val refinement = BookPageQuadAnalyzer.refinementForEvidence(
            quad = spreadQuad(),
            evidence = evidence(fraction = 0.50f),
        )

        assertEquals(BookPageRefinementKind.AMBIGUOUS_TWO_PAGES, refinement.kind)
        assertNull(refinement.quad)
    }

    @Test
    fun weakInteriorLineDoesNotSplitOrdinaryDocument() {
        val quad = spreadQuad()
        val refinement = BookPageQuadAnalyzer.refinementForEvidence(
            quad = quad,
            evidence = evidence(
                fraction = 0.20f,
                seamSupport = 0.08f,
                medianSupport = 0.06f,
            ),
        )

        assertEquals(BookPageRefinementKind.NONE, refinement.kind)
        assertEquals(quad, refinement.quad)
    }

    @Test
    fun partialSeamEvidenceIsNotASplit() {
        val quad = spreadQuad()
        val partial = BookPageQuadAnalyzer.refinementForEvidence(
            quad = quad,
            evidence = evidence(fraction = 0.50f).copy(strongestInteriorContinuity = 0.40f),
        )
        val continuous = BookPageQuadAnalyzer.refinementForEvidence(
            quad = quad,
            evidence = evidence(fraction = 0.50f).copy(strongestInteriorContinuity = 0.90f),
        )

        assertEquals(BookPageRefinementKind.NONE, partial.kind)
        assertEquals(quad, partial.quad)
        assertEquals(BookPageRefinementKind.AMBIGUOUS_TWO_PAGES, continuous.kind)
    }

    @Test
    fun fullHeightGutterBetweenOldSampleLinesIsFound() {
        for (fraction in listOf(0.47f, 0.53f)) {
            val refinement = BookPageQuadAnalyzer.analyze(spreadGrid(gutterFraction = fraction), gridQuad())

            assertEquals("gutter at $fraction", BookPageRefinementKind.AMBIGUOUS_TWO_PAGES, refinement.kind)
            assertEquals(fraction, refinement.evidence.strongestInteriorFraction, 0.02f)
            assertTrue(refinement.evidence.strongestInteriorContinuity >= 0.75f)
        }
    }

    @Test
    fun ruleCoveringPartOfTheHeightIsNotASeam() {
        // Strong vertical rule over the top 40% only (a table column or photo border).
        val grid = spreadGrid { x, y -> if (x in 239..241 && y in 30..150) 40f else null }

        val refinement = BookPageQuadAnalyzer.analyze(grid, gridQuad())

        // Strong enough that the mean-only test would have called it a centred gutter.
        assertTrue(refinement.evidence.strongestInteriorSupport >= 0.10f)
        assertTrue(refinement.evidence.strongestInteriorContinuity < 0.75f)
        assertEquals(BookPageRefinementKind.NONE, refinement.kind)
        assertEquals(gridQuad(), refinement.quad)
    }

    @Test
    fun plainSinglePageIsNotSplit() {
        val refinement = BookPageQuadAnalyzer.analyze(spreadGrid(), gridQuad())

        assertEquals(BookPageRefinementKind.NONE, refinement.kind)
        assertEquals(gridQuad(), refinement.quad)
    }

    @Test
    fun offCentreGutterKeepsTheLargerPage() {
        val quad = gridQuad()
        val refinement = BookPageQuadAnalyzer.analyze(spreadGrid(gutterFraction = 0.31f), quad)

        assertEquals(BookPageRefinementKind.DOMINANT_PAGE, refinement.kind)
        val page = checkNotNull(refinement.quad)
        val gutterX = (QUAD_LEFT + 0.31f * (QUAD_RIGHT - QUAD_LEFT)) / (GRID_WIDTH - 1)
        assertEquals(gutterX, page.topLeft.x, 0.02f)
        assertEquals(quad.topRight, page.topRight)
        assertEquals(quad.bottomRight, page.bottomRight)
    }

    @Test
    fun interiorLinesLeaveNoGapsBetweenSearchWindows() {
        val grid = spreadGrid()
        val fractions = BookPageQuadAnalyzer.interiorFractions(grid, gridQuad())
        val widthPx = QUAD_RIGHT - QUAD_LEFT

        assertEquals(0.10f, fractions.first(), 1e-6f)
        assertEquals(0.90f, fractions.last(), 1e-6f)
        // Each line searches +-5 px (plus the 1 px difference step): spacing must stay below 10 px.
        fractions.zipWithNext().forEach { (first, second) ->
            assertTrue((second - first) * widthPx < 10f)
        }
    }

    /** 400x300 px quad in a 480x360 frame: two bright pages on a dark desk. */
    private fun gridQuad(): DocumentCornerQuad = DocumentCornerQuad(
        topLeft = NormalizedPoint(QUAD_LEFT / (GRID_WIDTH - 1), QUAD_TOP / (GRID_HEIGHT - 1)),
        topRight = NormalizedPoint(QUAD_RIGHT / (GRID_WIDTH - 1), QUAD_TOP / (GRID_HEIGHT - 1)),
        bottomRight = NormalizedPoint(QUAD_RIGHT / (GRID_WIDTH - 1), QUAD_BOTTOM / (GRID_HEIGHT - 1)),
        bottomLeft = NormalizedPoint(QUAD_LEFT / (GRID_WIDTH - 1), QUAD_BOTTOM / (GRID_HEIGHT - 1)),
    )

    private fun spreadGrid(
        gutterFraction: Float? = null,
        extra: (Int, Int) -> Float? = { _, _ -> null },
    ): LumaGrid {
        val gutterCenter = gutterFraction?.let { QUAD_LEFT + it * (QUAD_RIGHT - QUAD_LEFT) }
        return lumaGrid(GRID_WIDTH, GRID_HEIGHT) { x, y ->
            val onPage = x >= QUAD_LEFT && x <= QUAD_RIGHT && y >= QUAD_TOP && y <= QUAD_BOTTOM
            when {
                !onPage -> 50f
                gutterCenter != null && kotlin.math.abs(x - gutterCenter) <= 3f -> 70f
                else -> extra(x, y) ?: 220f
            }
        }
    }

    private fun spreadQuad() = DocumentCornerQuad(
        topLeft = NormalizedPoint(0.10f, 0.12f),
        topRight = NormalizedPoint(0.90f, 0.12f),
        bottomRight = NormalizedPoint(0.88f, 0.88f),
        bottomLeft = NormalizedPoint(0.12f, 0.88f),
    )

    private fun evidence(
        fraction: Float,
        seamSupport: Float = 0.24f,
        medianSupport: Float = 0.06f,
    ) = BookPageVisualEvidence(
        boundarySupport = 0.18f,
        strongestInteriorFraction = fraction,
        strongestInteriorSupport = seamSupport,
        medianInteriorSupport = medianSupport,
    )

    private companion object {
        const val GRID_WIDTH = 480
        const val GRID_HEIGHT = 360
        const val QUAD_LEFT = 40f
        const val QUAD_RIGHT = 440f
        const val QUAD_TOP = 30f
        const val QUAD_BOTTOM = 330f
    }
}
