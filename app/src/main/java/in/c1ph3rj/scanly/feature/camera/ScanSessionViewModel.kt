package `in`.c1ph3rj.scanly.feature.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.BookAwareCornerResolver
import `in`.c1ph3rj.scanly.core.ml.AutomaticDocumentModelSelector
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.DocumentGateClass
import `in`.c1ph3rj.scanly.core.ml.DocumentGateDetector
import `in`.c1ph3rj.scanly.core.ml.DocumentGateResult
import `in`.c1ph3rj.scanly.core.ml.CornerResolutionIssue
import `in`.c1ph3rj.scanly.core.ml.toOrientedBitmap
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.usecase.ObserveLiveDetectionModelUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveAutomaticModelSelectionUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentGateEnabledUseCase
import `in`.c1ph3rj.scanly.domain.usecase.FinalizeCapturedPageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PreparePageCaptureUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PrepareReplacementCaptureUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

data class ScanSessionUiState(
    val document: ScanDocument? = null,
    val pages: List<ScanPage> = emptyList(),
    val captureInProgress: Boolean = false,
    val missingDocument: Boolean = false,
    val replacementPageId: String? = null,
    val latestCapturedPageId: String? = null,
    val liveDetection: LiveDetectionUiState = LiveDetectionUiState(),
) {
    val replacementPage: ScanPage?
        get() = pages.firstOrNull { page -> page.id == replacementPageId }

    val latestCapturedPage: ScanPage?
        get() = latestCapturedPageId?.let { pageId ->
            pages.firstOrNull { page -> page.id == pageId }
        } ?: pages.maxWithOrNull(compareBy<ScanPage> { page -> page.createdAtMillis }.thenBy { page -> page.pageIndex })

    val isReplacementMode: Boolean
        get() = replacementPageId != null
}

sealed interface ScanSessionEvent {
    data class PerformCapture(val draft: PageCaptureDraft) : ScanSessionEvent
    data class ShowMessage(val message: String) : ScanSessionEvent
    data class ReplacementCompleted(val pageId: String) : ScanSessionEvent
    data object NavigateUp : ScanSessionEvent
}

private enum class CaptureTrigger {
    MANUAL,
    AUTO,
}

