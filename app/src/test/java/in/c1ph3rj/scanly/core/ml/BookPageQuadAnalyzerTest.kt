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
}
