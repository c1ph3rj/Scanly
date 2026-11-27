package `in`.c1ph3rj.scanly.feature.camera

import android.os.SystemClock
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
import `in`.c1ph3rj.scanly.domain.usecase.SetDocumentScanModeUseCase
import `in`.c1ph3rj.scanly.domain.model.IdCardSide
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString

data class ScanSessionUiState(
    val document: ScanDocument? = null,
    val pages: List<ScanPage> = emptyList(),
    val captureInProgress: Boolean = false,
    val missingDocument: Boolean = false,
    val replacementPageId: String? = null,
    val latestCapturedPageId: String? = null,
    val liveDetection: LiveDetectionUiState = LiveDetectionUiState(),
    val scanMode: ScanMode = ScanMode.DOCUMENT,
    /** The ID card being captured: its pair id once the front is saved, and the side to capture next. */
    val idCard: IdCardCaptureState = IdCardCaptureState(),
) {
    val replacementPage: ScanPage?
        get() = pages.firstOrNull { page -> page.id == replacementPageId }

    val latestCapturedPage: ScanPage?
        get() = latestCapturedPageId?.let { pageId ->
            pages.firstOrNull { page -> page.id == pageId }
        } ?: pages.maxWithOrNull(compareBy<ScanPage> { page -> page.createdAtMillis }.thenBy { page -> page.pageIndex })

    val isReplacementMode: Boolean
        get() = replacementPageId != null

    /** The mode can't change while a capture runs or while retaking (a retake keeps the page's mode). */
    val scanModeLocked: Boolean
        get() = captureInProgress || isReplacementMode
}

