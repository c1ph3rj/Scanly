package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.DocumentGateClass
import `in`.c1ph3rj.scanly.core.ml.DocumentGateResult
import `in`.c1ph3rj.scanly.core.ml.DocumentGateTiming
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DocumentGateStabilityTrackerTest {
    @Test
    fun requiresTwoConsecutivePhysicalFramesAndRejectsScreenImmediately() {
        val tracker = DocumentGateStabilityTracker()

        assertFalse(tracker.evaluate(result(DocumentGateClass.PHYSICAL_DOCUMENT, 0.96f)))
        assertTrue(tracker.evaluate(result(DocumentGateClass.PHYSICAL_DOCUMENT, 0.97f)))
        assertFalse(tracker.evaluate(result(DocumentGateClass.DIGITAL_SCREEN, 0.01f)))
        assertFalse(tracker.evaluate(result(DocumentGateClass.PHYSICAL_DOCUMENT, 0.98f)))
    }

    @Test
    fun entersAfterTwoStrongFrames() {
        val tracker = DocumentGateStabilityTracker()

        assertFalse(tracker.evaluate(strong()))
        assertTrue(tracker.evaluate(strong()))
        assertTrue(tracker.evaluate(strong()))
    }

    @Test
    fun singleSoftFrameWhileAccepted_staysAccepted() {
        val tracker = acceptedTracker()

        assertTrue(tracker.evaluate(result(DocumentGateClass.PHYSICAL_DOCUMENT, 0.80f)))
        assertTrue(tracker.evaluate(strong()))
    }

    @Test
    fun singleWeakFrameWhileAccepted_staysAccepted() {
        val tracker = acceptedTracker()

        assertTrue(tracker.evaluate(result(DocumentGateClass.NEITHER, 0.40f)))
        assertTrue(tracker.evaluate(strong()))
        // The reject counter was cleared by the strong frame, so one more weak frame is tolerated.
        assertTrue(tracker.evaluate(result(DocumentGateClass.NEITHER, 0.40f)))
    }

    @Test
    fun twoConsecutiveWeakFrames_leaveAcceptedState() {
        val tracker = acceptedTracker()

        assertTrue(tracker.evaluate(result(DocumentGateClass.PHYSICAL_DOCUMENT, 0.60f)))
        assertFalse(tracker.evaluate(result(DocumentGateClass.NEITHER, 0.40f)))
    }

    @Test
    fun physicalNotTopClass_isNotSoftAccepted() {
        val tracker = acceptedTracker()
        val screenOnTop = DocumentGateResult(
            probabilities = mapOf(
                DocumentGateClass.PHYSICAL_DOCUMENT to 0.45f,
                DocumentGateClass.DIGITAL_SCREEN to 0.55f,
                DocumentGateClass.NEITHER to 0f,
            ),
            timing = DocumentGateTiming(0L, 0L, 0L, 0L),
        )

        assertTrue(tracker.evaluate(screenOnTop))
        assertFalse(tracker.evaluate(screenOnTop))
    }

    @Test
    fun singleConfidentScreenFrame_leavesImmediately() {
        val tracker = acceptedTracker()

        assertFalse(tracker.evaluate(result(DocumentGateClass.DIGITAL_SCREEN, 0.05f)))
    }

    @Test
    fun singleConfidentNeitherFrame_leavesImmediately() {
        val tracker = acceptedTracker()

        assertFalse(tracker.evaluate(result(DocumentGateClass.NEITHER, 0.05f)))
    }

    @Test
    fun afterLeaving_reentryNeedsTwoStrongFrames() {
        val tracker = acceptedTracker()
        assertFalse(tracker.evaluate(result(DocumentGateClass.DIGITAL_SCREEN, 0.05f)))

        assertFalse(tracker.evaluate(strong()))
        assertTrue(tracker.evaluate(strong()))
    }

    @Test
    fun softFramesNeverEnterFromIdle() {
        val tracker = DocumentGateStabilityTracker()

        repeat(5) {
            assertFalse(tracker.evaluate(result(DocumentGateClass.PHYSICAL_DOCUMENT, 0.80f)))
        }
    }

    @Test
    fun alternatingStrongAndWeakNeverEntersFromIdle() {
        val tracker = DocumentGateStabilityTracker()

        repeat(5) {
            assertFalse(tracker.evaluate(strong()))
            assertFalse(tracker.evaluate(result(DocumentGateClass.PHYSICAL_DOCUMENT, 0.80f)))
        }
    }

    @Test
    fun reset_clearsAcceptedState() {
        val tracker = acceptedTracker()

        tracker.reset()

        assertFalse(tracker.evaluate(strong()))
        assertTrue(tracker.evaluate(strong()))
    }

    @Test
    fun customRejectCount_isHonoured() {
        val tracker = DocumentGateStabilityTracker(requiredConsecutiveRejects = 3)
        tracker.evaluate(strong())
        assertTrue(tracker.evaluate(strong()))

        assertTrue(tracker.evaluate(result(DocumentGateClass.NEITHER, 0.40f)))
        assertTrue(tracker.evaluate(result(DocumentGateClass.NEITHER, 0.40f)))
        assertFalse(tracker.evaluate(result(DocumentGateClass.NEITHER, 0.40f)))
    }

    private fun acceptedTracker(): DocumentGateStabilityTracker {
        val tracker = DocumentGateStabilityTracker()
        tracker.evaluate(strong())
        assertTrue(tracker.evaluate(strong()))
        return tracker
    }

    private fun strong() = result(DocumentGateClass.PHYSICAL_DOCUMENT, 0.96f)

    /** [prediction] receives the probability mass not assigned to the physical class. */
    private fun result(prediction: DocumentGateClass, physical: Float): DocumentGateResult {
        val remaining = (1f - physical).coerceAtLeast(0f)
        return DocumentGateResult(
            probabilities = mapOf(
                DocumentGateClass.PHYSICAL_DOCUMENT to physical,
                DocumentGateClass.DIGITAL_SCREEN to if (prediction == DocumentGateClass.DIGITAL_SCREEN) remaining else 0f,
                DocumentGateClass.NEITHER to if (prediction == DocumentGateClass.NEITHER) remaining else 0f,
            ),
            timing = DocumentGateTiming(0L, 0L, 0L, 0L),
        )
    }
}
