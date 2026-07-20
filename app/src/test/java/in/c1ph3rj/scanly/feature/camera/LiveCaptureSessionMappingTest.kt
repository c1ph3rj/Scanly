package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.processing.LiveCapturePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Pure mapping tests for live analysis session → UI / capture completion events.
 */
class LiveCaptureSessionMappingTest {

    @Test
    fun liveCapturePhase_mapsToAutoCapturePhase() {
        assertEquals(AutoCapturePhase.OFF, LiveCapturePhase.OFF.toUiPhase())
        assertEquals(AutoCapturePhase.SEARCHING, LiveCapturePhase.SEARCHING.toUiPhase())
        assertEquals(AutoCapturePhase.HOLD_STEADY, LiveCapturePhase.HOLD_STEADY.toUiPhase())
        assertEquals(AutoCapturePhase.COUNTDOWN, LiveCapturePhase.COUNTDOWN.toUiPhase())
        assertEquals(AutoCapturePhase.COOLDOWN, LiveCapturePhase.COOLDOWN.toUiPhase())
        assertEquals(AutoCapturePhase.CAPTURING, LiveCapturePhase.CAPTURING.toUiPhase())
    }

    @Test
    fun replacementCompletionEvent_emitsOnlyForReplacementDrafts() {
        val replacement = PageCaptureDraft(
            pageId = "page-1",
            documentId = "doc-1",
            pageIndex = 0,
            rawImagePath = "/raw.jpg",
            processedImagePath = "/processed.jpg",
            thumbnailPath = "/thumb.jpg",
            replacementPageId = "page-1",
        )
        val normal = replacement.copy(replacementPageId = null)

        assertEquals(
            ScanSessionEvent.ReplacementCompleted("page-1"),
            replacementCompletionEvent(replacement, capturedPageId = "page-1"),
        )
        assertNull(replacementCompletionEvent(normal, capturedPageId = "page-1"))
    }
}
