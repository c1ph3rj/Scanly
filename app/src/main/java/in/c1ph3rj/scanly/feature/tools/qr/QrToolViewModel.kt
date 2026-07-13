package `in`.c1ph3rj.scanly.feature.tools.qr

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.usecase.SaveExportArtifactUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.GenerateQrBitmapUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.SaveQrPngUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class QrToolMode {
    Scan,
    Generate,
}

data class QrToolUiState(
    val mode: QrToolMode = QrToolMode.Scan,
    val scanResult: String? = null,
    val generateContent: String = "",
    val previewBitmap: Bitmap? = null,
    val savedArtifact: ExportArtifact? = null,
    val isWorking: Boolean = false,
)

sealed interface QrToolEvent {
    data class ShowMessage(val message: String) : QrToolEvent
}

@HiltViewModel
class QrToolViewModel @Inject constructor(
    private val generateQrBitmapUseCase: GenerateQrBitmapUseCase,
    private val saveQrPngUseCase: SaveQrPngUseCase,
    private val saveExportArtifactUseCase: SaveExportArtifactUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrToolUiState())
    val uiState: StateFlow<QrToolUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<QrToolEvent>()
    val events: SharedFlow<QrToolEvent> = _events.asSharedFlow()

    private var previewJob: Job? = null

    fun setMode(mode: QrToolMode) {
        _uiState.update { it.copy(mode = mode) }
    }

    fun onScanResult(value: String) {
        _uiState.update { it.copy(scanResult = value) }
    }

    fun clearScanResult() {
        _uiState.update { it.copy(scanResult = null) }
    }

    fun setGenerateContent(value: String) {
        _uiState.update { it.copy(generateContent = value, savedArtifact = null) }
        previewJob?.cancel()
        previewJob = viewModelScope.launch {
            delay(250)
            if (value.isBlank()) {
                val previous = _uiState.value.previewBitmap
                _uiState.update { it.copy(previewBitmap = null) }
                previous?.recycle()
                return@launch
            }
            when (val result = generateQrBitmapUseCase(value)) {
                is ScanlyResult.Success -> {
                    val previous = _uiState.value.previewBitmap
                    _uiState.update { it.copy(previewBitmap = result.value) }
                    previous?.recycle()
                }
                is ScanlyResult.Failure -> {
                    // Keep last good preview; soft-fail while typing invalid edge cases.
                }
            }
        }
    }

    fun saveGenerated() {
        val content = _uiState.value.generateContent
        if (content.isBlank()) {
            emitMessage("Enter text or a URL first.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            when (val saved = saveQrPngUseCase(content)) {
                is ScanlyResult.Success -> {
                    _uiState.update { it.copy(savedArtifact = saved.value) }
                    when (val exported = saveExportArtifactUseCase(saved.value)) {
                        is ScanlyResult.Success ->
                            emitMessage(
                                "Saved ${exported.value.fileName} to ${exported.value.destinationLabel}",
                            )
                        is ScanlyResult.Failure -> emitMessage(exported.error.message)
                    }
                    _uiState.update { it.copy(isWorking = false) }
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isWorking = false) }
                    emitMessage(saved.error.message)
                }
            }
        }
    }

    fun prepareShare(onReady: (ExportArtifact) -> Unit) {
        val existing = _uiState.value.savedArtifact
        if (existing != null) {
            onReady(existing)
            return
        }
        val content = _uiState.value.generateContent
        if (content.isBlank()) {
            emitMessage("Enter text or a URL first.")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            when (val saved = saveQrPngUseCase(content)) {
                is ScanlyResult.Success -> {
                    _uiState.update { it.copy(isWorking = false, savedArtifact = saved.value) }
                    onReady(saved.value)
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isWorking = false) }
                    emitMessage(saved.error.message)
                }
            }
        }
    }

    fun emitCopied() {
        emitMessage("Copied to clipboard")
    }

    fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(QrToolEvent.ShowMessage(message))
        }
    }

    override fun onCleared() {
        _uiState.value.previewBitmap?.recycle()
        super.onCleared()
    }
}
