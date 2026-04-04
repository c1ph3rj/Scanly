package `in`.c1ph3rj.scanly.core.ml

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentCornerQuadTest {

    @Test
    fun validNormalizedClockwiseQuadIsAccepted() {
        val quad = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.1f, 0.1f),
            topRight = NormalizedPoint(0.9f, 0.1f),
            bottomRight = NormalizedPoint(0.85f, 0.9f),
            bottomLeft = NormalizedPoint(0.12f, 0.88f),
        )

        assertTrue(quad.isValid())
    }

    @Test
    fun duplicatePointsAreRejected() {
        val quad = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.1f, 0.1f),
            topRight = NormalizedPoint(0.1f, 0.1f),
            bottomRight = NormalizedPoint(0.9f, 0.9f),
            bottomLeft = NormalizedPoint(0.1f, 0.9f),
        )

        assertFalse(quad.isValid())
    }

    @Test
    fun outOfBoundsPointsAreRejected() {
        val quad = DocumentCornerQuad(
            topLeft = NormalizedPoint(-0.1f, 0.1f),
            topRight = NormalizedPoint(0.9f, 0.1f),
            bottomRight = NormalizedPoint(0.9f, 0.9f),
            bottomLeft = NormalizedPoint(0.1f, 0.9f),
        )

        assertFalse(quad.isValid())
    }
}
