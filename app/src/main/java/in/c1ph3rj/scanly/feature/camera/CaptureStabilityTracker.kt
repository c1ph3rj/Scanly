package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.DocumentQuadPolicy
import `in`.c1ph3rj.scanly.core.ml.LiveQuadRules
import `in`.c1ph3rj.scanly.core.ml.ScanlyModelAssets
import `in`.c1ph3rj.scanly.core.ml.ScanlyModelContract
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString

enum class AutoCapturePhase {
    OFF,
    SEARCHING,
    HOLD_STEADY,
    COUNTDOWN,
    COOLDOWN,
    CAPTURING,
}

data class LiveDetectionUiState(
    val quad: DocumentCornerQuad? = null,
    val overlayFrame: DetectionOverlayFrame? = null,
    val autoCaptureEnabled: Boolean = true,
    val isGridEnabled: Boolean = true,
    val phase: AutoCapturePhase = AutoCapturePhase.SEARCHING,
    val statusMessage: String = appString(R.string.scan_session_point_your_camera_at_a_2),
    val countdownValue: Int? = null,
    val sceneIssue: CaptureSceneIssue? = null,
    val model: DocumentCornerModel = DocumentCornerModel.ACCURATE,
    val confidence: Float? = null,
    val inferenceMillis: Double? = null,
    val totalMillis: Double? = null,
    val gateClass: `in`.c1ph3rj.scanly.core.ml.DocumentGateClass? = null,
    val gatePhysicalProbability: Float? = null,
    val gateMillis: Double? = null,
    val gateAccepted: Boolean = false,
) {
    val hasOverlay: Boolean = quad != null && overlayFrame?.isValid == true
}

data class StabilityEvaluation(
    val phase: AutoCapturePhase,
    val statusMessage: String,
    val countdownValue: Int?,
    val shouldAutoCapture: Boolean,
)

