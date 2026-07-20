package `in`.c1ph3rj.scanly.feature.tools

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ImportImagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SuggestDocumentTitleUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ToolsUiState(
    val isImporting: Boolean = false,
    val importCurrent: Int = 0,
    val importTotal: Int = 0,
    val importStageLabel: String = "",
)

sealed interface ToolsEvent {
    data class OpenDocument(val documentId: String) : ToolsEvent
    data class OpenScanSession(val documentId: String) : ToolsEvent
    data class ShowMessage(val message: String) : ToolsEvent
}

@HiltViewModel
class ToolsViewModel @Inject constructor(
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val importImagesUseCase: ImportImagesUseCase,
    private val suggestDocumentTitleUseCase: SuggestDocumentTitleUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ToolsUiState())
    val uiState: StateFlow<ToolsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ToolsEvent>()
    val events: SharedFlow<ToolsEvent> = _events.asSharedFlow()

    suspend fun suggestDocumentTitle(format: DocumentTitleFormat): String =
        suggestDocumentTitleUseCase(format)

    fun createDocumentForScan(title: String) {
        viewModelScope.launch {
            when (val result = createDocumentUseCase(title)) {
                is ScanlyResult.Success ->
                    _events.emit(ToolsEvent.OpenScanSession(result.value))
                is ScanlyResult.Failure ->
                    _events.emit(ToolsEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun importImagesAsDocument(imageUris: List<Uri>) {
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
                when (val createResult = createDocumentUseCase.createImported()) {
                    is ScanlyResult.Success -> {
                        when (
                            val importResult = importImagesUseCase(
                                documentId = createResult.value,
                                imageUriStrings = cappedSelection.items.map { it.toString() },
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
                        ) {
                            is ScanlyResult.Success -> {
                                _events.emit(ToolsEvent.OpenDocument(createResult.value))
                                _events.emit(
                                    ToolsEvent.ShowMessage(
                                        ImageImportSupport.importResultMessage(
                                            importedCount = cappedSelection.items.size,
                                            truncated = cappedSelection.truncated,
                                        ),
                                    ),
                                )
                            }
                            is ScanlyResult.Failure ->
                                _events.emit(ToolsEvent.ShowMessage(importResult.error.message))
                        }
                    }
                    is ScanlyResult.Failure ->
                        _events.emit(ToolsEvent.ShowMessage(createResult.error.message))
                }
            } finally {
                _uiState.update {
                    it.copy(
                        isImporting = false,
                        importCurrent = 0,
                        importTotal = 0,
                        importStageLabel = "",
                    )
                }
            }
        }
    }
}
