package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad

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
    val statusMessage: String = "Point your camera at a document.",
    val countdownValue: Int? = null,
    val sceneIssue: CaptureSceneIssue? = null,
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
    private val minStableDurationMillis: Long = 1_800L,
    private val cooldownMillis: Long = 2_500L,
    private val rearmDistanceThreshold: Float = 0.08f,
    private val minArea: Float = 0.12f,
    private val maxArea: Float = 0.92f,
    private val minAspectRatio: Float = 0.35f,
    private val maxAspectRatio: Float = 1.9f,
) {
    private var stableReferenceQuad: DocumentCornerQuad? = null
    private var stableSinceMillis: Long? = null
    private var cooldownUntilMillis: Long = 0L
    private var lastCapturedQuad: DocumentCornerQuad? = null
    private var waitingForSceneChange: Boolean = false

    fun evaluate(
        result: CornerDetectionResult,
        autoCaptureEnabled: Boolean,
        nowMillis: Long,
        sceneIssue: CaptureSceneIssue? = null,
    ): StabilityEvaluation {
        val candidateQuad = result.quad?.takeIf { quad ->
            result.confidence >= stableConfidenceThreshold && quad.isValid() && isShapePlausible(quad)
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
                    "Auto-capture is off. Use the shutter when ready."
                } else {
                    "Document detected. Tap capture whenever you are ready."
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
                statusMessage = "Point your camera at a full document.",
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
                    statusMessage = "Move to the next page before auto-capture re-arms.",
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
                statusMessage = "Hold position. Auto-capture is cooling down.",
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }

        val stableSince = stableSinceMillis
        val reference = stableReferenceQuad
        if (reference == null || stableSince == null || candidateQuad.meanCornerDistance(reference) > jitterThreshold) {
            stableReferenceQuad = candidateQuad
            stableSinceMillis = nowMillis
            return StabilityEvaluation(
                phase = AutoCapturePhase.HOLD_STEADY,
                statusMessage = "Hold steady to auto-capture.",
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }

        val stableDuration = nowMillis - stableSince
        if (stableDuration >= minStableDurationMillis) {
            return StabilityEvaluation(
                phase = AutoCapturePhase.COUNTDOWN,
                statusMessage = "Capturing now.",
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
            statusMessage = "Hold steady.",
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
        statusMessage = if (autoCaptureEnabled) "Capturing page…" else "Saving page…",
        countdownValue = null,
        shouldAutoCapture = false,
    )

    private fun resetStableWindow() {
        stableReferenceQuad = null
        stableSinceMillis = null
    }

    private fun isShapePlausible(quad: DocumentCornerQuad): Boolean {
        val area = quad.area()
        val aspectRatio = quad.estimatedAspectRatio()
        return area in minArea..maxArea && aspectRatio in minAspectRatio..maxAspectRatio
    }
}
