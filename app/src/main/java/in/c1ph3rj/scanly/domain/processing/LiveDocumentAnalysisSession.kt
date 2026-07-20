package `in`.c1ph3rj.scanly.domain.processing

import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.DocumentGateClass
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel

/**
 * Domain-facing live camera analysis: gate + corners + stability for auto-capture.
 * Feature ViewModels map [LiveFrameAnalysis] into UI state; they must not inject ML detectors.
 */
interface LiveDocumentAnalysisSession {
    fun setLiveModel(model: DocumentCornerModel)

    fun setDocumentGateEnabled(enabled: Boolean)

    /** Resolves manual vs automatic live corner model selection. */
    suspend fun resolveLiveModel(
        manualModel: DocumentCornerModel,
        automaticSelectionEnabled: Boolean,
    ): DocumentCornerModel

    fun resetStability()

    /** Clears smoothed corners before a shutter capture (manual or auto). */
    fun prepareForCapture()

    fun onCaptureCommitted(quad: DocumentCornerQuad?, nowMillis: Long)

    fun capturingEvaluation(autoCaptureEnabled: Boolean): LiveStabilityEvaluation

    /**
     * Runs gate/corners/stability on one frame. Caller owns [frame] lifecycle;
     * implementation may copy bytes / create temporary bitmaps and must recycle them.
     */
    suspend fun analyzeFrame(
        frame: DetectionFrame,
        autoCaptureEnabled: Boolean,
        nowMillis: Long = System.currentTimeMillis(),
    ): LiveFrameAnalysis
}

enum class LiveCapturePhase {
    OFF,
    SEARCHING,
    HOLD_STEADY,
    COUNTDOWN,
    COOLDOWN,
    CAPTURING,
}

data class LiveStabilityEvaluation(
    val phase: LiveCapturePhase,
    val statusMessage: String,
    val countdownValue: Int?,
    val shouldAutoCapture: Boolean,
)

data class LiveFrameAnalysis(
    val ok: Boolean,
    /** Present when [ok] is false — user-visible fallback copy. */
    val failureStatusMessage: String? = null,
    val frame: DetectionFrame? = null,
    val quad: DocumentCornerQuad? = null,
    val phase: LiveCapturePhase = LiveCapturePhase.SEARCHING,
    val statusMessage: String = "",
    val countdownValue: Int? = null,
    val shouldAutoCapture: Boolean = false,
    val model: DocumentCornerModel = DocumentCornerModel.ACCURATE,
    val confidence: Float? = null,
    val inferenceMillis: Double? = null,
    val totalMillis: Double? = null,
    val gateClass: DocumentGateClass? = null,
    val gatePhysicalProbability: Float? = null,
    val gateMillis: Double? = null,
    val gateAccepted: Boolean = false,
    /** When true, UI treats status as a scene/quality warning (e.g. blur / dark). */
    val hasSceneIssue: Boolean = false,
)