class CaptureStabilityTracker(
    /**
     * Minimum confidence for a quad to count towards the stable window, per producing model.
     * Model confidence scales differ (presence heads vs YOLO-pose), so one fixed value does not fit.
     */
    private val stableConfidenceRequirement: (DocumentCornerModel) -> Float = ::defaultStableConfidence,
    private val jitterThreshold: Float = 0.028f,
    private val maxCornerJitterThreshold: Float = 0.065f,
    private val minStableDurationMillis: Long = 1_800L,
    private val cooldownMillis: Long = 2_500L,
    private val rearmDistanceThreshold: Float = 0.08f,
    /** Consecutive frames without a fresh eligible quad that keep the stable window (~375 ms at 8 fps). */
    private val maxGapFrames: Int = 3,
    /** Upper bound on a tolerated gap in time, so slow frames cannot stretch a stale window. */
    private val maxGapMillis: Long = 500L,
    /**
     * Sustained absence (nothing drawn) that re-arms auto-capture without the quad moving. Must
     * outlast what a capture itself causes (selector reset: 1-2 empty frames) and a detector flicker
     * (covered by the selector grace), yet stay well under the time it takes to swap a page.
     */
    private val sceneChangeAbsentFrames: Int = 4,
    private val sceneChangeAbsentMillis: Long = 600L,
    /** Live readiness limits of the active scan mode. */
    private val quadRules: LiveQuadRules = LiveQuadRules.Default,
    /** Shown while waiting for the next subject after a capture (e.g. "Turn the page"). */
    private val nextSubjectMessage: () -> String = {
        appString(R.string.capture_stability_tracker_move_to_the_next_page)
    },
) {
    private var stableReferenceQuad: DocumentCornerQuad? = null
    private var stableSinceMillis: Long? = null
    private var lastEligibleMillis: Long = 0L
    private var gapFrames: Int = 0
    private var cooldownUntilMillis: Long = 0L
    private var lastCapturedQuad: DocumentCornerQuad? = null
    private var waitingForSceneChange: Boolean = false
    private var absentFrames: Int = 0
    private var absentSinceMillis: Long? = null

    /** Full reset: also forgets the cooldown and the captured-page guard. */
    fun reset() {
        resetTracking()
        cooldownUntilMillis = 0L
        lastCapturedQuad = null
        waitingForSceneChange = false
    }

    /**
     * Drops the stable window and absence bookkeeping but keeps the cooldown and the "wait for scene
     * change" guard, so a settings change or detection error cannot re-capture the page just saved.
     */
    fun resetTracking() {
        resetStableWindow()
        absentFrames = 0
        absentSinceMillis = null
    }

    /**
     * @param isFreshDetection false when [result]'s quad is the selector's quad held from an earlier
     *   frame through a detection gap. Such a frame is drawn but never arms or fires auto-capture.
     * @param quadWithinVisibleRegion false when the quad extends past what the preview shows (and
     *   what the saved photo is cropped to); it is drawn but cannot arm auto-capture.
     */
    fun evaluate(
        result: CornerDetectionResult,
        autoCaptureEnabled: Boolean,
        nowMillis: Long,
        sceneIssue: CaptureSceneIssue? = null,
        isFreshDetection: Boolean = true,
        quadWithinVisibleRegion: Boolean = true,
    ): StabilityEvaluation {
        val drawnQuad = result.quad
        trackAbsence(documentVisible = drawnQuad != null, nowMillis = nowMillis)
        val freshQuad = drawnQuad?.takeIf { isFreshDetection }
        val confidentQuad = freshQuad?.takeIf { quad ->
            result.confidence >= stableConfidenceRequirement(result.model) &&
                DocumentQuadPolicy.isCaptureReady(quad, result.frameAspectRatio, quadRules)
        }
        val candidateQuad = confidentQuad?.takeIf { quadWithinVisibleRegion }

        if (sceneIssue != null) {
            resetStableWindow()
            return StabilityEvaluation(
                phase = if (autoCaptureEnabled) AutoCapturePhase.SEARCHING else AutoCapturePhase.OFF,
                statusMessage = sceneIssue.guidance,
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }

        if (!autoCaptureEnabled) {
            resetStableWindow()
            return StabilityEvaluation(
                phase = AutoCapturePhase.OFF,
                statusMessage = if (drawnQuad == null) {
                    appString(R.string.capture_stability_tracker_auto_capture_is_off_use)
                } else {
                    appString(R.string.capture_stability_tracker_document_detected_tap_capture_whenever)
                },
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }

        if (waitingForSceneChange) {
            // Released only by a fresh quad far from the captured one, or by sustained absence
            // (trackAbsence) - never by a single dropped frame.
            val captured = lastCapturedQuad
            val movedEnough = captured == null ||
                freshQuad?.let { quad -> quad.meanCornerDistance(captured) >= rearmDistanceThreshold } == true
            if (!movedEnough) {
                resetStableWindow()
                return StabilityEvaluation(
                    phase = AutoCapturePhase.COOLDOWN,
                    statusMessage = nextSubjectMessage(),
                    countdownValue = null,
                    shouldAutoCapture = false,
                )
            }
            waitingForSceneChange = false
        }

        if (nowMillis < cooldownUntilMillis) {
            resetStableWindow()
            return StabilityEvaluation(
                phase = AutoCapturePhase.COOLDOWN,
                statusMessage = appString(R.string.capture_stability_tracker_hold_position_auto_capture_is),
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }

        if (candidateQuad == null) {
            return evaluateGap(
                nowMillis = nowMillis,
                drawn = drawnQuad != null,
                outsideVisibleRegion = confidentQuad != null,
            )
        }
        gapFrames = 0
        lastEligibleMillis = nowMillis

        val stableSince = stableSinceMillis
        val reference = stableReferenceQuad
        if (
            reference == null ||
            stableSince == null ||
            candidateQuad.meanCornerDistance(reference) > jitterThreshold ||
            candidateQuad.maxCornerDistance(reference) > maxCornerJitterThreshold
        ) {
            stableReferenceQuad = candidateQuad
            stableSinceMillis = nowMillis
            return holdSteady()
        }

        return countdown(stableDuration = nowMillis - stableSince, allowCapture = true)
    }

    fun onCaptureCommitted(quad: DocumentCornerQuad?, nowMillis: Long) {
        cooldownUntilMillis = nowMillis + cooldownMillis
        lastCapturedQuad = quad
        waitingForSceneChange = quad != null
        resetTracking()
    }

    fun capturingState(autoCaptureEnabled: Boolean): StabilityEvaluation = StabilityEvaluation(
        phase = AutoCapturePhase.CAPTURING,
        statusMessage = if (autoCaptureEnabled) appString(R.string.capture_stability_tracker_capturing_page) else appString(R.string.capture_stability_tracker_saving_page),
        countdownValue = null,
        shouldAutoCapture = false,
    )

    /**
     * No fresh eligible quad this frame. A short gap (bounded by frames and time) keeps the stable
     * window so one dropped detection does not restart the hold, but never fires auto-capture.
     */
    private fun evaluateGap(nowMillis: Long, drawn: Boolean, outsideVisibleRegion: Boolean): StabilityEvaluation {
        val stableSince = stableSinceMillis
        if (
            stableSince != null &&
            stableReferenceQuad != null &&
            gapFrames < maxGapFrames &&
            nowMillis - lastEligibleMillis <= maxGapMillis
        ) {
            gapFrames += 1
            return countdown(stableDuration = nowMillis - stableSince, allowCapture = false)
        }
        resetStableWindow()
        return when {
            // Drawn but cut off by the visible preview: the whole page has to be on screen.
            outsideVisibleRegion -> StabilityEvaluation(
                phase = AutoCapturePhase.HOLD_STEADY,
                statusMessage = appString(R.string.capture_stability_tracker_point_your_camera_at_a),
                countdownValue = null,
                shouldAutoCapture = false,
            )
            // Drawn (it passed the model's own threshold) but not yet confident enough to count.
            drawn -> holdSteady()
            else -> StabilityEvaluation(
                phase = AutoCapturePhase.SEARCHING,
                statusMessage = appString(R.string.capture_stability_tracker_point_your_camera_at_a),
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }
    }

    private fun holdSteady() = StabilityEvaluation(
        phase = AutoCapturePhase.HOLD_STEADY,
        statusMessage = appString(R.string.capture_stability_tracker_hold_steady_to_auto_capture),
        countdownValue = null,
        shouldAutoCapture = false,
    )

    private fun countdown(stableDuration: Long, allowCapture: Boolean): StabilityEvaluation {
        if (stableDuration >= minStableDurationMillis && allowCapture) {
            return StabilityEvaluation(
                phase = AutoCapturePhase.COUNTDOWN,
                statusMessage = appString(R.string.capture_stability_tracker_capturing_now),
                countdownValue = 1,
                shouldAutoCapture = true,
            )
        }

        val countdownValue = when {
            stableDuration < minStableDurationMillis / 3 -> 3
            stableDuration < (minStableDurationMillis * 2) / 3 -> 2
            else -> 1
        }
        return StabilityEvaluation(
            phase = AutoCapturePhase.COUNTDOWN,
            statusMessage = appString(R.string.capture_stability_tracker_hold_steady),
            countdownValue = countdownValue,
            shouldAutoCapture = false,
        )
    }

    /** Counts consecutive frames with nothing drawn; only sustained absence re-arms after a capture. */
    private fun trackAbsence(documentVisible: Boolean, nowMillis: Long) {
        if (documentVisible) {
            absentFrames = 0
            absentSinceMillis = null
            return
        }
        absentFrames += 1
        val absentSince = absentSinceMillis ?: nowMillis.also { absentSinceMillis = it }
        if (
            waitingForSceneChange &&
            absentFrames >= sceneChangeAbsentFrames &&
            nowMillis - absentSince >= sceneChangeAbsentMillis
        ) {
            waitingForSceneChange = false
        }
    }

    private fun resetStableWindow() {
        stableReferenceQuad = null
        stableSinceMillis = null
        gapFrames = 0
    }

    companion object {
        /** Presence-head models: never below the previous fixed gate of 0.72. */
        private const val PRESENCE_MODEL_STABLE_CONFIDENCE = 0.72f

        /** YOLO-pose scores run ~0.4-0.9 on real pages; 0.5 drops the weakest without stalling. */
        private const val LEGACY_POSE_STABLE_CONFIDENCE = 0.5f

        /** Never below the model's own presence threshold, so anything counted was also drawn. */
        fun defaultStableConfidence(model: DocumentCornerModel): Float {
            val contract = ScanlyModelAssets.contract(model)
            val floor = when (contract.outputKind) {
                ScanlyModelContract.OutputKind.LEGACY_POSE -> LEGACY_POSE_STABLE_CONFIDENCE
                ScanlyModelContract.OutputKind.CORNERS_AND_PRESENCE -> PRESENCE_MODEL_STABLE_CONFIDENCE
            }
            return maxOf(contract.presenceThreshold, floor)
        }
    }
}
