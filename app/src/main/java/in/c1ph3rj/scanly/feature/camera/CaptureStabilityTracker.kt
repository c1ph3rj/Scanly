package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.DocumentQuadPolicy
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
    private val stableConfidenceThreshold: Float = 0.72f,
    private val jitterThreshold: Float = 0.028f,
    private val maxCornerJitterThreshold: Float = 0.065f,
    private val minStableDurationMillis: Long = 1_800L,
    private val cooldownMillis: Long = 2_500L,
    private val rearmDistanceThreshold: Float = 0.08f,
) {
    private var stableReferenceQuad: DocumentCornerQuad? = null
    private var stableSinceMillis: Long? = null
    private var cooldownUntilMillis: Long = 0L
    private var lastCapturedQuad: DocumentCornerQuad? = null
    private var waitingForSceneChange: Boolean = false

    fun reset() {
        stableReferenceQuad = null
        stableSinceMillis = null
        cooldownUntilMillis = 0L
        lastCapturedQuad = null
        waitingForSceneChange = false
    }

    fun evaluate(
        result: CornerDetectionResult,
        autoCaptureEnabled: Boolean,
        nowMillis: Long,
        sceneIssue: CaptureSceneIssue? = null,
    ): StabilityEvaluation {
        val candidateQuad = result.quad?.takeIf { quad ->
            result.confidence >= stableConfidenceThreshold && DocumentQuadPolicy.isCaptureReady(quad)
        }

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
                statusMessage = if (candidateQuad == null) {
                    appString(R.string.capture_stability_tracker_auto_capture_is_off_use)
                } else {
                    appString(R.string.capture_stability_tracker_document_detected_tap_capture_whenever)
                },
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }

        if (candidateQuad == null) {
            resetStableWindow()
            if (waitingForSceneChange) {
                waitingForSceneChange = false
            }
            return StabilityEvaluation(
                phase = AutoCapturePhase.SEARCHING,
                statusMessage = appString(R.string.capture_stability_tracker_point_your_camera_at_a),
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }

        if (waitingForSceneChange && lastCapturedQuad != null) {
            val movedEnough = candidateQuad.meanCornerDistance(lastCapturedQuad!!) >= rearmDistanceThreshold
            if (!movedEnough) {
                resetStableWindow()
                return StabilityEvaluation(
                    phase = AutoCapturePhase.COOLDOWN,
                    statusMessage = appString(R.string.capture_stability_tracker_move_to_the_next_page),
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
            return StabilityEvaluation(
                phase = AutoCapturePhase.HOLD_STEADY,
                statusMessage = appString(R.string.capture_stability_tracker_hold_steady_to_auto_capture),
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }

        val stableDuration = nowMillis - stableSince
        if (stableDuration >= minStableDurationMillis) {
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

    fun onCaptureCommitted(quad: DocumentCornerQuad?, nowMillis: Long) {
        cooldownUntilMillis = nowMillis + cooldownMillis
        lastCapturedQuad = quad
        waitingForSceneChange = quad != null
        resetStableWindow()
    }

    fun capturingState(autoCaptureEnabled: Boolean): StabilityEvaluation = StabilityEvaluation(
        phase = AutoCapturePhase.CAPTURING,
        statusMessage = if (autoCaptureEnabled) appString(R.string.capture_stability_tracker_capturing_page) else appString(R.string.capture_stability_tracker_saving_page),
        countdownValue = null,
        shouldAutoCapture = false,
    )

    private fun resetStableWindow() {
        stableReferenceQuad = null
        stableSinceMillis = null
    }

}
