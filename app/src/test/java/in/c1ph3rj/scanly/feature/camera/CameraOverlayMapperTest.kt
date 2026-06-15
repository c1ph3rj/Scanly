package `in`.c1ph3rj.scanly.feature.camera

import org.junit.Assert.assertEquals
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

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: NormalizedPoint) {
        assertEquals(expectedX, actual.x, 0.0001f)
        assertEquals(expectedY, actual.y, 0.0001f)
    }
}
