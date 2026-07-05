package `in`.c1ph3rj.scanly.core.ui

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomableImageStateTest {
    @Test
    fun `double tap zooms in then resets to fitted scale`() {
        val state = ZoomableImageState()

        state.toggleDoubleTapZoom()

        assertTrue(state.isZoomActive)
        assertEquals(3f, state.scale)

        state.offset = Offset(120f, -80f)
        state.toggleDoubleTapZoom()

        assertFalse(state.isZoomActive)
        assertEquals(1f, state.scale)
        assertEquals(Offset.Zero, state.offset)
    }
}
