package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.AutomaticDocumentModelSelector
import `in`.c1ph3rj.scanly.core.ml.BookAwareCornerResolver
import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.CornerResolution
import `in`.c1ph3rj.scanly.core.ml.CornerResolutionIssue
import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.DocumentGateClass
import `in`.c1ph3rj.scanly.core.ml.DocumentGateDetector
import `in`.c1ph3rj.scanly.core.ml.DocumentGateResult
import `in`.c1ph3rj.scanly.core.ml.toOrientedBitmap
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.processing.LiveCapturePhase
import `in`.c1ph3rj.scanly.domain.processing.LiveDocumentAnalysisSession
import `in`.c1ph3rj.scanly.domain.processing.LiveFrameAnalysis
import `in`.c1ph3rj.scanly.domain.processing.LiveStabilityEvaluation
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Live gate + corner + stability orchestration for the scan session.
 * ViewModels depend on [LiveDocumentAnalysisSession], not on individual detectors.
 */
@Singleton
class DefaultLiveDocumentAnalysisSession @Inject constructor(
    private val bookAwareCornerResolver: BookAwareCornerResolver,
    private val documentGateDetector: DocumentGateDetector,
    private val automaticDocumentModelSelector: AutomaticDocumentModelSelector,
) : LiveDocumentAnalysisSession {

    private val stabilityTracker = CaptureStabilityTracker()
    private val gateStabilityTracker = DocumentGateStabilityTracker()
    private val stableCornerSelector = StableCornerSelector()

    @Volatile
    private var liveModel: DocumentCornerModel = DocumentCornerModel.ACCURATE

    @Volatile
    private var documentGateEnabled: Boolean = true

    override fun setLiveModel(model: DocumentCornerModel) {
        liveModel = model
        resetStability()
    }

    override fun setDocumentGateEnabled(enabled: Boolean) {
        documentGateEnabled = enabled
        resetStability()
    }

    override suspend fun resolveLiveModel(
        manualModel: DocumentCornerModel,
        automaticSelectionEnabled: Boolean,
    ): DocumentCornerModel {
        if (!automaticSelectionEnabled) return manualModel
        return runCatching { automaticDocumentModelSelector.selection().liveModel }
            .getOrDefault(manualModel)
    }

    override fun resetStability() {
        stabilityTracker.reset()
        gateStabilityTracker.reset()
        stableCornerSelector.reset()
    }

    override fun prepareForCapture() {
        // Match prior ViewModel behavior: only clear smoothed corners before shutter.
        stableCornerSelector.reset()
    }

    override fun onCaptureCommitted(quad: DocumentCornerQuad?, nowMillis: Long) {
        stabilityTracker.onCaptureCommitted(quad = quad, nowMillis = nowMillis)
    }

    override fun capturingEvaluation(autoCaptureEnabled: Boolean): LiveStabilityEvaluation =
        stabilityTracker.capturingState(autoCaptureEnabled).toDomain()

    override suspend fun analyzeFrame(
        frame: DetectionFrame,
        autoCaptureEnabled: Boolean,
        nowMillis: Long,
    ): LiveFrameAnalysis {
        val selectedModel = liveModel
        return runCatching {
            // Cheap frame-byte quality first: skip bitmap + ML when lens is blocked or scene is too dark.
            val quality = CaptureFrameQualityAnalyzer.analyze(frame)
            val earlySceneIssue = quality.sceneIssue(hasDocumentCandidate = false)
            if (
                earlySceneIssue == CaptureSceneIssue.LENS_BLOCKED ||
                earlySceneIssue == CaptureSceneIssue.TOO_DARK
            ) {
                stableCornerSelector.reset()
                gateStabilityTracker.reset()
                val empty = emptyDetection(selectedModel)
                val evaluation = stabilityTracker.evaluate(
                    result = empty,
                    autoCaptureEnabled = autoCaptureEnabled,
                    nowMillis = nowMillis,
                    sceneIssue = earlySceneIssue,
                )
                return@runCatching LiveFrameAnalysis(
                    ok = true,
                    frame = frame,
                    quad = null,
                    phase = evaluation.phase.toDomain(),
                    statusMessage = evaluation.statusMessage,
                    countdownValue = evaluation.countdownValue,
                    shouldAutoCapture = false,
                    model = selectedModel,
                    confidence = 0f,
                    hasSceneIssue = true,
                    gateAccepted = false,
                )
            }

            val bitmap = frame.toOrientedBitmap()
            try {
                val gate = if (documentGateEnabled) {
                    documentGateDetector.classify(bitmap)
                } else {
                    null
                }
                val gateAccepted = gate?.let(gateStabilityTracker::evaluate) ?: true
                if (gate == null) gateStabilityTracker.reset()
                val cornerResolution = if (gateAccepted) {
                    bookAwareCornerResolver.detect(bitmap, selectedModel)
                } else {
                    stableCornerSelector.reset()
                    CornerResolution(result = emptyDetection(selectedModel))
                }
                val detection = stableCornerSelector.select(cornerResolution.result)
                val sceneIssue = quality.sceneIssue(hasDocumentCandidate = detection.quad != null)
                val evaluation = stabilityTracker.evaluate(
                    result = detection,
                    autoCaptureEnabled = autoCaptureEnabled,
                    nowMillis = nowMillis,
                    sceneIssue = sceneIssue,
                )
                val statusMessage = gateGuidance(
                    sceneIssue = sceneIssue,
                    cornerIssue = cornerResolution.issue,
                    gate = gate,
                    gateAccepted = gateAccepted,
                ) ?: evaluation.statusMessage

                LiveFrameAnalysis(
                    ok = true,
                    frame = frame,
                    quad = detection.quad,
                    phase = evaluation.phase.toDomain(),
                    statusMessage = statusMessage,
                    countdownValue = evaluation.countdownValue,
                    shouldAutoCapture = evaluation.shouldAutoCapture,
                    model = detection.model,
                    confidence = detection.confidence,
                    inferenceMillis = detection.timing.inferenceMillis,
                    totalMillis = detection.timing.totalMillis,
                    gateClass = gate?.predictedClass,
                    gatePhysicalProbability = gate?.physicalDocumentProbability,
                    gateMillis = gate?.timing?.totalMillis,
                    gateAccepted = gateAccepted,
                    hasSceneIssue = sceneIssue != null,
                )
            } finally {
                bitmap.recycle()
            }
        }.getOrElse {
            stableCornerSelector.reset()
            LiveFrameAnalysis(
                ok = false,
                failureStatusMessage = "Live detection is unavailable. Manual capture still works.",
                frame = frame,
                phase = if (autoCaptureEnabled) LiveCapturePhase.SEARCHING else LiveCapturePhase.OFF,
                statusMessage = "Live detection is unavailable. Manual capture still works.",
                model = selectedModel,
            )
        }
    }

    private fun emptyDetection(model: DocumentCornerModel): CornerDetectionResult =
        CornerDetectionResult(
            quad = null,
            confidence = 0f,
            inferenceTimeMillis = 0L,
            modelName = model.displayName,
            model = model,
        )

    private fun gateGuidance(
        sceneIssue: CaptureSceneIssue?,
        cornerIssue: CornerResolutionIssue?,
        gate: DocumentGateResult?,
        gateAccepted: Boolean,
    ): String? = when {
        sceneIssue != null -> null
        cornerIssue == CornerResolutionIssue.TWO_PAGES_AMBIGUOUS ->
            "Two pages are visible. Move closer and frame one page."
        cornerIssue == CornerResolutionIssue.MODELS_DISAGREE ->
            "Page edges are ambiguous. Hold steady or move closer."
        gateAccepted -> null
        gate?.predictedClass == DocumentGateClass.DIGITAL_SCREEN ->
            "Screen detected. Point at a physical document."
        gate?.predictedClass == DocumentGateClass.NEITHER ->
            "Point your camera at a physical document."
        else -> "Checking document…"
    }
}

private fun StabilityEvaluation.toDomain(): LiveStabilityEvaluation = LiveStabilityEvaluation(
    phase = phase.toDomain(),
    statusMessage = statusMessage,
    countdownValue = countdownValue,
    shouldAutoCapture = shouldAutoCapture,
)

private fun AutoCapturePhase.toDomain(): LiveCapturePhase = when (this) {
    AutoCapturePhase.OFF -> LiveCapturePhase.OFF
    AutoCapturePhase.SEARCHING -> LiveCapturePhase.SEARCHING
    AutoCapturePhase.HOLD_STEADY -> LiveCapturePhase.HOLD_STEADY
    AutoCapturePhase.COUNTDOWN -> LiveCapturePhase.COUNTDOWN
    AutoCapturePhase.COOLDOWN -> LiveCapturePhase.COOLDOWN
    AutoCapturePhase.CAPTURING -> LiveCapturePhase.CAPTURING
}
