package `in`.c1ph3rj.scanly.data.pdftools

import `in`.c1ph3rj.scanly.domain.model.NormRect
import org.junit.Assert.assertEquals
import org.junit.Test

class SignGeometryTest {

    private val box = NormRect(left = 0.1f, top = 0.2f, width = 0.3f, height = 0.1f)

    @Test
    fun unrotatedPageMapsTopLeftOriginToPdfBottomLeft() {
        val frame = SignGeometry.PageFrame(0f, 0f, 600f, 800f, rotation = 0)
        // Box top-left on screen = (60, 160 from top) = PDF (60, 640).
        assertPoint(60f, 640f, SignGeometry.boxPointToUser(box, frame, 0f, 0f))
        // Bottom-right on screen = (240, 240 from top) = PDF (240, 560).
        assertPoint(240f, 560f, SignGeometry.boxPointToUser(box, frame, 1f, 1f))
    }

    @Test
    fun cropBoxOffsetIsApplied() {
        val frame = SignGeometry.PageFrame(lowerX = 20f, lowerY = 30f, boxWidth = 600f, boxHeight = 800f, rotation = 0)
        assertPoint(80f, 670f, SignGeometry.boxPointToUser(box, frame, 0f, 0f))
    }

    @Test
    fun everyRotationAgreesWithWatermarkMapping() {
        for (rotation in listOf(0, 90, 180, 270, -90, 450)) {
            val frame = SignGeometry.PageFrame(lowerX = 12f, lowerY = 7f, boxWidth = 600f, boxHeight = 800f, rotation)
            for ((fx, fy) in listOf(0f to 0f, 1f to 0f, 0f to 1f, 1f to 1f, 0.5f to 0.5f)) {
                val visualX = (box.left + fx * box.width) * frame.visualWidth
                val visualY = (1f - (box.top + fy * box.height)) * frame.visualHeight
                val expected = WatermarkGeometry.toUserSpace(
                    x = visualX,
                    y = visualY,
                    lowerX = frame.lowerX,
                    lowerY = frame.lowerY,
                    boxWidth = frame.boxWidth,
                    boxHeight = frame.boxHeight,
                    rotation = rotation,
                )
                assertPoint(expected.first, expected.second, SignGeometry.boxPointToUser(box, frame, fx, fy))
            }
        }
    }

    @Test
    fun rotatedPageSwapsVisualSize() {
        val frame = SignGeometry.PageFrame(0f, 0f, 600f, 800f, rotation = 90)
        assertEquals(800f, frame.visualWidth, 0.001f)
        assertEquals(600f, frame.visualHeight, 0.001f)
    }

    @Test
    fun unitSquareKeepsBoxSizeInPoints() {
        val frame = SignGeometry.PageFrame(0f, 0f, 600f, 800f, rotation = 90)
        val m = SignGeometry.unitSquareToUser(box, frame)
        // Unit u-axis length = box width in points, v-axis = box height.
        assertEquals(0.3f * 800f, kotlin.math.hypot(m.a, m.b), 0.01f)
        assertEquals(0.1f * 600f, kotlin.math.hypot(m.c, m.d), 0.01f)
    }

    private fun assertPoint(x: Float, y: Float, actual: Pair<Float, Float>) {
        assertEquals("x", x, actual.first, 0.01f)
        assertEquals("y", y, actual.second, 0.01f)
    }
}
