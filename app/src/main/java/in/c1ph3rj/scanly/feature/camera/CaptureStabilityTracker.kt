package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.DocumentQuadPolicy
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.ScanMode

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
    val model: DocumentCornerModel = DocumentCornerModel.ACCURATE,
    val confidence: Float? = null,
    val inferenceMillis: Double? = null,
    val totalMillis: Double? = null,
    val gateClass: `in`.c1ph3rj.scanly.core.ml.DocumentGateClass? = null,
    val gatePhysicalProbability: Float? = null,
    val gateMillis: Double? = null,
    val gateAccepted: Boolean = false,
    val bookGutterFraction: Float? = null,
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
        scanMode: ScanMode = ScanMode.DOCUMENT,
    ): StabilityEvaluation {
        val requiredConfidence = when (scanMode) {
            ScanMode.DOCUMENT -> stableConfidenceThreshold
            ScanMode.ID_CARD -> 0.66f
            ScanMode.BOOK -> 0.69f
        }
        val candidateQuad = result.quad?.takeIf { quad ->
            result.confidence >= requiredConfidence &&
                DocumentQuadPolicy.isReady(
                    quad = quad,
                    readiness = `in`.c1ph3rj.scanly.core.ml.QuadReadiness.LIVE_CAPTURE,
                    scanMode = scanMode,
                )
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
                } else if (scanMode == ScanMode.ID_CARD) {
                    "ID aligned. Tap capture whenever you are ready."
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
                statusMessage = when {
                    scanMode == ScanMode.ID_CARD && result.quad != null &&
                        DocumentQuadPolicy.idCardGuideFitScore(result.quad) < 0.72f ->
                        "Center the ID and align all four edges with the guide."
                    scanMode == ScanMode.ID_CARD ->
                        "Place the full ID inside the guide."
                    scanMode == ScanMode.BOOK ->
                        "Frame the full open spread."
                    else ->
                        "Point your camera at a full document."
                },
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
                    statusMessage = if (scanMode == ScanMode.ID_CARD) {
                        "Flip or replace the card before auto-capture re-arms."
                    } else {
                        "Move to the next page before auto-capture re-arms."
                    },
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
                statusMessage = if (scanMode == ScanMode.ID_CARD) {
                    "Hold the ID steady inside the guide."
                } else {
                    "Hold steady to auto-capture."
                },
                countdownValue = null,
                shouldAutoCapture = false,
            )
        }

        val requiredStableDuration = when (scanMode) {
            ScanMode.ID_CARD -> minOf(minStableDurationMillis, 1_500L)
            ScanMode.BOOK -> maxOf(minStableDurationMillis, 2_100L)
            ScanMode.DOCUMENT -> minStableDurationMillis
        }
        val stableDuration = nowMillis - stableSince
        if (stableDuration >= requiredStableDuration) {
            return StabilityEvaluation(
                phase = AutoCapturePhase.COUNTDOWN,
                statusMessage = "Capturing now.",
                countdownValue = 1,
                shouldAutoCapture = true,
            )
        }

        val countdownValue = when {
            stableDuration < requiredStableDuration / 3 -> 3
            stableDuration < (requiredStableDuration * 2) / 3 -> 2
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

    fun capturingState(
        autoCaptureEnabled: Boolean,
        scanMode: ScanMode = ScanMode.DOCUMENT,
    ): StabilityEvaluation = StabilityEvaluation(
        phase = AutoCapturePhase.CAPTURING,
        statusMessage = when {
            scanMode == ScanMode.ID_CARD && autoCaptureEnabled -> "Capturing ID…"
            scanMode == ScanMode.ID_CARD -> "Saving ID…"
            autoCaptureEnabled -> "Capturing page…"
            else -> "Saving page…"
        },
        countdownValue = null,
        shouldAutoCapture = false,
    )

    private fun resetStableWindow() {
        stableReferenceQuad = null
        stableSinceMillis = null
    }

}