@HiltViewModel
class ScanSessionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDocumentUseCase: ObserveDocumentUseCase,
    observeDocumentPagesUseCase: ObserveDocumentPagesUseCase,
    private val bookAwareCornerResolver: BookAwareCornerResolver,
    private val documentGateDetector: DocumentGateDetector,
    private val automaticDocumentModelSelector: AutomaticDocumentModelSelector,
    private val preparePageCaptureUseCase: PreparePageCaptureUseCase,
    private val prepareReplacementCaptureUseCase: PrepareReplacementCaptureUseCase,
    private val finalizeCapturedPageUseCase: FinalizeCapturedPageUseCase,
    observeLiveDetectionModelUseCase: ObserveLiveDetectionModelUseCase,
    observeAutomaticModelSelectionUseCase: ObserveAutomaticModelSelectionUseCase,
    observeDocumentGateEnabledUseCase: ObserveDocumentGateEnabledUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[ScanSessionDestination.documentIdArgument])
    private val initialReplacementPageId: String? = savedStateHandle[ScanSessionDestination.replacePageIdArgument]
    private val analysisInFlight = AtomicBoolean(false)
    private val stabilityTracker = CaptureStabilityTracker()
    private val gateStabilityTracker = DocumentGateStabilityTracker()
    private val stableCornerSelector = StableCornerSelector()
    @Volatile
    private var liveModel: DocumentCornerModel = DocumentCornerModel.ACCURATE
    @Volatile
    private var documentGateEnabled: Boolean = true
    private var pendingCaptureQuad: DocumentCornerQuad? = null
    private var pendingCaptureTrigger: CaptureTrigger = CaptureTrigger.MANUAL
    private val _uiState = MutableStateFlow(
        ScanSessionUiState(replacementPageId = initialReplacementPageId),
    )
    val uiState: StateFlow<ScanSessionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScanSessionEvent>()
    val events: SharedFlow<ScanSessionEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                observeLiveDetectionModelUseCase(),
                observeAutomaticModelSelectionUseCase(),
            ) { manualModel, automaticSelectionEnabled ->
                manualModel to automaticSelectionEnabled
            }.collectLatest { (manualModel, automaticSelectionEnabled) ->
                val model = if (automaticSelectionEnabled) {
                    runCatching { automaticDocumentModelSelector.selection().liveModel }
                        .getOrDefault(manualModel)
                } else {
                    manualModel
                }
                liveModel = model
                stabilityTracker.reset()
                gateStabilityTracker.reset()
                stableCornerSelector.reset()
                _uiState.update { current ->
                    current.copy(liveDetection = current.liveDetection.copy(model = model))
                }
            }
        }
        viewModelScope.launch {
            observeDocumentGateEnabledUseCase().collectLatest { enabled ->
                documentGateEnabled = enabled
                stabilityTracker.reset()
                gateStabilityTracker.reset()
                stableCornerSelector.reset()
            }
        }
        viewModelScope.launch {
            observeDocumentUseCase(documentId).collectLatest { document ->
                _uiState.update { current ->
                    current.copy(
                        document = document,
                        missingDocument = document == null,
                    )
                }
            }
        }
        viewModelScope.launch {
            observeDocumentPagesUseCase(documentId).collectLatest { pages ->
                _uiState.update { current ->
                    current.copy(
                        pages = pages,
                        replacementPageId = current.replacementPageId?.takeIf { pageId ->
                            pages.isEmpty() || pages.any { page -> page.id == pageId }
                        },
                        latestCapturedPageId = current.latestCapturedPageId?.takeIf { pageId ->
                            pages.any { page -> page.id == pageId }
                        },
                    )
                }
            }
        }
    }

    fun onGridEnabledChanged(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                liveDetection = current.liveDetection.copy(
                    isGridEnabled = enabled,
                ),
            )
        }
    }

    fun requestCapture() {
        viewModelScope.launch {
            startCapture(CaptureTrigger.MANUAL)
        }
    }

    fun onAutoCaptureEnabledChanged(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                liveDetection = current.liveDetection.copy(
                    autoCaptureEnabled = enabled,
                    phase = if (enabled) AutoCapturePhase.SEARCHING else AutoCapturePhase.OFF,
                    statusMessage = if (enabled) {
                        "Point your camera at a document."
                    } else {
                        "Auto-capture is off. Use the shutter when ready."
                    },
                    countdownValue = null,
                ),
            )
        }
    }

    fun onReplacementPageSelected(pageId: String?) {
        _uiState.update { current ->
            current.copy(replacementPageId = pageId)
        }
    }

    fun clearReplacementSelection() {
        onReplacementPageSelected(pageId = null)
    }

    fun onPreviewFrame(frameProvider: () -> DetectionFrame?): Boolean {
        if (_uiState.value.missingDocument || _uiState.value.captureInProgress) {
            return false
        }
        if (!analysisInFlight.compareAndSet(false, true)) {
            return false
        }
        val frame = runCatching(frameProvider).getOrNull()
        if (frame == null) {
            analysisInFlight.set(false)
            return false
        }

        viewModelScope.launch {
            try {
                val analysis = runCatching {
                    withContext(Dispatchers.Default) {
                        val selectedModel = liveModel
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
                                `in`.c1ph3rj.scanly.core.ml.CornerResolution(
                                    result = CornerDetectionResult(
                                        quad = null,
                                        confidence = 0f,
                                        inferenceTimeMillis = 0L,
                                        modelName = selectedModel.displayName,
                                        model = selectedModel,
                                    ),
                                )
                            }
                            val detection = stableCornerSelector.select(cornerResolution.result)
                            val quality = CaptureFrameQualityAnalyzer.analyze(frame)
                            FrameAnalysis(
                                detection = detection,
                                gate = gate,
                                gateAccepted = gateAccepted,
                                sceneIssue = quality.sceneIssue(hasDocumentCandidate = detection.quad != null),
                                cornerIssue = cornerResolution.issue,
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }.getOrElse {
                    stableCornerSelector.reset()
                    _uiState.update { current ->
                        current.copy(
                            liveDetection = current.liveDetection.copy(
                                quad = null,
                                overlayFrame = frame.toDetectionOverlayFrame(),
                                sceneIssue = null,
                                phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.SEARCHING else AutoCapturePhase.OFF,
                                statusMessage = "Live detection is unavailable. Manual capture still works.",
                                countdownValue = null,
                            ),
                        )
                    }
                    return@launch
                }
                val detectionResult = analysis.detection
                val sceneIssue = analysis.sceneIssue

                val autoCaptureEnabled = _uiState.value.liveDetection.autoCaptureEnabled
                val evaluation = stabilityTracker.evaluate(
                    result = detectionResult,
                    autoCaptureEnabled = autoCaptureEnabled,
                    nowMillis = System.currentTimeMillis(),
                    sceneIssue = sceneIssue,
                )

                _uiState.update { current ->
                    current.copy(
                        liveDetection = current.liveDetection.copy(
                            quad = detectionResult.quad,
                            overlayFrame = frame.toDetectionOverlayFrame(),
                            sceneIssue = sceneIssue,
                            phase = evaluation.phase,
                            statusMessage = gateGuidance(analysis) ?: evaluation.statusMessage,
                            countdownValue = evaluation.countdownValue,
                            model = detectionResult.model,
                            confidence = detectionResult.confidence,
                            inferenceMillis = detectionResult.timing.inferenceMillis,
                            totalMillis = detectionResult.timing.totalMillis,
                            gateClass = analysis.gate?.predictedClass,
                            gatePhysicalProbability = analysis.gate?.physicalDocumentProbability,
                            gateMillis = analysis.gate?.timing?.totalMillis,
                            gateAccepted = analysis.gateAccepted,
                        ),
                    )
                }

                if (evaluation.shouldAutoCapture && !_uiState.value.captureInProgress) {
                    startCapture(CaptureTrigger.AUTO)
                }
            } finally {
                analysisInFlight.set(false)
            }
        }
        return true
    }

    fun onCaptureSaved(draft: PageCaptureDraft) {
        viewModelScope.launch {
            var capturedPageId: String? = null
            var completionEvent: ScanSessionEvent? = null
            when (val result = finalizeCapturedPageUseCase(draft)) {
                is ScanlyResult.Success -> {
                    capturedPageId = result.value
                    stabilityTracker.onCaptureCommitted(
                        quad = pendingCaptureQuad,
                        nowMillis = System.currentTimeMillis(),
                    )
                    completionEvent = replacementCompletionEvent(
                        draft = draft,
                        capturedPageId = result.value,
                    ) ?: run {
                        ScanSessionEvent.ShowMessage(
                            captureSuccessMessage(
                                draft = draft,
                                trigger = pendingCaptureTrigger,
                            ),
                        )
                    }
                }
                is ScanlyResult.Failure -> _events.emit(ScanSessionEvent.ShowMessage(result.error.message))
            }
            pendingCaptureQuad = null
            pendingCaptureTrigger = CaptureTrigger.MANUAL
            _uiState.update { current ->
                current.copy(
                    captureInProgress = false,
                    latestCapturedPageId = capturedPageId ?: current.latestCapturedPageId,
                    liveDetection = current.liveDetection.copy(
                        phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.COOLDOWN else AutoCapturePhase.OFF,
                        statusMessage = if (current.liveDetection.autoCaptureEnabled) {
                            "Move to the next page before auto-capture re-arms."
                        } else {
                            "Auto-capture is off. Use the shutter when ready."
                        },
                        countdownValue = null,
                        sceneIssue = null,
                    ),
                )
            }
            completionEvent?.let { event -> _events.emit(event) }
        }
    }

    fun onCaptureFailed(message: String) {
        viewModelScope.launch {
            pendingCaptureQuad = null
            pendingCaptureTrigger = CaptureTrigger.MANUAL
            _uiState.update { current ->
                current.copy(
                    captureInProgress = false,
                    liveDetection = current.liveDetection.copy(
                        phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.SEARCHING else AutoCapturePhase.OFF,
                        statusMessage = "Capture failed. Reposition the document and try again.",
                        countdownValue = null,
                        sceneIssue = null,
                    ),
                )
            }
            _events.emit(ScanSessionEvent.ShowMessage(message))
        }
    }

    private suspend fun startCapture(trigger: CaptureTrigger) {
        if (_uiState.value.captureInProgress || _uiState.value.document == null) {
            if (_uiState.value.document == null) {
                emitMessage("Document not found.")
            }
            return
        }

        pendingCaptureTrigger = trigger
        pendingCaptureQuad = _uiState.value.liveDetection.quad
        stableCornerSelector.reset()
        val autoCaptureEnabled = _uiState.value.liveDetection.autoCaptureEnabled
        val captureState = stabilityTracker.capturingState(autoCaptureEnabled)
        _uiState.update { current ->
            current.copy(
                captureInProgress = true,
                liveDetection = current.liveDetection.copy(
                    phase = captureState.phase,
                    statusMessage = captureState.statusMessage,
                    countdownValue = captureState.countdownValue,
                ),
            )
        }

        val replacementPageId = _uiState.value.replacementPageId
        val capturePreparationResult = if (replacementPageId != null) {
            prepareReplacementCaptureUseCase(replacementPageId)
        } else {
            preparePageCaptureUseCase(documentId)
        }

        when (val result = capturePreparationResult) {
            is ScanlyResult.Success -> _events.emit(ScanSessionEvent.PerformCapture(result.value))
            is ScanlyResult.Failure -> {
                pendingCaptureQuad = null
                pendingCaptureTrigger = CaptureTrigger.MANUAL
                _uiState.update { current ->
                    current.copy(
                        captureInProgress = false,
                        liveDetection = current.liveDetection.copy(
                            phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.SEARCHING else AutoCapturePhase.OFF,
                            statusMessage = result.error.message,
                            countdownValue = null,
                        ),
                    )
                }
                _events.emit(ScanSessionEvent.ShowMessage(result.error.message))
            }
        }
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(ScanSessionEvent.ShowMessage(message))
        }
    }

    private fun captureSuccessMessage(
        draft: PageCaptureDraft,
        trigger: CaptureTrigger,
    ): String = when {
        trigger == CaptureTrigger.AUTO -> "Auto-captured page ${draft.pageIndex + 1}."
        else -> "Page ${draft.pageIndex + 1} saved."
    }

}

