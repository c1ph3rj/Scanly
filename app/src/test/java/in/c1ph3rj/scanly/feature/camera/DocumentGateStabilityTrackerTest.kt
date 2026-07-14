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
