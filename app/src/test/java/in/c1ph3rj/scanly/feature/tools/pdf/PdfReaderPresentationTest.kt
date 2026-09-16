package `in`.c1ph3rj.scanly.feature.tools.pdf

import `in`.c1ph3rj.scanly.core.ui.ZoomableImageState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfReaderPresentationTest {

    @Test
    fun goToPageClampsToValidRange() {
        assertEquals(0, clampPdfReaderPageIndex(-4, 12))
        assertEquals(0, clampPdfReaderPageIndex(0, 12))
        assertEquals(5, clampPdfReaderPageIndex(5, 12))
        assertEquals(11, clampPdfReaderPageIndex(11, 12))
        assertEquals(11, clampPdfReaderPageIndex(99, 12))
        assertEquals(0, clampPdfReaderPageIndex(3, 0))
        assertEquals(0, clampPdfReaderPageIndex(-1, 1))
    }

    @Test
    fun layoutToggleSwitchesPagedAndContinuous() {
        assertEquals(PdfReaderLayout.Continuous, togglePdfReaderLayout(PdfReaderLayout.Paged))
        assertEquals(PdfReaderLayout.Paged, togglePdfReaderLayout(PdfReaderLayout.Continuous))
    }

    @Test
    fun chromeToggleFlipsVisibility() {
        assertFalse(togglePdfReaderChrome(true))
        assertTrue(togglePdfReaderChrome(false))
    }

    @Test
    fun pageLabelIsCurrentOfTotal() {
        assertEquals("1 of 12", formatPdfReaderPageLabel(0, 12))
        assertEquals("3 of 12", formatPdfReaderPageLabel(2, 12))
        assertEquals("12 of 12", formatPdfReaderPageLabel(11, 12))
        assertEquals("12 of 12", formatPdfReaderPageLabel(40, 12))
        assertEquals("0 of 0", formatPdfReaderPageLabel(0, 0))
    }

    @Test
    fun continuousYieldsOneFingerScrollToParentUntilZoomed() {
        assertTrue(pdfReaderAllowParentScrollGestures())
        assertTrue(pdfReaderParentScrollerEnabled(zoomActive = false))
        assertFalse(pdfReaderParentScrollerEnabled(zoomActive = true))
    }

    @Test
    fun zoomActiveTracksAnyPageNotOnlyTheCurrentIndex() {
        val states = mapOf(
            0 to ZoomableImageState(),
            2 to ZoomableImageState(),
        )
        assertFalse(pdfReaderZoomActive(states))
        states.getValue(2).toggleDoubleTapZoom()
        assertTrue(pdfReaderZoomActive(states))
        states.getValue(2).reset()
        assertFalse(pdfReaderZoomActive(states))
    }

    @Test
    fun fitScreenResetsEveryZoomedPageNotOnlyCurrentIndex() {
        val states = mapOf(
            0 to ZoomableImageState(),
            1 to ZoomableImageState(),
            2 to ZoomableImageState(),
        )
        states.getValue(1).toggleDoubleTapZoom()
        states.getValue(2).toggleDoubleTapZoom()
        assertTrue(states.getValue(1).isZoomActive)
        assertTrue(states.getValue(2).isZoomActive)
        resetPdfReaderZoom(states)
        assertFalse(states.getValue(0).isZoomActive)
        assertFalse(states.getValue(1).isZoomActive)
        assertFalse(states.getValue(2).isZoomActive)
        assertFalse(pdfReaderZoomActive(states))
    }
}
