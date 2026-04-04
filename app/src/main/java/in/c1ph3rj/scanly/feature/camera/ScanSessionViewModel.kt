package `in`.c1ph3rj.scanly.feature.camera

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerDetector
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
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
    val liveDetection: LiveDetectionUiState = LiveDetectionUiState(),
) {
    val replacementPage: ScanPage?
        get() = pages.firstOrNull { page -> page.id == replacementPageId }

    val isReplacementMode: Boolean
        get() = replacementPageId != null
}

sealed interface ScanSessionEvent {
    data class PerformCapture(val draft: PageCaptureDraft) : ScanSessionEvent
    data class ShowMessage(val message: String) : ScanSessionEvent
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
    private val documentCornerDetector: DocumentCornerDetector,
    private val preparePageCaptureUseCase: PreparePageCaptureUseCase,
    private val prepareReplacementCaptureUseCase: PrepareReplacementCaptureUseCase,
    private val finalizeCapturedPageUseCase: FinalizeCapturedPageUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[ScanSessionDestination.documentIdArgument])
    private val initialReplacementPageId: String? = savedStateHandle[ScanSessionDestination.replacePageIdArgument]
    private val analysisInFlight = AtomicBoolean(false)
    private val stabilityTracker = CaptureStabilityTracker()
    private var pendingCaptureQuad: DocumentCornerQuad? = null
    private var pendingCaptureTrigger: CaptureTrigger = CaptureTrigger.MANUAL
    private var replacementCaptureCompleted = false
    private val launchedInReplacementMode: Boolean = initialReplacementPageId != null

    private val _uiState = MutableStateFlow(
        ScanSessionUiState(
            replacementPageId = initialReplacementPageId,
        ),
    )
    val uiState: StateFlow<ScanSessionUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScanSessionEvent>()
    val events: SharedFlow<ScanSessionEvent> = _events.asSharedFlow()

    init {
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
                    current.copy(pages = pages)
                }
                val selectedReplacementId = _uiState.value.replacementPageId
                if (selectedReplacementId != null &&
                    pages.none { page -> page.id == selectedReplacementId } &&
                    !replacementCaptureCompleted
                ) {
                    _uiState.update { current -> current.copy(replacementPageId = null) }
                    emitMessage("The selected page is no longer available for retake.")
                    if (launchedInReplacementMode && selectedReplacementId == initialReplacementPageId) {
                        _events.emit(ScanSessionEvent.NavigateUp)
                    }
                }
            }
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

    fun onPreviewFrame(frame: DetectionFrame) {
        if (_uiState.value.missingDocument || _uiState.value.captureInProgress) {
            return
        }
        if (!analysisInFlight.compareAndSet(false, true)) {
            return
        }

        viewModelScope.launch {
            try {
                val detectionResult = runCatching {
                    withContext(Dispatchers.Default) {
                        documentCornerDetector.detect(frame)
                    }
                }.getOrElse {
                    _uiState.update { current ->
                        current.copy(
                            liveDetection = current.liveDetection.copy(
                                quad = null,
                                frameWidth = orientedWidth(frame),
                                frameHeight = orientedHeight(frame),
                                confidence = null,
                                inferenceTimeMillis = null,
                                phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.SEARCHING else AutoCapturePhase.OFF,
                                statusMessage = "Live detection is unavailable. Manual capture still works.",
                                countdownValue = null,
                            ),
                        )
                    }
                    return@launch
                }

                val autoCaptureEnabled = _uiState.value.liveDetection.autoCaptureEnabled
                val evaluation = stabilityTracker.evaluate(
                    result = detectionResult,
                    autoCaptureEnabled = autoCaptureEnabled,
                    nowMillis = System.currentTimeMillis(),
                )

                _uiState.update { current ->
                    current.copy(
                        liveDetection = current.liveDetection.copy(
                            quad = detectionResult.quad,
                            frameWidth = orientedWidth(frame),
                            frameHeight = orientedHeight(frame),
                            confidence = detectionResult.confidence.takeIf { it > 0f },
                            inferenceTimeMillis = detectionResult.inferenceTimeMillis,
                            phase = evaluation.phase,
                            statusMessage = evaluation.statusMessage,
                            countdownValue = evaluation.countdownValue,
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
    }

    fun onCaptureSaved(draft: PageCaptureDraft) {
        viewModelScope.launch {
            when (val result = finalizeCapturedPageUseCase(draft)) {
                is ScanlyResult.Success -> {
                    stabilityTracker.onCaptureCommitted(
                        quad = pendingCaptureQuad,
                        nowMillis = System.currentTimeMillis(),
                    )
                    replacementCaptureCompleted = draft.isReplacement
                    _events.emit(
                        ScanSessionEvent.ShowMessage(
                            captureSuccessMessage(
                                draft = draft,
                                trigger = pendingCaptureTrigger,
                            ),
                        ),
                    )
                    if (draft.isReplacement && launchedInReplacementMode) {
                        _events.emit(ScanSessionEvent.NavigateUp)
                    }
                }
                is ScanlyResult.Failure -> _events.emit(ScanSessionEvent.ShowMessage(result.error.message))
            }
            pendingCaptureQuad = null
            pendingCaptureTrigger = CaptureTrigger.MANUAL
            _uiState.update { current ->
                current.copy(
                    captureInProgress = false,
                    replacementPageId = when {
                        draft.isReplacement && !launchedInReplacementMode -> null
                        else -> current.replacementPageId
                    },
                    liveDetection = current.liveDetection.copy(
                        phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.COOLDOWN else AutoCapturePhase.OFF,
                        statusMessage = if (current.liveDetection.autoCaptureEnabled) {
                            "Move to the next page before auto-capture re-arms."
                        } else {
                            "Auto-capture is off. Use the shutter when ready."
                        },
                        countdownValue = null,
                    ),
                )
            }
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

        val replacementTargetPageId = _uiState.value.replacementPageId
        val capturePreparationResult = replacementTargetPageId?.let { pageId ->
            prepareReplacementCaptureUseCase(pageId)
        } ?: preparePageCaptureUseCase(documentId)

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
        draft.isReplacement && trigger == CaptureTrigger.AUTO -> "Auto-replaced page ${draft.pageIndex + 1}."
        draft.isReplacement -> "Page ${draft.pageIndex + 1} replaced."
        trigger == CaptureTrigger.AUTO -> "Auto-captured page ${draft.pageIndex + 1}."
        else -> "Page ${draft.pageIndex + 1} saved."
    }

    private fun orientedWidth(frame: DetectionFrame): Int =
        if (frame.rotationDegrees % 180 == 0) frame.width else frame.height

    private fun orientedHeight(frame: DetectionFrame): Int =
        if (frame.rotationDegrees % 180 == 0) frame.height else frame.width
}
