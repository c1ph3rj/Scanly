package `in`.c1ph3rj.scanly.feature.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint

class CameraOverlayMapperTest {

    @Test
    fun rotatesCameraCropIntoDetectionOrientation() {
        val frame = DetectionFrame(
            width = 640,
            height = 480,
            rotationDegrees = 90,
            bytes = ByteArray(0),
            cropLeft = 80,
            cropTop = 0,
            cropRight = 560,
            cropBottom = 480,
        )

        assertEquals(
            DetectionOverlayFrame(
                width = 480,
                height = 640,
                cropLeft = 0,
                cropTop = 80,
                cropRight = 480,
                cropBottom = 560,
            ),
            frame.toDetectionOverlayFrame(),
        )
    }

    @Test
    fun mapsDetectedCornersThroughCameraXCropRect() {
        val quad = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.25f, 0.25f),
            topRight = NormalizedPoint(0.75f, 0.25f),
            bottomRight = NormalizedPoint(0.75f, 0.75f),
            bottomLeft = NormalizedPoint(0.25f, 0.75f),
        )
        val frame = DetectionOverlayFrame(
            width = 480,
            height = 640,
            cropLeft = 0,
            cropTop = 80,
            cropRight = 480,
            cropBottom = 560,
        )

        val mapped = quad.mapToPreview(frame)

        assertPoint(0.25f, 1f / 6f, mapped[0])
        assertPoint(0.75f, 1f / 6f, mapped[1])
        assertPoint(0.75f, 5f / 6f, mapped[2])
        assertPoint(0.25f, 5f / 6f, mapped[3])
    }

    @Test
    fun fullFrameCropAcceptsAnyQuadInsideTheFrameMargin() {
        val frame = sensorFrame(rotation = 0, cropLeft = 0, cropRight = 640).toDetectionOverlayFrame()
        // Corners at the live policy's own frame margin (0.0125) stay valid: no behaviour change.
        val edgeHugging = box(0.0125f, 0.0125f, 0.9875f, 0.9875f)

        assertTrue(edgeHugging.isWithinVisibleRegion(frame, margin = 0.01f))
    }

    @Test
    fun visibleRegionFollowsTheCropForEveryRotation() {
        // The sensor crop keeps the left three quarters of a 640x480 buffer.
        val left = box(0.05f, 0.2f, 0.5f, 0.8f)
        val right = box(0.5f, 0.2f, 0.95f, 0.8f)
        val top = box(0.2f, 0.05f, 0.8f, 0.5f)
        val bottom = box(0.2f, 0.5f, 0.8f, 0.95f)
        val centre = box(0.3f, 0.3f, 0.7f, 0.7f)

        val expectations = mapOf(
            // rotation -> (quad that must be visible, quad that must be cut off)
            0 to (left to right),
            90 to (top to bottom),
            180 to (right to left),
            270 to (bottom to top),
        )
        expectations.forEach { (rotation, quads) ->
            val frame = sensorFrame(rotation, cropLeft = 0, cropRight = 480).toDetectionOverlayFrame()
            val (visible, cutOff) = quads
            assertTrue("rotation $rotation visible", visible.isWithinVisibleRegion(frame, margin = 0.01f))
            assertFalse("rotation $rotation cut off", cutOff.isWithinVisibleRegion(frame, margin = 0.01f))
            assertTrue("rotation $rotation centre", centre.isWithinVisibleRegion(frame, margin = 0.01f))
        }
    }

    @Test
    fun marginIsMeasuredInsideTheVisibleCrop() {
        // 90 degrees with a centred crop: visible oriented rows 80..560 of 640.
        val frame = sensorFrame(rotation = 90, cropLeft = 80, cropRight = 560).toDetectionOverlayFrame()
        val justInside = box(0.2f, 0.13f, 0.8f, 0.87f)
        val onTheCropEdge = box(0.2f, 0.125f, 0.8f, 0.875f)

        assertTrue(justInside.isWithinVisibleRegion(frame, margin = 0.005f))
        assertFalse(onTheCropEdge.isWithinVisibleRegion(frame, margin = 0.005f))
    }

    @Test
    fun invalidFrameIsNeverVisible() {
        val frame = DetectionOverlayFrame(width = 0, height = 0, cropLeft = 0, cropTop = 0, cropRight = 0, cropBottom = 0)

        assertFalse(box(0.3f, 0.3f, 0.7f, 0.7f).isWithinVisibleRegion(frame, margin = 0.01f))
    }

    private fun sensorFrame(rotation: Int, cropLeft: Int, cropRight: Int) = DetectionFrame(
        width = 640,
        height = 480,
        rotationDegrees = rotation,
        bytes = ByteArray(0),
        cropLeft = cropLeft,
        cropTop = 0,
        cropRight = cropRight,
        cropBottom = 480,
    )

    private fun box(left: Float, top: Float, right: Float, bottom: Float) = DocumentCornerQuad(
        topLeft = NormalizedPoint(left, top),
        topRight = NormalizedPoint(right, top),
        bottomRight = NormalizedPoint(right, bottom),
        bottomLeft = NormalizedPoint(left, bottom),
    )

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: NormalizedPoint) {
        assertEquals(expectedX, actual.x, 0.0001f)
        assertEquals(expectedY, actual.y, 0.0001f)
    }
}
