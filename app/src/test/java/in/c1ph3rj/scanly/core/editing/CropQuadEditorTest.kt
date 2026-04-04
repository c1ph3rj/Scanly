package `in`.c1ph3rj.scanly.core.editing

import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint

class CropQuadEditorTest {

    @Test
    fun rotateClockwiseReordersCornersIntoNewOrientation() {
        val quad = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.1f, 0.1f),
            topRight = NormalizedPoint(0.8f, 0.12f),
            bottomRight = NormalizedPoint(0.82f, 0.9f),
            bottomLeft = NormalizedPoint(0.12f, 0.88f),
        )

        val rotated = CropQuadEditor.rotateClockwise(quad)

        assertPoint(0.12f, 0.12f, rotated.topLeft)
        assertPoint(0.9f, 0.1f, rotated.topRight)
        assertPoint(0.88f, 0.8f, rotated.bottomRight)
        assertPoint(0.1f, 0.82f, rotated.bottomLeft)
    }

    @Test
    fun moveHandleSoftClampsInsteadOfFreezingAtInvalidQuad() {
        val quad = CropQuadEditor.defaultQuad()

        val moved = CropQuadEditor.moveHandle(
            quad = quad,
            handle = CropHandle.TOP_LEFT,
            target = NormalizedPoint(0.95f, 0.95f),
        )

        assertTrue(moved.isValid(minArea = 0.005f, minDistance = 0.015f))
        assertTrue(moved.topLeft.x > quad.topLeft.x)
        assertTrue(moved.topLeft.y > quad.topLeft.y)
    }

    @Test
    fun defaultQuadProducesValidInsetCrop() {
        val quad = CropQuadEditor.defaultQuad()

        assertTrue(quad.isValid())
    }

    private fun assertPoint(
        expectedX: Float,
        expectedY: Float,
        point: NormalizedPoint,
    ) {
        assertEquals(expectedX, point.x, 0.0001f)
        assertEquals(expectedY, point.y, 0.0001f)
    }
}
