package `in`.c1ph3rj.scanly.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NormRectTest {

    @Test
    fun reshapeKeepsCentreAndPixelProportionsAfterRotate() {
        // Portrait page 0.5 wide-to-tall rotated to landscape: aspect 0.5 -> 2.0.
        val box = NormRect(0.2f, 0.4f, 0.4f, 0.1f)
        val reshaped = box.reshapedForPageAspect(oldPageAspect = 0.5f, newPageAspect = 2f)

        assertEquals(box.centerX, reshaped.centerX, 1e-4f)
        assertEquals(box.centerY, reshaped.centerY, 1e-4f)
        val oldPixelRatio = (box.width / box.height) * 0.5f
        val newPixelRatio = (reshaped.width / reshaped.height) * 2f
        assertEquals(oldPixelRatio, newPixelRatio, 1e-3f)
    }

    @Test
    fun reshapeIsIdentityWhenAspectIsUnchanged() {
        val box = NormRect(0.1f, 0.2f, 0.3f, 0.1f)
        val reshaped = box.reshapedForPageAspect(oldPageAspect = 0.7f, newPageAspect = 0.7f)

        assertEquals(box.width, reshaped.width, 1e-5f)
        assertEquals(box.height, reshaped.height, 1e-5f)
        assertEquals(box.left, reshaped.left, 1e-5f)
        assertEquals(box.top, reshaped.top, 1e-5f)
    }

    @Test
    fun reshapeStaysOnThePage() {
        val reshaped = NormRect(0.8f, 0.9f, 0.2f, 0.1f).reshapedForPageAspect(oldPageAspect = 0.5f, newPageAspect = 2f)

        assertTrue(reshaped.left >= 0f && reshaped.top >= 0f)
        assertTrue(reshaped.right <= 1f + 1e-5f && reshaped.bottom <= 1f + 1e-5f)
    }
}
