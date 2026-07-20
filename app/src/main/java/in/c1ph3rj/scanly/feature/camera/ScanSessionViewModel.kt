package `in`.c1ph3rj.scanly.feature.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.processing.LiveCapturePhase
import `in`.c1ph3rj.scanly.domain.processing.LiveDocumentAnalysisSession
import `in`.c1ph3rj.scanly.domain.processing.LiveFrameAnalysis
import `in`.c1ph3rj.scanly.domain.usecase.FinalizeCapturedPageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveAutomaticModelSelectionUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentGateEnabledUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveLiveDetectionModelUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PreparePageCaptureUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PrepareReplacementCaptureUseCase
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
        } ?: pages.maxWithOrNull(
            compareBy<ScanPage> { page -> page.createdAtMillis }.thenBy { page -> page.pageIndex },
        )

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
    private val liveAnalysisSession: LiveDocumentAnalysisSession,
    private val preparePageCaptureUseCase: PreparePageCaptureUseCase,
    private val prepareReplacementCaptureUseCase: PrepareReplacementCaptureUseCase,
    private val finalizeCapturedPageUseCase: FinalizeCapturedPageUseCase,
    observeLiveDetectionModelUseCase: ObserveLiveDetectionModelUseCase,
    observeAutomaticModelSelectionUseCase: ObserveAutomaticModelSelectionUseCase,
    observeDocumentGateEnabledUseCase: ObserveDocumentGateEnabledUseCase,
) : ViewModel() {
    private val documentId: String =
        checkNotNull(savedStateHandle[ScanSessionDestination.documentIdArgument])
    private val initialReplacementPageId: String? =
        savedStateHandle[ScanSessionDestination.replacePageIdArgument]
    private val analysisInFlight = AtomicBoolean(false)
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
                val model = liveAnalysisSession.resolveLiveModel(
                    manualModel = manualModel,
                    automaticSelectionEnabled = automaticSelectionEnabled,
                )
                liveAnalysisSession.setLiveModel(model)
                _uiState.update { current ->
                    current.copy(liveDetection = current.liveDetection.copy(model = model))
                }
            }
        }
        viewModelScope.launch {
            observeDocumentGateEnabledUseCase().collectLatest { enabled ->
                liveAnalysisSession.setDocumentGateEnabled(enabled)
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
                val autoCaptureEnabled = _uiState.value.liveDetection.autoCaptureEnabled
                val analysis = liveAnalysisSession.analyzeFrame(
                    frame = frame,
                    autoCaptureEnabled = autoCaptureEnabled,
                )
                applyFrameAnalysis(analysis)
                if (analysis.ok && analysis.shouldAutoCapture && !_uiState.value.captureInProgress) {
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
                    liveAnalysisSession.onCaptureCommitted(
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
                        phase = if (current.liveDetection.autoCaptureEnabled) {
                            AutoCapturePhase.COOLDOWN
                        } else {
                            AutoCapturePhase.OFF
                        },
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
                        phase = if (current.liveDetection.autoCaptureEnabled) {
                            AutoCapturePhase.SEARCHING
                        } else {
                            AutoCapturePhase.OFF
                        },
                        statusMessage = "Capture failed. Reposition the document and try again.",
                        countdownValue = null,
                        sceneIssue = null,
                    ),
                )
            }
            _events.emit(ScanSessionEvent.ShowMessage(message))
        }
    }

    private fun applyFrameAnalysis(analysis: LiveFrameAnalysis) {
        val frame = analysis.frame
        val overlayFrame = frame?.toDetectionOverlayFrame()
        // Scene issues only affect HUD tint; guidance is already in statusMessage.
        val sceneIssue = if (analysis.hasSceneIssue) {
            CaptureSceneIssue.entries.firstOrNull { issue ->
                analysis.statusMessage == issue.guidance
            } ?: CaptureSceneIssue.BLURRY
        } else {
            null
        }
        _uiState.update { current ->
            current.copy(
                liveDetection = current.liveDetection.copy(
                    quad = analysis.quad,
                    overlayFrame = overlayFrame,
                    sceneIssue = sceneIssue,
                    phase = analysis.phase.toUiPhase(),
                    statusMessage = analysis.statusMessage,
                    countdownValue = analysis.countdownValue,
                    model = analysis.model,
                    confidence = analysis.confidence,
                    inferenceMillis = analysis.inferenceMillis,
                    totalMillis = analysis.totalMillis,
                    gateClass = analysis.gateClass,
                    gatePhysicalProbability = analysis.gatePhysicalProbability,
                    gateMillis = analysis.gateMillis,
                    gateAccepted = analysis.gateAccepted,
                ),
            )
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
        liveAnalysisSession.prepareForCapture()
        val autoCaptureEnabled = _uiState.value.liveDetection.autoCaptureEnabled
        val captureState = liveAnalysisSession.capturingEvaluation(autoCaptureEnabled)
        _uiState.update { current ->
            current.copy(
                captureInProgress = true,
                liveDetection = current.liveDetection.copy(
                    phase = captureState.phase.toUiPhase(),
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
                            phase = if (current.liveDetection.autoCaptureEnabled) {
                                AutoCapturePhase.SEARCHING
                            } else {
                                AutoCapturePhase.OFF
                            },
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

internal fun LiveCapturePhase.toUiPhase(): AutoCapturePhase = when (this) {
    LiveCapturePhase.OFF -> AutoCapturePhase.OFF
    LiveCapturePhase.SEARCHING -> AutoCapturePhase.SEARCHING
    LiveCapturePhase.HOLD_STEADY -> AutoCapturePhase.HOLD_STEADY
    LiveCapturePhase.COUNTDOWN -> AutoCapturePhase.COUNTDOWN
    LiveCapturePhase.COOLDOWN -> AutoCapturePhase.COOLDOWN
    LiveCapturePhase.CAPTURING -> AutoCapturePhase.CAPTURING
}

internal fun replacementCompletionEvent(
    draft: PageCaptureDraft,
    capturedPageId: String,
): ScanSessionEvent.ReplacementCompleted? = if (draft.isReplacement) {
    ScanSessionEvent.ReplacementCompleted(capturedPageId)
} else {
    null
}
