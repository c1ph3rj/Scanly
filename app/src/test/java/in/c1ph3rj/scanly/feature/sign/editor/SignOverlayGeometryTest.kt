package `in`.c1ph3rj.scanly.feature.sign.editor

import `in`.c1ph3rj.scanly.domain.model.NormRect
import org.junit.Assert.assertEquals
import org.junit.Test

class SignOverlayGeometryTest {

    private val box = NormRect(left = 0.2f, top = 0.2f, width = 0.4f, height = 0.2f)

    @Test
    fun fitPage_landscapeContainer_fitsHeightAndCentresHorizontally() {
        val fit = fitPage(containerW = 300f, containerH = 100f, aspect = 0.5f)

        assertClose(125f, fit.left)
        assertClose(0f, fit.top)
        assertClose(50f, fit.width)
        assertClose(100f, fit.height)
    }

    @Test
    fun fitPage_portraitContainer_fitsWidthAndCentresVertically() {
        val fit = fitPage(containerW = 100f, containerH = 300f, aspect = 0.5f)

        assertClose(0f, fit.left)
        assertClose(50f, fit.top)
        assertClose(100f, fit.width)
        assertClose(200f, fit.height)
    }

    @Test
    fun resize_lockedBottomRight_growsFromFixedTopLeft() {
        val result = resizeFromCorner(box, Corner.BottomRight, dxNorm = 0.1f, dyNorm = 0f, lockAspect = true, pageAspect = 1f)

        assertRect(NormRect(0.2f, 0.2f, 0.5f, 0.25f), result)
        assertClose(2f, aspectOf(result))
    }

    @Test
    fun resize_lockedTopLeft_growsFromFixedBottomRight() {
        val result = resizeFromCorner(box, Corner.TopLeft, dxNorm = -0.1f, dyNorm = 0f, lockAspect = true, pageAspect = 1f)

        assertRect(NormRect(0.1f, 0.15f, 0.5f, 0.25f), result)
    }

    @Test
    fun resize_lockedUsesLargerDragComponent() {
        val result = resizeFromCorner(box, Corner.BottomRight, dxNorm = 0f, dyNorm = 0.2f, lockAspect = true, pageAspect = 1f)

        assertRect(NormRect(0.2f, 0.2f, 0.8f, 0.4f), result)
    }

    @Test
    fun resize_lockedClampsToPageAndKeepsAspect() {
        val result = resizeFromCorner(box, Corner.BottomRight, dxNorm = 1f, dyNorm = 0f, lockAspect = true, pageAspect = 1f)

        assertRect(NormRect(0.2f, 0.2f, 0.8f, 0.4f), result)
        assertClose(1f, result.right)
    }

    @Test
    fun resize_lockedRespectsMinimumWidth() {
        val result = resizeFromCorner(box, Corner.TopLeft, dxNorm = 1f, dyNorm = 0f, lockAspect = true, pageAspect = 1f)

        assertClose(0.06f, result.width)
        assertClose(0.03f, result.height)
        assertClose(0.6f, result.right)
        assertClose(0.4f, result.bottom)
        assertClose(2f, aspectOf(result))
    }

    @Test
    fun resize_unlockedTopRight_keepsBottomLeftFixed() {
        val result = resizeFromCorner(box, Corner.TopRight, dxNorm = 0.1f, dyNorm = -0.05f, lockAspect = false, pageAspect = 1f)

        assertRect(NormRect(0.2f, 0.15f, 0.5f, 0.25f), result)
    }

    @Test
    fun resize_unlockedBottomLeft_keepsTopRightFixed() {
        val result = resizeFromCorner(box, Corner.BottomLeft, dxNorm = -0.1f, dyNorm = 0.1f, lockAspect = false, pageAspect = 1f)

        assertRect(NormRect(0.1f, 0.2f, 0.5f, 0.3f), result)
    }

    @Test
    fun resize_unlockedRespectsMinimumWidth() {
        val result = resizeFromCorner(box, Corner.BottomRight, dxNorm = -1f, dyNorm = 0f, lockAspect = false, pageAspect = 1f)

        assertClose(0.03f, result.width)
        assertClose(0.2f, result.left)
        assertClose(0.2f, result.top)
    }

    @Test
    fun resize_unlockedClampsToRightEdge() {
        val result = resizeFromCorner(box, Corner.BottomRight, dxNorm = 1f, dyNorm = 0f, lockAspect = false, pageAspect = 1f)

        assertClose(0.8f, result.width)
        assertClose(1f, result.right)
    }

    @Test
    fun moveBox_movesByOffset() {
        val result = moveBox(box, dxNorm = 0.1f, dyNorm = 0.05f)

        assertRect(NormRect(0.3f, 0.25f, 0.4f, 0.2f), result)
    }

    @Test
    fun moveBox_clampsToTopLeftEdge() {
        val result = moveBox(box, dxNorm = 0.1f, dyNorm = -0.5f)

        assertRect(NormRect(0.3f, 0f, 0.4f, 0.2f), result)
    }

    @Test
    fun moveBox_clampsToBottomRightEdge() {
        val result = moveBox(NormRect(0.8f, 0.8f, 0.1f, 0.1f), dxNorm = 0.5f, dyNorm = 0.5f)

        assertRect(NormRect(0.9f, 0.9f, 0.1f, 0.1f), result)
    }

    private fun aspectOf(rect: NormRect): Float = rect.width * 1f / rect.height

    private fun assertRect(expected: NormRect, actual: NormRect) {
        assertClose(expected.left, actual.left)
        assertClose(expected.top, actual.top)
        assertClose(expected.width, actual.width)
        assertClose(expected.height, actual.height)
    }

    private fun assertClose(expected: Float, actual: Float) {
        assertEquals(expected.toDouble(), actual.toDouble(), TOLERANCE)
    }

    private companion object {
        const val TOLERANCE = 1e-4
    }
}
