package `in`.c1ph3rj.scanly.feature.tools.text

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ImportImagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ImageTextUiState(
    val isImporting: Boolean = false,
    val importCurrent: Int = 0,
    val importTotal: Int = 0,
    val importStageLabel: String = "",
)

sealed interface ImageTextEvent {
    /** Photos became [documentId]; [notice] is set when some were skipped or capped. */
    data class OpenText(val documentId: String, val notice: String?) : ImageTextEvent
    data class ShowMessage(val message: String) : ImageTextEvent
}

/**
 * Backs "Image to text". Picked photos are imported as a new document so the existing
 * Extracted text screen (and library search) can read them; library scans open directly.
 */
@HiltViewModel
class ImageTextViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val importImagesUseCase: ImportImagesUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImageTextUiState())
    val uiState: StateFlow<ImageTextUiState> = _uiState.asStateFlow()

    val libraryDocuments: StateFlow<List<ScanDocument>> = observeDocumentsUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    private val _events = MutableSharedFlow<ImageTextEvent>()
    val events: SharedFlow<ImageTextEvent> = _events.asSharedFlow()

    fun importPhotos(imageUris: List<Uri>) {
        if (imageUris.isEmpty() || _uiState.value.isImporting) return
        val cappedSelection = ImageImportSupport.capSelection(imageUris)

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isImporting = true,
                    importCurrent = 0,
                    importTotal = cappedSelection.items.size,
                    importStageLabel = "Starting import",
                )
            }
            try {
                val documentId = when (val createResult = createDocumentUseCase.createImported()) {
                    is ScanlyResult.Success -> createResult.value
                    is ScanlyResult.Failure -> {
                        _events.emit(ImageTextEvent.ShowMessage(createResult.error.message))
                        return@launch
                    }
                }
                val importResult = importImagesUseCase(
                    documentId = documentId,
                    imageUris = cappedSelection.items,
                    onProgress = { progress ->
                        _uiState.update {
                            it.copy(
                                importCurrent = progress.currentIndex,
                                importTotal = progress.totalCount,
                                importStageLabel = progress.stageLabel,
                            )
                        }
                    },
                )
                when (importResult) {
                    is ScanlyResult.Success -> {
                        val summary = importResult.value
                        // "Imported N images" alone is noise here; only mention what was left out.
                        val notice = if (cappedSelection.truncated || summary.skippedCount > 0) {
                            ImageImportSupport.importResultMessage(
                                importedCount = summary.importedCount,
                                truncated = cappedSelection.truncated,
                                skippedCount = summary.skippedCount,
                            )
                        } else {
                            null
                        }
                        _events.emit(ImageTextEvent.OpenText(documentId, notice))
                    }
                    is ScanlyResult.Failure ->
                        _events.emit(ImageTextEvent.ShowMessage(importResult.error.message))
                }
            } finally {
                _uiState.update {
                    it.copy(isImporting = false, importCurrent = 0, importTotal = 0, importStageLabel = "")
                }
            }
        }
    }
}
