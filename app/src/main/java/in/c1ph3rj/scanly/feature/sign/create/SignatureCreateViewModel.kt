package `in`.c1ph3rj.scanly.feature.sign.create

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.editing.InkStroke
import `in`.c1ph3rj.scanly.domain.model.SignatureKind
import `in`.c1ph3rj.scanly.domain.model.SignatureSource
import `in`.c1ph3rj.scanly.domain.usecase.ExtractSignatureInkUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveSignatureUseCase
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How the new mark is produced: drawn on the pad or extracted from a photo. */
enum class SignatureCreateMode { Draw, Upload }

data class SignatureCreateUiState(
    val mode: SignatureCreateMode = SignatureCreateMode.Draw,
    val kind: SignatureKind = SignatureKind.SIGNATURE,
    val strokes: List<InkStroke> = emptyList(),
    val uploadUri: String? = null,
    /** Ink mask extracted from [uploadUri] at the current sensitivity. */
    val uploadPreview: Bitmap? = null,
    val sensitivity: Float = SignatureCreateViewModel.DefaultSensitivity,
    val isExtracting: Boolean = false,
    val isSaving: Boolean = false,
    val uploadError: String? = null,
)

/** One-shot events for the create screen. */
sealed interface SignatureCreateEvent {
    /** The mark was saved; the screen should close. */
    data object Saved : SignatureCreateEvent

    data class Message(val text: String) : SignatureCreateEvent
}

/**
 * Holds the signature being drawn or uploaded and saves it as a reusable mark.
 *
 * The initial [SignatureKind] comes from the navigation argument `kind` (its storage value).
 */
@HiltViewModel
class SignatureCreateViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val saveSignatureUseCase: SaveSignatureUseCase,
    private val extractSignatureInkUseCase: ExtractSignatureInkUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        SignatureCreateUiState(kind = SignatureKind.fromStorage(savedStateHandle.get<String>(KindKey))),
    )
    val uiState: StateFlow<SignatureCreateUiState> = _uiState.asStateFlow()

    private val eventChannel = Channel<SignatureCreateEvent>(Channel.BUFFERED)
    val events: Flow<SignatureCreateEvent> = eventChannel.receiveAsFlow()

    private var extractJob: Job? = null

    /** Incremented for every extraction or save; results from older requests are dropped. */
    private var extractRequest = 0L

    fun setMode(mode: SignatureCreateMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun setKind(kind: SignatureKind) {
        _uiState.update { it.copy(kind = kind) }
    }

    fun addStroke(stroke: InkStroke) {
        _uiState.update { it.copy(strokes = it.strokes + stroke) }
    }

    fun undo() {
        _uiState.update { it.copy(strokes = it.strokes.dropLast(1)) }
    }

    fun clear() {
        _uiState.update { it.copy(strokes = emptyList()) }
    }

    /** Uses the picked photo as the upload source and extracts its ink straight away. */
    fun onImagePicked(uriString: String) {
        if (_uiState.value.isSaving) return
        val previous = _uiState.value.uploadPreview
        _uiState.update {
            it.copy(uploadUri = uriString, uploadPreview = null, uploadError = null, isExtracting = false)
        }
        previous?.recycle()
        scheduleExtraction(debounceMs = 0L)
    }

    /** Updates the ink sensitivity and re-extracts after a short pause, so dragging stays smooth. */
    fun setSensitivity(value: Float) {
        if (_uiState.value.isSaving) return
        _uiState.update { it.copy(sensitivity = value.coerceIn(MinSensitivity, MaxSensitivity)) }
        scheduleExtraction(debounceMs = SensitivityDebounceMs)
    }

    /**
     * Saves the mark. In Draw mode [drawnMask] is the ink mask rendered from the pad (it is
     * recycled after saving); in Upload mode the extracted preview is saved and [drawnMask] is ignored.
     */
    fun save(drawnMask: Bitmap?) {
        val state = _uiState.value
        if (state.isSaving) return
        val isDraw = state.mode == SignatureCreateMode.Draw
        val mask = if (isDraw) drawnMask else state.uploadPreview
        if (mask == null || mask.isRecycled) {
            emit(SignatureCreateEvent.Message(missingMarkMessage(state)))
            return
        }
        val source = if (isDraw) SignatureSource.DRAWN else SignatureSource.IMPORTED

        extractRequest++
        extractJob?.cancel()
        _uiState.update { it.copy(isSaving = true, isExtracting = false) }
        viewModelScope.launch {
            val result = saveSignatureUseCase(mask, state.kind, source)
            if (isDraw) mask.recycle()
            _uiState.update { it.copy(isSaving = false) }
            when (result) {
                is ScanlyResult.Success -> eventChannel.send(SignatureCreateEvent.Saved)
                is ScanlyResult.Failure -> eventChannel.send(SignatureCreateEvent.Message(result.error.message))
            }
        }
    }

    private fun scheduleExtraction(debounceMs: Long) {
        val uri = _uiState.value.uploadUri ?: return
        val request = ++extractRequest
        extractJob?.cancel()
        extractJob = viewModelScope.launch {
            if (debounceMs > 0L) delay(debounceMs)
            val sensitivity = _uiState.value.sensitivity
            _uiState.update { it.copy(isExtracting = true, uploadError = null) }
            val result = extractSignatureInkUseCase(uri, sensitivity)
            if (request != extractRequest) {
                if (result is ScanlyResult.Success) result.value.recycle()
                return@launch
            }
            when (result) {
                is ScanlyResult.Success -> {
                    val previous = _uiState.value.uploadPreview
                    _uiState.update { it.copy(uploadPreview = result.value, isExtracting = false) }
                    if (previous !== result.value) previous?.recycle()
                }
                is ScanlyResult.Failure -> {
                    val previous = _uiState.value.uploadPreview
                    _uiState.update {
                        it.copy(uploadPreview = null, isExtracting = false, uploadError = result.error.message)
                    }
                    previous?.recycle()
                }
            }
        }
    }

    private fun missingMarkMessage(state: SignatureCreateUiState): String = when {
        state.mode == SignatureCreateMode.Draw -> "Draw your signature first."
        state.isExtracting -> "Still reading the photo. Try again in a moment."
        else -> "Choose a photo of your signature first."
    }

    private fun emit(event: SignatureCreateEvent) {
        viewModelScope.launch { eventChannel.send(event) }
    }

    override fun onCleared() {
        extractJob?.cancel()
        _uiState.value.uploadPreview?.recycle()
        super.onCleared()
    }

    companion object {
        /** Navigation argument carrying the [SignatureKind] storage value. */
        const val KindKey = "kind"

        const val DefaultSensitivity = 0.5f
        const val MinSensitivity = 0.15f
        const val MaxSensitivity = 0.85f

        private const val SensitivityDebounceMs = 200L
    }
}
