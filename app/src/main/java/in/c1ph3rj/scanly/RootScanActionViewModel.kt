package `in`.c1ph3rj.scanly

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ImportImagesToDocumentUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RootScanUiState(
    val isImporting: Boolean = false,
)

sealed interface RootScanEvent {
    data class OpenScanSession(val documentId: String) : RootScanEvent
    data class OpenDocument(val documentId: String) : RootScanEvent
    data class LaunchImportPicker(val documentId: String) : RootScanEvent
    data class ShowMessage(val message: String) : RootScanEvent
}

@HiltViewModel
class RootScanActionViewModel @Inject constructor(
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val importImagesToDocumentUseCase: ImportImagesToDocumentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RootScanUiState())
    val uiState: StateFlow<RootScanUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<RootScanEvent>()
    val events: SharedFlow<RootScanEvent> = _events.asSharedFlow()

    fun startCamera(titleForNewDocument: String?) {
        viewModelScope.launch {
            val documentId = createNewDocument(titleForNewDocument) ?: return@launch
            _events.emit(RootScanEvent.OpenScanSession(documentId))
        }
    }

    fun startImport(titleForNewDocument: String?) {
        viewModelScope.launch {
            val documentId = createNewDocument(titleForNewDocument) ?: return@launch
            _events.emit(RootScanEvent.LaunchImportPicker(documentId))
        }
    }

    fun importImages(
        documentId: String,
        imageUris: List<Uri>,
    ) {
        if (imageUris.isEmpty() || _uiState.value.isImporting) {
            return
        }

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isImporting = true) }
            when (val result = importImagesToDocumentUseCase(documentId, imageUris)) {
                is ScanlyResult.Success -> {
                    _uiState.update { current -> current.copy(isImporting = false) }
                    val importResult = result.value
                    val message = if (importResult.hasFailures) {
                        "Imported ${importResult.importedCount} of ${importResult.requestedCount} images."
                    } else {
                        "Imported ${importResult.importedCount} images."
                    }
                    _events.emit(RootScanEvent.ShowMessage(message))
                    if (importResult.importedCount > 0) {
                        _events.emit(RootScanEvent.OpenDocument(documentId))
                    }
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isImporting = false) }
                    _events.emit(RootScanEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    private suspend fun createNewDocument(titleForNewDocument: String?): String? {
        val title = titleForNewDocument?.takeIf(String::isNotBlank) ?: "New scan"
        return when (val result = createDocumentUseCase(title)) {
            is ScanlyResult.Success -> result.value
            is ScanlyResult.Failure -> {
                _events.emit(RootScanEvent.ShowMessage(result.error.message))
                null
            }
        }
    }
}
