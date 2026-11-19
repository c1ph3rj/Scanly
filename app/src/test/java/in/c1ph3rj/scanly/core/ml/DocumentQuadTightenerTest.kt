package `in`.c1ph3rj.scanly.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class DocumentQuadTightenerTest {
    @Test
    fun quadOvershootingAllSidesIsPulledOntoPaperEdges() {
        val grid = pageOnDesk()
        // ~3% overshoot on every side.
        val loose = grid.quadFromPixels(left = 51f, top = 68f, right = 248f, bottom = 331f)

        val refined = DocumentQuadTightener.refine(grid, loose)

        assertQuadNear(grid.quadFromPixels(PAGE_LEFT, PAGE_TOP, PAGE_RIGHT, PAGE_BOTTOM), refined, FRAME_TOLERANCE)
    }

    @Test
    fun quadOvershootingOneSideMovesOnlyThatSide() {
        val grid = pageOnDesk()
        val loose = grid.quadFromPixels(left = 49f, top = PAGE_TOP, right = PAGE_RIGHT, bottom = PAGE_BOTTOM)

        val refined = DocumentQuadTightener.refine(grid, loose)

        val pageLeft = grid.toNormalizedX(PAGE_LEFT)
        assertEquals(pageLeft, refined.topLeft.x, FRAME_TOLERANCE)
        assertEquals(pageLeft, refined.bottomLeft.x, FRAME_TOLERANCE)
        // Right-hand corners and the top/bottom heights are untouched.
        assertEquals(loose.topRight.x, refined.topRight.x, 1e-4f)
        assertEquals(loose.bottomRight.x, refined.bottomRight.x, 1e-4f)
        assertEquals(loose.topLeft.y, refined.topLeft.y, 1e-4f)
        assertEquals(loose.topRight.y, refined.topRight.y, 1e-4f)
        assertEquals(loose.bottomLeft.y, refined.bottomLeft.y, 1e-4f)
        assertEquals(loose.bottomRight.y, refined.bottomRight.y, 1e-4f)
    }

    @Test
    fun quadAlreadyOnPaperEdgeIsUnchanged() {
        val grid = pageOnDesk()
        val onEdge = grid.quadFromPixels(PAGE_LEFT, PAGE_TOP, PAGE_RIGHT, PAGE_BOTTOM)

        assertEquals(onEdge, DocumentQuadTightener.refine(grid, onEdge))
    }

    @Test
    fun rulesAndTextLinesInsideThePageDoNotPullAnEdgeThatIsOnThePaper() {
        // Low-contrast desk, so the ink is a far stronger edge than the paper boundary.
        val grid = pageOnDesk(desk = 150f) { x, y ->
            when {
                y in 84..86 && x in 64..235 -> INK // letterhead rule just inside the top edge
                x in 65..66 && y in 84..315 -> INK // table column rule just inside the left edge
                x in 233..234 && y in 84..315 -> INK // and the right edge
                y in 312..313 && x in 64..235 -> INK // footer rule just inside the bottom edge
                y >= 95 && y < 308 && (y - 95) % 10 < 2 && x in 70..228 -> INK // text lines
                else -> null
            }
        }
        val onEdge = grid.quadFromPixels(PAGE_LEFT, PAGE_TOP, PAGE_RIGHT, PAGE_BOTTOM)

        assertEquals(onEdge, DocumentQuadTightener.refine(grid, onEdge))
    }

    @Test
    fun overshootingQuadStopsAtPaperEdgeNotAtRuleInside() {
        val grid = pageOnDesk { x, y -> if (y in 92..94 && x in 64..235) INK else null }
        val loose = grid.quadFromPixels(left = PAGE_LEFT, top = 68f, right = PAGE_RIGHT, bottom = PAGE_BOTTOM)

        val refined = DocumentQuadTightener.refine(grid, loose)

        val topPx = grid.toPixelY(refined.topLeft.y)
        assertTrue("top landed at $topPx px", abs(topPx - PAGE_TOP) < 3f)
        assertEquals(refined.topLeft.y, refined.topRight.y, 1e-4f)
    }

    @Test
    fun featurelessInputLeavesQuadUnchanged() {
        val grid = lumaGrid(WIDTH, HEIGHT) { _, _ -> 128f }
        val quad = grid.quadFromPixels(left = 51f, top = 68f, right = 248f, bottom = 331f)

        assertEquals(quad, DocumentQuadTightener.refine(grid, quad))
    }

    @Test
    fun edgeSeenByOnlyPartOfItsSamplesStaysPut() {
        // Over the right half of the top side, paper continues upward (a second sheet), so only
        // ~half the samples find an edge inside the overshooting top side.
        val grid = lumaGrid(WIDTH, HEIGHT) { x, y ->
            val onPage = x in 60..239 && y in 80..319
            val secondSheet = x in 150..239 && y in 20..79
            if (onPage || secondSheet) PAPER else DESK
        }
        val loose = grid.quadFromPixels(left = PAGE_LEFT, top = 68f, right = PAGE_RIGHT, bottom = PAGE_BOTTOM)

        assertEquals(loose, DocumentQuadTightener.refine(grid, loose))
    }

    @Test
    fun edgeWithInconsistentOffsetsStaysPut() {
        // The paper top is stepped: half the samples see it 6.5 px in, half 21.5 px in.
        val grid = lumaGrid(WIDTH, HEIGHT) { x, y ->
            val top = if (x < 150) 73 else 88
            if (x in 60..239 && y in top..319) PAPER else DESK
        }
        val loose = grid.quadFromPixels(left = PAGE_LEFT, top = 66f, right = PAGE_RIGHT, bottom = PAGE_BOTTOM)

        assertEquals(loose, DocumentQuadTightener.refine(grid, loose))
    }

    @Test
    fun strongBoundarySupportSkipsTightening() {
        val grid = pageOnDesk()
        val loose = grid.quadFromPixels(left = 51f, top = 68f, right = 248f, bottom = 331f)

        assertEquals(loose, DocumentQuadTightener.refine(grid, loose, boundarySupport = 0.2f))
    }

    /** Bright page (pixels x 60..239, y 80..319) on a dark desk; [ink] overrides page pixels. */
    private fun pageOnDesk(
        desk: Float = DESK,
        ink: (Int, Int) -> Float? = { _, _ -> null },
    ): LumaGrid = lumaGrid(WIDTH, HEIGHT) { x, y ->
        if (x in 60..239 && y in 80..319) ink(x, y) ?: PAPER else desk
    }

    private fun assertQuadNear(expected: DocumentCornerQuad, actual: DocumentCornerQuad, tolerance: Float) {
        expected.orderedPoints().zip(actual.orderedPoints()).forEach { (want, got) ->
            assertEquals("${want.first} x", want.second.x, got.second.x, tolerance)
            assertEquals("${want.first} y", want.second.y, got.second.y, tolerance)
        }
    }

    private companion object {
        const val WIDTH = 300
        const val HEIGHT = 400
        const val DESK = 40f
        const val PAPER = 220f
        const val INK = 30f
        /** The paper boundary sits between pixel rows/columns. */
        const val PAGE_LEFT = 59.5f
        const val PAGE_RIGHT = 239.5f
        const val PAGE_TOP = 79.5f
        const val PAGE_BOTTOM = 319.5f
        /** "Within ~1.5% of frame". */
        const val FRAME_TOLERANCE = 0.015f
    }
}
