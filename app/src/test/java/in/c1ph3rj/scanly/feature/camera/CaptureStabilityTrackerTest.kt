package `in`.c1ph3rj.scanly.feature.camera

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel

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

    @Test
    fun sceneIssueStopsAutoCaptureAndReturnsPlainLanguageGuidance() {
        val tracker = CaptureStabilityTracker()

        val evaluation = tracker.evaluate(
            result = detection(standardQuad()),
            autoCaptureEnabled = true,
            nowMillis = 2_000L,
            sceneIssue = CaptureSceneIssue.TOO_DARK,
        )

        assertEquals(AutoCapturePhase.SEARCHING, evaluation.phase)
        assertFalse(evaluation.shouldAutoCapture)
        assertEquals("Too dark. Add light or turn on the flash.", evaluation.statusMessage)
    }

    // --- Scene-change guard -------------------------------------------------------------------

    @Test
    fun droppedFramesAfterCaptureDoNotReArmTheSamePage() {
        val tracker = CaptureStabilityTracker()
        val quad = standardQuad()
        val capturedAt = captureFirstPage(tracker, quad)

        // The selector is reset at capture time, so the first frames afterwards are empty.
        tracker.evaluate(empty(), autoCaptureEnabled = true, nowMillis = capturedAt + 125L)
        tracker.evaluate(empty(), autoCaptureEnabled = true, nowMillis = capturedAt + 250L)
        val still = feed(tracker, detection(quad), from = capturedAt + 375L, until = capturedAt + 10_375L)

        assertNull(still.firstCaptureTime())
        assertTrue(still.all { (_, evaluation) -> evaluation.phase == AutoCapturePhase.COOLDOWN })
    }

    @Test
    fun briefAbsenceBoundedByFramesOrTimeDoesNotReArm() {
        // Enough time but too few frames (slow analysis).
        val slow = CaptureStabilityTracker()
        val quad = standardQuad()
        val slowCapture = captureFirstPage(slow, quad)
        listOf(300L, 600L, 900L).forEach { offset ->
            slow.evaluate(empty(), autoCaptureEnabled = true, nowMillis = slowCapture + offset)
        }
        assertNull(feed(slow, detection(quad), from = slowCapture + 1_000L, until = slowCapture + 11_000L).firstCaptureTime())

        // Enough frames but too little time.
        val fast = CaptureStabilityTracker()
        val fastCapture = captureFirstPage(fast, quad)
        (1..8).forEach { index ->
            fast.evaluate(empty(), autoCaptureEnabled = true, nowMillis = fastCapture + (index * 50L))
        }
        assertNull(feed(fast, detection(quad), from = fastCapture + 450L, until = fastCapture + 10_450L).firstCaptureTime())
    }

    @Test
    fun sustainedAbsenceReArmsForTheNextStablePage() {
        val tracker = CaptureStabilityTracker()
        val quad = standardQuad()
        val capturedAt = captureFirstPage(tracker, quad)

        feed(tracker, empty(), from = capturedAt + 125L, until = capturedAt + 1_000L)
        val next = feed(tracker, detection(quad), from = capturedAt + 1_125L, until = capturedAt + 8_000L)

        val captureTime = checkNotNull(next.firstCaptureTime())
        // Still waits out the cooldown and a full hold.
        assertTrue(captureTime >= capturedAt + 2_500L + 1_800L)
    }

    @Test
    fun movedQuadReArmsAfterTheNormalHold() {
        val tracker = CaptureStabilityTracker()
        val capturedAt = captureFirstPage(tracker, standardQuad())

        val moved = feed(tracker, detection(shiftedQuad()), from = capturedAt + 125L, until = capturedAt + 8_000L)

        val captureTime = checkNotNull(moved.firstCaptureTime())
        assertTrue(captureTime >= capturedAt + 2_500L + 1_800L)
        assertTrue(captureTime <= capturedAt + 2_500L + 1_800L + 250L)
    }

    @Test
    fun resetTrackingKeepsTheSceneChangeGuard() {
        val tracker = CaptureStabilityTracker()
        val quad = standardQuad()
        val capturedAt = captureFirstPage(tracker, quad)

        tracker.resetTracking()

        assertNull(feed(tracker, detection(quad), from = capturedAt + 125L, until = capturedAt + 10_000L).firstCaptureTime())
    }

    // --- Short detection gaps -----------------------------------------------------------------

    @Test
    fun singleDroppedFrameDoesNotRestartTheHold() {
        val tracker = CaptureStabilityTracker()
        val result = detection(standardQuad())

        feed(tracker, result, from = 0L, until = 875L)
        val gap = tracker.evaluate(empty(), autoCaptureEnabled = true, nowMillis = 1_000L)
        val rest = feed(tracker, result, from = 1_125L, until = 4_000L)

        assertEquals(AutoCapturePhase.COUNTDOWN, gap.phase)
        assertFalse(gap.shouldAutoCapture)
        assertEquals(1_875L, rest.firstCaptureTime())
    }

    @Test
    fun heldQuadNeverFiresAutoCaptureButNextFreshFrameDoes() {
        val tracker = CaptureStabilityTracker()
        val result = detection(standardQuad())

        feed(tracker, result, from = 0L, until = 1_750L)
        val held = tracker.evaluate(result, autoCaptureEnabled = true, nowMillis = 1_875L, isFreshDetection = false)
        val fresh = tracker.evaluate(result, autoCaptureEnabled = true, nowMillis = 2_000L)

        assertEquals(AutoCapturePhase.COUNTDOWN, held.phase)
        assertFalse(held.shouldAutoCapture)
        assertTrue(fresh.shouldAutoCapture)
    }

    @Test
    fun gapLongerThanAllowedFramesRestartsTheHold() {
        val tracker = CaptureStabilityTracker()
        val result = detection(standardQuad())

        feed(tracker, result, from = 0L, until = 875L)
        val gap = feed(tracker, empty(), from = 1_000L, until = 1_375L)
        val resumed = tracker.evaluate(result, autoCaptureEnabled = true, nowMillis = 1_500L)

        assertEquals(AutoCapturePhase.COUNTDOWN, gap[2].second.phase)
        assertEquals(AutoCapturePhase.SEARCHING, gap[3].second.phase)
        assertEquals(AutoCapturePhase.HOLD_STEADY, resumed.phase)
    }

    @Test
    fun staleHeldQuadsCannotExtendTheWindow() {
        val tracker = CaptureStabilityTracker()
        val result = detection(standardQuad())

        feed(tracker, result, from = 0L, until = 875L)
        val held = feed(tracker, result, from = 1_000L, until = 1_375L, fresh = false)
        val resumed = tracker.evaluate(result, autoCaptureEnabled = true, nowMillis = 1_500L)

        assertTrue(held.none { (_, evaluation) -> evaluation.shouldAutoCapture })
        assertEquals(AutoCapturePhase.HOLD_STEADY, held[3].second.phase)
        assertEquals(AutoCapturePhase.HOLD_STEADY, resumed.phase)
        assertNull(resumed.countdownValue)
    }

    @Test
    fun gapLongerThanAllowedTimeRestartsTheHold() {
        val tracker = CaptureStabilityTracker()
        val result = detection(standardQuad())

        feed(tracker, result, from = 0L, until = 500L)
        val gap = tracker.evaluate(empty(), autoCaptureEnabled = true, nowMillis = 1_100L)
        val resumed = tracker.evaluate(result, autoCaptureEnabled = true, nowMillis = 1_200L)

        assertEquals(AutoCapturePhase.SEARCHING, gap.phase)
        assertEquals(AutoCapturePhase.HOLD_STEADY, resumed.phase)
    }

    // --- Confidence per model -----------------------------------------------------------------

    @Test
    fun defaultStableConfidenceFollowsEachModelsScale() {
        assertEquals(0.5f, CaptureStabilityTracker.defaultStableConfidence(DocumentCornerModel.ACCURATE), 0.0001f)
        assertEquals(0.875f, CaptureStabilityTracker.defaultStableConfidence(DocumentCornerModel.LITE), 0.0001f)
        assertEquals(0.95f, CaptureStabilityTracker.defaultStableConfidence(DocumentCornerModel.STANDARD), 0.0001f)
        assertEquals(0.725f, CaptureStabilityTracker.defaultStableConfidence(DocumentCornerModel.HIGH), 0.0001f)
    }

    @Test
    fun legacyPoseConfidenceBelowOldFixedGateStillAutoCaptures() {
        val tracker = CaptureStabilityTracker()

        val frames = feed(
            tracker,
            detection(standardQuad(), confidence = 0.6f, model = DocumentCornerModel.ACCURATE),
            from = 0L,
            until = 3_000L,
        )

        assertEquals(1_875L, frames.firstCaptureTime())
    }

    @Test
    fun drawnQuadBelowStableConfidenceShowsHoldSteadyNotSearching() {
        val tracker = CaptureStabilityTracker()

        val frames = feed(
            tracker,
            detection(standardQuad(), confidence = 0.8f, model = DocumentCornerModel.LITE),
            from = 0L,
            until = 5_000L,
        )

        assertNull(frames.firstCaptureTime())
        frames.forEach { (_, evaluation) ->
            assertEquals(AutoCapturePhase.HOLD_STEADY, evaluation.phase)
            assertEquals("Hold steady to auto-capture.", evaluation.statusMessage)
        }
    }

    @Test
    fun injectedConfidenceRequirementIsApplied() {
        val tracker = CaptureStabilityTracker(stableConfidenceRequirement = { 0.95f })

        val frames = feed(tracker, detection(standardQuad(), confidence = 0.92f), from = 0L, until = 5_000L)

        assertNull(frames.firstCaptureTime())
    }

    // --- Visible region and frame aspect ------------------------------------------------------

    @Test
    fun quadOutsideVisibleRegionIsNeverAutoCaptured() {
        val tracker = CaptureStabilityTracker()

        val frames = feed(tracker, detection(standardQuad()), from = 0L, until = 5_000L, visible = false)

        assertNull(frames.firstCaptureTime())
        frames.forEach { (_, evaluation) ->
            assertEquals(AutoCapturePhase.HOLD_STEADY, evaluation.phase)
            assertEquals("Point your camera at a full document.", evaluation.statusMessage)
        }
    }

    @Test
    fun frameAspectRatioIsUsedForCaptureReadiness() {
        val narrow = DocumentCornerQuad(
            topLeft = NormalizedPoint(0.35f, 0.1f),
            topRight = NormalizedPoint(0.65f, 0.1f),
            bottomRight = NormalizedPoint(0.65f, 0.9f),
            bottomLeft = NormalizedPoint(0.35f, 0.9f),
        )

        val square = feed(CaptureStabilityTracker(), detection(narrow, aspect = 1f), from = 0L, until = 3_000L)
        // In a 1:2 portrait frame the same quad is ~0.19 wide/high: too thin to be a page.
        val portrait = feed(CaptureStabilityTracker(), detection(narrow, aspect = 0.5f), from = 0L, until = 3_000L)

        assertNotNull(square.firstCaptureTime())
        assertNull(portrait.firstCaptureTime())
    }

    private fun captureFirstPage(tracker: CaptureStabilityTracker, quad: DocumentCornerQuad): Long {
        val capturedAt = checkNotNull(feed(tracker, detection(quad), from = 0L, until = 3_000L).firstCaptureTime())
        tracker.onCaptureCommitted(quad = quad, nowMillis = capturedAt)
        return capturedAt
    }

    /** Feeds [result] every [step] ms in [from]..[until], stopping after the first auto-capture. */
    private fun feed(
        tracker: CaptureStabilityTracker,
        result: CornerDetectionResult,
        from: Long,
        until: Long,
        step: Long = 125L,
        fresh: Boolean = true,
        visible: Boolean = true,
    ): List<Pair<Long, StabilityEvaluation>> {
        val evaluations = mutableListOf<Pair<Long, StabilityEvaluation>>()
        var now = from
        while (now <= until) {
            val evaluation = tracker.evaluate(
                result = result,
                autoCaptureEnabled = true,
                nowMillis = now,
                isFreshDetection = fresh,
                quadWithinVisibleRegion = visible,
            )
            evaluations += now to evaluation
            if (evaluation.shouldAutoCapture) break
            now += step
        }
        return evaluations
    }

    private fun List<Pair<Long, StabilityEvaluation>>.firstCaptureTime(): Long? =
        firstOrNull { (_, evaluation) -> evaluation.shouldAutoCapture }?.first

    private fun empty() = CornerDetectionResult(
        quad = null,
        confidence = 0f,
        inferenceTimeMillis = 61L,
        modelName = "test-model",
    )

    private fun detection(
        quad: DocumentCornerQuad,
        confidence: Float = 0.92f,
        model: DocumentCornerModel = DocumentCornerModel.ACCURATE,
        aspect: Float = 1f,
    ) = CornerDetectionResult(
        quad = quad,
        confidence = confidence,
        inferenceTimeMillis = 61L,
        modelName = "test-model",
        model = model,
        frameAspectRatio = aspect,
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