data class IdCardCaptureState(
    val pairId: String? = null,
    val nextSide: IdCardSide = IdCardSide.FRONT,
)

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
    private val setDocumentScanModeUseCase: SetDocumentScanModeUseCase,
    observeLiveDetectionModelUseCase: ObserveLiveDetectionModelUseCase,
    observeAutomaticModelSelectionUseCase: ObserveAutomaticModelSelectionUseCase,
    observeDocumentGateEnabledUseCase: ObserveDocumentGateEnabledUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[ScanSessionDestination.documentIdArgument])
    private val initialReplacementPageId: String? = savedStateHandle[ScanSessionDestination.replacePageIdArgument]
    private val analysisInFlight = AtomicBoolean(false)
    /** The active scan mode's detection rules; replaced (with fresh trackers) on a mode change. */
    @Volatile
    private var strategy: CaptureModeStrategy = CaptureModeStrategy.forMode(ScanMode.DOCUMENT)
    private var stabilityTracker = strategy.newStabilityTracker()
    private var gateStabilityTracker = strategy.newGateTracker()
    private var stableCornerSelector = strategy.newCornerSelector()
    /** Set once the mode has been taken from the document (or the retaken page). */
    private var scanModeResolved = false
    /**
     * Guards [gateStabilityTracker] and [stableCornerSelector]: frames update them on
     * Dispatchers.Default while settings changes and captures reset them on the main thread.
     */
    private val trackerLock = Any()
    /** Bumped on every selector reset; a frame analyzed under an older value is discarded. */
    private val trackerGeneration = AtomicInteger(0)
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

    // A buffered channel, not a SharedFlow: the screen's collector restarts whenever the camera
    // rebinds, and a dropped PerformCapture would leave captureInProgress stuck (and Back blocked).
    private val _events = Channel<ScanSessionEvent>(Channel.BUFFERED)
    val events: Flow<ScanSessionEvent> = _events.receiveAsFlow()

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
                // Keep cooldown + captured-page guard: a settings change must not re-capture the page.
                stabilityTracker.resetTracking()
                resetFrameTrackers()
                _uiState.update { current ->
                    current.copy(liveDetection = current.liveDetection.copy(model = model, quad = null))
                }
            }
        }
        viewModelScope.launch {
            observeDocumentGateEnabledUseCase().collectLatest { enabled ->
                documentGateEnabled = enabled
                stabilityTracker.resetTracking()
                resetFrameTrackers()
                _uiState.update { current ->
                    current.copy(liveDetection = current.liveDetection.copy(quad = null))
                }
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
                resolveInitialScanMode()
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
                resolveInitialScanMode()
            }
        }
    }

    /**
     * Switches the camera to [mode] and remembers it for the document. Ignored while the mode is
     * locked (capture running, retake).
     */
    fun onScanModeChanged(mode: ScanMode) {
        val current = _uiState.value
        if (current.scanModeLocked || current.scanMode == mode) return
        scanModeResolved = true
        applyScanMode(mode, current.pages)
        viewModelScope.launch { setDocumentScanModeUseCase(documentId, mode) }
    }

    /** Ends the current ID card with its front only; the next capture starts a new card. */
    fun onSkipIdCardBack() {
        _uiState.update { current ->
            if (current.scanMode != ScanMode.ID_CARD) return@update current
            current.copy(
                idCard = IdCardCaptureState(),
                liveDetection = current.liveDetection.copy(
                    statusMessage = idSearchMessage(IdCardSide.FRONT),
                ),
            )
        }
    }

    /** The first document (and, for a retake, page) emission decides the mode; later ones don't. */
    private fun resolveInitialScanMode() {
        if (scanModeResolved) return
        val current = _uiState.value
        val document = current.document ?: return
        val replacementId = current.replacementPageId
        val mode = if (replacementId != null) {
            // Wait for the page list so the retake uses the page's own mode.
            val page = current.pages.firstOrNull { it.id == replacementId } ?: return
            page.scanMode
        } else {
            document.preferredScanMode
        }
        scanModeResolved = true
        applyScanMode(mode, current.pages)
    }

    private fun applyScanMode(mode: ScanMode, pages: List<ScanPage>) {
        val next = CaptureModeStrategy.forMode(mode)
        synchronized(trackerLock) {
            strategy = next
            trackerGeneration.incrementAndGet()
            gateStabilityTracker = next.newGateTracker()
            stableCornerSelector = next.newCornerSelector()
        }
        stabilityTracker = next.newStabilityTracker()
        _uiState.update { current ->
            current.copy(
                scanMode = mode,
                idCard = if (mode == ScanMode.ID_CARD) resolveIdCaptureState(pages) else IdCardCaptureState(),
                liveDetection = current.liveDetection.copy(
                    quad = null,
                    countdownValue = null,
                    sceneIssue = null,
                    phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.SEARCHING else AutoCapturePhase.OFF,
                    statusMessage = searchingMessage(mode, current.liveDetection.autoCaptureEnabled),
                ),
            )
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
                    statusMessage = searchingMessage(current.scanMode, enabled),
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

        val generation = trackerGeneration.get()
        viewModelScope.launch {
            try {
                val analysis = runCatching {
                    withContext(Dispatchers.Default) {
                        val selectedModel = liveModel
                        val activeStrategy = strategy
                        val bitmap = frame.toOrientedBitmap()
                        try {
                            val gate = if (documentGateEnabled) {
                                documentGateDetector.classify(bitmap)
                            } else {
                                null
                            }
                            val gateAccepted = synchronized(trackerLock) {
                                if (gate == null) {
                                    gateStabilityTracker.reset()
                                    true
                                } else {
                                    gateStabilityTracker.evaluate(gate)
                                }
                            }
                            val cornerResolution = if (gateAccepted) {
                                bookAwareCornerResolver.detect(
                                    bitmap = bitmap,
                                    selectedModel = selectedModel,
                                    bookAnalysis = activeStrategy.bookAnalysis,
                                    liveRules = activeStrategy.quadRules,
                                )
                            } else {
                                synchronized(trackerLock) { stableCornerSelector.reset() }
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
                            // A reset (capture start, settings change) while this frame was in
                            // flight makes it stale: do not feed it into the fresh selector.
                            val selection = synchronized(trackerLock) {
                                if (trackerGeneration.get() != generation) {
                                    null
                                } else {
                                    stableCornerSelector.selectTracked(cornerResolution.result)
                                }
                            } ?: return@withContext null
                            val detection = selection.result
                            val quality = CaptureFrameQualityAnalyzer.analyze(frame, checkSharpness = detection.quad != null)
                            FrameAnalysis(
                                detection = detection,
                                isFreshDetection = selection.isFresh,
                                gate = gate,
                                gateAccepted = gateAccepted,
                                sceneIssue = quality.sceneIssue(
                                    hasDocumentCandidate = detection.quad != null,
                                    checkGlare = activeStrategy.detectGlare,
                                ),
                                cornerIssue = cornerResolution.issue,
                            )
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }.getOrElse {
                    if (isStaleFrame(generation)) return@launch
                    resetCornerSelector()
                    stabilityTracker.resetTracking()
                    _uiState.update { current ->
                        current.copy(
                            liveDetection = current.liveDetection.copy(
                                quad = null,
                                overlayFrame = frame.toDetectionOverlayFrame(),
                                sceneIssue = null,
                                phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.SEARCHING else AutoCapturePhase.OFF,
                                statusMessage = appString(R.string.scan_session_live_detection_is_unavailable_manual),
                                countdownValue = null,
                            ),
                        )
                    }
                    return@launch
                }
                // Dropped when a reset happened meanwhile, or a capture started: writing it would
                // overwrite the CAPTURING state and show a pre-capture quad.
                if (analysis == null || isStaleFrame(generation)) return@launch
                val detectionResult = analysis.detection
                val sceneIssue = analysis.sceneIssue
                val overlayFrame = frame.toDetectionOverlayFrame()
                // Detection sees the whole frame; the user (and the saved crop) only sees the viewport.
                val quadWithinVisibleRegion = detectionResult.quad
                    ?.isWithinVisibleRegion(overlayFrame, VISIBLE_REGION_MARGIN) == true

                val autoCaptureEnabled = _uiState.value.liveDetection.autoCaptureEnabled
                val evaluation = stabilityTracker.evaluate(
                    result = detectionResult,
                    autoCaptureEnabled = autoCaptureEnabled,
                    nowMillis = monotonicNowMillis(),
                    sceneIssue = sceneIssue,
                    isFreshDetection = analysis.isFreshDetection,
                    quadWithinVisibleRegion = quadWithinVisibleRegion,
                )

                _uiState.update { current ->
                    current.copy(
                        liveDetection = current.liveDetection.copy(
                            quad = detectionResult.quad,
                            overlayFrame = overlayFrame,
                            sceneIssue = sceneIssue,
                            phase = evaluation.phase,
                            statusMessage = gateGuidance(analysis)
                                ?: modeStatusMessage(current, evaluation),
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
            var failureMessage: String? = null
            when (val result = finalizeCapturedPageUseCase(draft)) {
                is ScanlyResult.Success -> {
                    capturedPageId = result.value
                    if (!draft.isReplacement) advanceIdCard(draft)
                    stabilityTracker.onCaptureCommitted(
                        quad = pendingCaptureQuad,
                        nowMillis = monotonicNowMillis(),
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
                is ScanlyResult.Failure -> {
                    failureMessage = result.error.message
                    // Back off like a commit so a persistent failure (e.g. disk full) does not
                    // re-trigger auto-capture every time the still scene turns stable again.
                    applyFailureCooldown()
                    _events.send(ScanSessionEvent.ShowMessage(result.error.message))
                }
            }
            pendingCaptureQuad = null
            pendingCaptureTrigger = CaptureTrigger.MANUAL
            _uiState.update { current ->
                current.copy(
                    captureInProgress = false,
                    latestCapturedPageId = capturedPageId ?: current.latestCapturedPageId,
                    liveDetection = current.liveDetection.copy(
                        phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.COOLDOWN else AutoCapturePhase.OFF,
                        statusMessage = if (failureMessage != null) {
                            appString(R.string.scan_session_page_not_saved_reposition_the)
                        } else if (current.liveDetection.autoCaptureEnabled) {
                            nextSubjectMessage(current)
                        } else {
                            appString(R.string.capture_stability_tracker_auto_capture_is_off_use)
                        },
                        countdownValue = null,
                        sceneIssue = null,
                    ),
                )
            }
            completionEvent?.let { event -> _events.send(event) }
        }
    }

    fun onCaptureFailed(message: String) {
        viewModelScope.launch {
            applyFailureCooldown()
            pendingCaptureQuad = null
            pendingCaptureTrigger = CaptureTrigger.MANUAL
            _uiState.update { current ->
                current.copy(
                    captureInProgress = false,
                    liveDetection = current.liveDetection.copy(
                        phase = if (current.liveDetection.autoCaptureEnabled) AutoCapturePhase.SEARCHING else AutoCapturePhase.OFF,
                        statusMessage = appString(R.string.scan_session_capture_failed_reposition_the_document),
                        countdownValue = null,
                        sceneIssue = null,
                    ),
                )
            }
            _events.send(ScanSessionEvent.ShowMessage(message))
        }
    }

    private suspend fun startCapture(trigger: CaptureTrigger) {
        if (_uiState.value.captureInProgress || _uiState.value.document == null) {
            if (_uiState.value.document == null) {
                emitMessage(appString(R.string.scan_session_document_not_found))
            }
            return
        }

        pendingCaptureTrigger = trigger
        pendingCaptureQuad = _uiState.value.liveDetection.quad
        resetCornerSelector()
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
            val state = _uiState.value
            preparePageCaptureUseCase(
                documentId = documentId,
                scanMode = state.scanMode,
                idCardPairId = state.idCard.pairId ?: UUID.randomUUID().toString(),
                idCardSide = state.idCard.nextSide,
            )
        }

        when (val result = capturePreparationResult) {
            is ScanlyResult.Success -> _events.send(ScanSessionEvent.PerformCapture(result.value))
            is ScanlyResult.Failure -> {
                applyFailureCooldown()
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
                _events.send(ScanSessionEvent.ShowMessage(result.error.message))
            }
        }
    }

    /** After a saved ID side: the back follows the front; a saved back starts the next card. */
    private fun advanceIdCard(draft: PageCaptureDraft) {
        if (draft.scanMode != ScanMode.ID_CARD) return
        _uiState.update { current ->
            current.copy(
                idCard = when (draft.idCardSide) {
                    IdCardSide.FRONT -> IdCardCaptureState(pairId = draft.idCardPairId, nextSide = IdCardSide.BACK)
                    else -> IdCardCaptureState()
                },
            )
        }
    }

    /** Same back-off as a committed capture, minus the success path, for every failed attempt. */
    private fun applyFailureCooldown() {
        stabilityTracker.onCaptureCommitted(
            quad = pendingCaptureQuad,
            nowMillis = monotonicNowMillis(),
        )
    }

    private fun resetFrameTrackers() = synchronized(trackerLock) {
        trackerGeneration.incrementAndGet()
        gateStabilityTracker.reset()
        stableCornerSelector.reset()
    }

    /** Resets the selector and invalidates frames already in flight, so no stale quad reappears. */
    private fun resetCornerSelector() = synchronized(trackerLock) {
        trackerGeneration.incrementAndGet()
        stableCornerSelector.reset()
    }

    private fun isStaleFrame(generation: Int): Boolean =
        trackerGeneration.get() != generation || _uiState.value.captureInProgress

    /** Durations and cooldowns must not jump with wall-clock adjustments. */
    private fun monotonicNowMillis(): Long = SystemClock.elapsedRealtime()

    private fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.send(ScanSessionEvent.ShowMessage(message))
        }
    }

    private fun captureSuccessMessage(
        draft: PageCaptureDraft,
        trigger: CaptureTrigger,
    ): String = when {
        trigger == CaptureTrigger.AUTO -> appString(R.string.scan_session_auto_captured_page, draft.pageIndex + 1)
        else -> appString(R.string.scan_session_page_saved, draft.pageIndex + 1)
    }

}

/** Corner inset (fraction of the visible crop) a quad needs before it may arm auto-capture. */
private const val VISIBLE_REGION_MARGIN = 0.01f

/** Status while waiting for the next subject after a capture. */
private fun nextSubjectMessage(state: ScanSessionUiState): String = when (state.scanMode) {
    ScanMode.ID_CARD -> if (state.idCard.nextSide == IdCardSide.BACK) {
        appString(R.string.scan_mode_id_flip_the_card)
    } else {
        appString(R.string.scan_mode_id_card_saved_next)
    }
    ScanMode.BOOK -> appString(R.string.scan_mode_book_turn_the_page)
    ScanMode.DOCUMENT -> appString(R.string.capture_stability_tracker_move_to_the_next_page)
}

/** The tracker's status, with ID card cooldown and search messages naming the side to scan. */
private fun modeStatusMessage(state: ScanSessionUiState, evaluation: StabilityEvaluation): String = when {
    state.scanMode != ScanMode.ID_CARD -> evaluation.statusMessage
    evaluation.phase == AutoCapturePhase.COOLDOWN -> nextSubjectMessage(state)
    evaluation.phase == AutoCapturePhase.SEARCHING -> idSearchMessage(state.idCard.nextSide)
    else -> evaluation.statusMessage
}

private fun idSearchMessage(side: IdCardSide): String = when (side) {
    IdCardSide.FRONT -> appString(R.string.scan_mode_id_scan_the_front)
    IdCardSide.BACK -> appString(R.string.scan_mode_id_scan_the_back)
}

private fun searchingMessage(mode: ScanMode, autoCaptureEnabled: Boolean): String = when {
    !autoCaptureEnabled -> appString(R.string.capture_stability_tracker_auto_capture_is_off_use)
    mode == ScanMode.ID_CARD -> idSearchMessage(IdCardSide.FRONT)
    mode == ScanMode.BOOK -> appString(R.string.scan_mode_book_frame_one_page)
    else -> appString(R.string.scan_session_point_your_camera_at_a_2)
}

/**
 * Where an interrupted ID card left off: the newest card with a front but no back yet is resumed
 * at its back; otherwise the next capture starts a new card.
 */
internal fun resolveIdCaptureState(pages: List<ScanPage>): IdCardCaptureState {
    val idPages = pages.filter { it.scanMode == ScanMode.ID_CARD && it.idCardPairId != null }
    val newest = idPages.maxWithOrNull(compareBy<ScanPage> { it.createdAtMillis }.thenBy { it.pageIndex })
        ?: return IdCardCaptureState()
    val pairId = checkNotNull(newest.idCardPairId)
    val sides = idPages.filter { it.idCardPairId == pairId }.mapNotNull { it.idCardSide }.toSet()
    return if (IdCardSide.FRONT in sides && IdCardSide.BACK !in sides) {
        IdCardCaptureState(pairId = pairId, nextSide = IdCardSide.BACK)
    } else {
        IdCardCaptureState()
    }
}

private data class FrameAnalysis(
    val detection: CornerDetectionResult,
    val isFreshDetection: Boolean,
    val gate: DocumentGateResult?,
    val gateAccepted: Boolean,
    val sceneIssue: CaptureSceneIssue?,
    val cornerIssue: CornerResolutionIssue?,
)

private fun gateGuidance(analysis: FrameAnalysis): String? = when {
    analysis.sceneIssue != null -> null
    analysis.cornerIssue == CornerResolutionIssue.TWO_PAGES_AMBIGUOUS ->
        appString(R.string.scan_session_two_pages_are_visible_move)
    analysis.cornerIssue == CornerResolutionIssue.MODELS_DISAGREE ->
        appString(R.string.scan_session_page_edges_are_ambiguous_hold)
    analysis.gateAccepted -> null
    analysis.gate?.predictedClass == DocumentGateClass.DIGITAL_SCREEN ->
        appString(R.string.scan_session_screen_detected_point_at_a)
    analysis.gate?.predictedClass == DocumentGateClass.NEITHER ->
        appString(R.string.scan_session_point_your_camera_at_a)
    else -> appString(R.string.scan_session_checking_document)
}

internal fun replacementCompletionEvent(
    draft: PageCaptureDraft,
    capturedPageId: String,
): ScanSessionEvent.ReplacementCompleted? = if (draft.isReplacement) {
    ScanSessionEvent.ReplacementCompleted(capturedPageId)
} else {
    null
}
