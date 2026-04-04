package `in`.c1ph3rj.scanly.core.processing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint

class PerspectiveQuadMathTest {

    @Test
    fun sourcePointsPreserveCornerOrder() {
        val quad = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.1f, 0.2f),
            topRight = NormalizedPoint(0.9f, 0.2f),
            bottomRight = NormalizedPoint(0.85f, 0.8f),
            bottomLeft = NormalizedPoint(0.15f, 0.78f),
        )

        val sourcePoints = PerspectiveQuadMath.sourcePoints(
            quad = quad,
            width = 1_000,
            height = 2_000,
        )

        assertArrayEquals(
            floatArrayOf(
                100f, 400f,
                900f, 400f,
                850f, 1_600f,
                150f, 1_560f,
            ),
            sourcePoints,
            0.01f,
        )
    }

    @Test
    fun outputSizeRespectsDetectedDocumentGeometry() {
        val quad = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.1f, 0.1f),
            topRight = NormalizedPoint(0.9f, 0.1f),
            bottomRight = NormalizedPoint(0.88f, 0.92f),
            bottomLeft = NormalizedPoint(0.12f, 0.9f),
        )

        val outputSize = PerspectiveQuadMath.outputSize(
            quad = quad,
            sourceWidth = 2_000,
            sourceHeight = 3_000,
            minDimension = 720,
            maxDimension = 2_400,
        )

        assertTrue(outputSize.width in 1_520..1_580)
        assertTrue(outputSize.height in 2_380..2_400)
    }
}
