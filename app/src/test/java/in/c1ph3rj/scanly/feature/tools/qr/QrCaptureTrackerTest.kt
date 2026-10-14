package `in`.c1ph3rj.scanly.feature.tools.qr

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QrCaptureTrackerTest {
    @Test
    fun captureWaitsUntilTheSameCodeHasBeenVisibleFor350Millis() {
        val tracker = QrCaptureTracker()

        assertFalse(tracker.update("first", 1_000L))
        assertFalse(tracker.update("first", 1_349L))
        assertTrue(tracker.update("first", 1_350L))
    }

    @Test
    fun losingTheCodeRestartsTheVisibleLock() {
        val tracker = QrCaptureTracker()

        assertFalse(tracker.update("first", 0L))
        assertFalse(tracker.update(null, 300L))
        assertFalse(tracker.update("first", 350L))
        assertFalse(tracker.update("first", 699L))
        assertTrue(tracker.update("first", 700L))
    }

    @Test
    fun switchingCodesDoesNotCarryOverThePreviousLock() {
        val tracker = QrCaptureTracker()

        assertFalse(tracker.update("first", 0L))
        assertFalse(tracker.update("second", 300L))
        assertFalse(tracker.update("second", 649L))
        assertTrue(tracker.update("second", 650L))
    }

    @Test
    fun staleFramesRestartTheLockEvenWhenTheCodeMatches() {
        val tracker = QrCaptureTracker()

        assertFalse(tracker.update("first", 0L))
        assertFalse(tracker.update("first", 300L))
        assertFalse(tracker.update("first", 801L))
        assertFalse(tracker.update("first", 1_150L))
        assertTrue(tracker.update("first", 1_151L))
    }

    @Test
    fun frameGapToleranceIncludes500Millis() {
        val tracker = QrCaptureTracker()

        assertFalse(tracker.update("first", 0L))
        assertTrue(tracker.update("first", 500L))
    }

    @Test
    fun scanAgainRequiresANewLockForTheSameCode() {
        val tracker = QrCaptureTracker()

        assertFalse(tracker.update("first", 0L))
        assertTrue(tracker.update("first", 350L))
        tracker.reset()
        assertFalse(tracker.update("first", 400L))
        assertFalse(tracker.update("first", 749L))
        assertTrue(tracker.update("first", 750L))
    }
}
