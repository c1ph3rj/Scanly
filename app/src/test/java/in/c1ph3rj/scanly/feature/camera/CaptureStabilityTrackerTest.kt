package `in`.c1ph3rj.scanly.feature.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint

class CaptureStabilityTrackerTest {

    @Test
    fun stableQuadTransitionsIntoAutoCaptureThenRequiresSceneChange() {
        val tracker = CaptureStabilityTracker()
        val quad = standardQuad()
        val result = detection(quad)

        val first = tracker.evaluate(
            result = result,
            autoCaptureEnabled = true,
            nowMillis = 0L,
        )
        val second = tracker.evaluate(
            result = result,
            autoCaptureEnabled = true,
            nowMillis = 900L,
        )
        val ready = tracker.evaluate(
            result = result,
            autoCaptureEnabled = true,
            nowMillis = 1_900L,
        )

        assertEquals(AutoCapturePhase.HOLD_STEADY, first.phase)
        assertEquals(AutoCapturePhase.COUNTDOWN, second.phase)
        assertFalse(second.shouldAutoCapture)
        assertTrue(ready.shouldAutoCapture)

        tracker.onCaptureCommitted(quad = quad, nowMillis = 1_900L)

        val duplicateScene = tracker.evaluate(
            result = result,
            autoCaptureEnabled = true,
            nowMillis = 2_000L,
        )
        val movedScene = tracker.evaluate(
            result = detection(shiftedQuad()),
            autoCaptureEnabled = true,
            nowMillis = 4_800L,
        )

        assertEquals(AutoCapturePhase.COOLDOWN, duplicateScene.phase)
        assertFalse(duplicateScene.shouldAutoCapture)
        assertEquals(AutoCapturePhase.HOLD_STEADY, movedScene.phase)
    }

    @Test
    fun autoCaptureDisabledKeepsManualGuidanceOnly() {
        val tracker = CaptureStabilityTracker()

        val evaluation = tracker.evaluate(
            result = detection(standardQuad()),
            autoCaptureEnabled = false,
            nowMillis = 0L,
        )

        assertEquals(AutoCapturePhase.OFF, evaluation.phase)
        assertFalse(evaluation.shouldAutoCapture)
        assertTrue(evaluation.statusMessage.contains("Tap capture"))
    }

    private fun detection(quad: DocumentCornerQuad) = CornerDetectionResult(
        quad = quad,
        confidence = 0.92f,
        inferenceTimeMillis = 61L,
        modelName = "test-model",
    )

    private fun standardQuad() = DocumentCornerQuad(
        topLeft = NormalizedPoint(0.12f, 0.12f),
        topRight = NormalizedPoint(0.88f, 0.12f),
        bottomRight = NormalizedPoint(0.84f, 0.9f),
        bottomLeft = NormalizedPoint(0.16f, 0.88f),
    )

    private fun shiftedQuad() = DocumentCornerQuad(
        topLeft = NormalizedPoint(0.24f, 0.14f),
        topRight = NormalizedPoint(0.92f, 0.16f),
        bottomRight = NormalizedPoint(0.88f, 0.9f),
        bottomLeft = NormalizedPoint(0.28f, 0.88f),
    )
}