private data class FrameAnalysis(
    val detection: CornerDetectionResult,
    val gate: DocumentGateResult?,
    val gateAccepted: Boolean,
    val sceneIssue: CaptureSceneIssue?,
    val cornerIssue: CornerResolutionIssue?,
)

private fun gateGuidance(analysis: FrameAnalysis): String? = when {
    analysis.sceneIssue != null -> null
    analysis.cornerIssue == CornerResolutionIssue.TWO_PAGES_AMBIGUOUS ->
        "Two pages are visible. Move closer and frame one page."
    analysis.cornerIssue == CornerResolutionIssue.MODELS_DISAGREE ->
        "Page edges are ambiguous. Hold steady or move closer."
    analysis.gateAccepted -> null
    analysis.gate?.predictedClass == DocumentGateClass.DIGITAL_SCREEN ->
        "Screen detected. Point at a physical document."
    analysis.gate?.predictedClass == DocumentGateClass.NEITHER ->
        "Point your camera at a physical document."
    else -> "Checking document…"
}

internal fun replacementCompletionEvent(
    draft: PageCaptureDraft,
    capturedPageId: String,
): ScanSessionEvent.ReplacementCompleted? = if (draft.isReplacement) {
    ScanSessionEvent.ReplacementCompleted(capturedPageId)
} else {
    null
}
