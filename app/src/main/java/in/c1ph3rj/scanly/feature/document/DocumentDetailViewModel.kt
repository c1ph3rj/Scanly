package `in`.c1ph3rj.scanly.feature.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.usecase.DeletePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.AssignDocumentToGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ExportDocumentImageArchiveUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ExportDocumentPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ImportImagesToDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.MovePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PrepareDocumentPdfShareUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PrepareDocumentImageShareUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class DocumentDetailUiState(
    val document: ScanDocument? = null,
    val groups: List<DocumentGroup> = emptyList(),
    val pages: List<ScanPage> = emptyList(),
    val selectedPageId: String? = null,
    val missingDocument: Boolean = false,
    val isMutatingPage: Boolean = false,
    val isImporting: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
) {
    val selectedPage: ScanPage?
        get() = pages.firstOrNull { page -> page.id == selectedPageId } ?: pages.firstOrNull()
}

sealed interface DocumentDetailEvent {
    data class ShowMessage(val message: String) : DocumentDetailEvent
    data class SaveExportedFile(val artifact: ExportArtifact) : DocumentDetailEvent
    data class ShareFiles(val artifact: ShareArtifact) : DocumentDetailEvent
}

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeDocumentUseCase: ObserveDocumentUseCase,
    private val observeDocumentGroupsUseCase: ObserveDocumentGroupsUseCase,
    private val observeDocumentPagesUseCase: ObserveDocumentPagesUseCase,
    private val assignDocumentToGroupUseCase: AssignDocumentToGroupUseCase,
    private val createDocumentGroupUseCase: CreateDocumentGroupUseCase,
    private val importImagesToDocumentUseCase: ImportImagesToDocumentUseCase,
    private val movePageUseCase: MovePageUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val exportDocumentPdfUseCase: ExportDocumentPdfUseCase,
    private val exportDocumentImageArchiveUseCase: ExportDocumentImageArchiveUseCase,
    private val prepareDocumentPdfShareUseCase: PrepareDocumentPdfShareUseCase,
    private val prepareDocumentImageShareUseCase: PrepareDocumentImageShareUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[DocumentDestination.documentIdArgument])

    private val _uiState = MutableStateFlow(DocumentDetailUiState())
    val uiState: StateFlow<DocumentDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DocumentDetailEvent>()
    val events: SharedFlow<DocumentDetailEvent> = _events.asSharedFlow()

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
            observeDocumentGroupsUseCase().collectLatest { groups ->
                _uiState.update { current ->
                    current.copy(groups = groups)
                }
            }
        }

        viewModelScope.launch {
            observeDocumentPagesUseCase(documentId).collectLatest { pages ->
                _uiState.update { current ->
                    current.copy(
                        pages = pages,
                        selectedPageId = resolveSelectedPageId(
                            currentSelectedPageId = current.selectedPageId,
                            pages = pages,
                        ),
                    )
                }
            }
        }
    }

    fun selectPage(pageId: String) {
        _uiState.update { current ->
            current.copy(selectedPageId = pageId)
        }
    }

    fun moveSelectedPageLeft() {
        val selectedPage = _uiState.value.selectedPage ?: return
        if (selectedPage.pageIndex <= 0) return
        mutateSelectedPage(
            successMessage = "Moved page earlier in the document.",
        ) {
            movePageUseCase(
                pageId = selectedPage.id,
                targetIndex = selectedPage.pageIndex - 1,
            )
        }
    }

    fun moveSelectedPageRight() {
        val snapshot = _uiState.value
        val selectedPage = snapshot.selectedPage ?: return
        if (selectedPage.pageIndex >= snapshot.pages.lastIndex) return
        mutateSelectedPage(
            successMessage = "Moved page later in the document.",
        ) {
            movePageUseCase(
                pageId = selectedPage.id,
                targetIndex = selectedPage.pageIndex + 1,
            )
        }
    }

    fun deleteSelectedPage() {
        val selectedPage = _uiState.value.selectedPage ?: return
        mutateSelectedPage(
            successMessage = "Deleted page ${selectedPage.pageIndex + 1}.",
        ) {
            deletePageUseCase(selectedPage.id)
        }
    }

    fun exportPdf(options: PdfExportOptions) {
        runExportAction(
            progressMessage = "Generating PDF",
            action = { exportDocumentPdfUseCase(documentId, options) },
            onSuccess = DocumentDetailEvent::SaveExportedFile,
        )
    }

    fun sharePdf(options: PdfExportOptions) {
        runExportAction(
            progressMessage = "Preparing PDF",
            action = { prepareDocumentPdfShareUseCase(documentId, options) },
            onSuccess = DocumentDetailEvent::ShareFiles,
        )
    }

    fun exportImageArchive() {
        runExportAction(
            progressMessage = "Preparing ZIP",
            action = { exportDocumentImageArchiveUseCase(documentId) },
            onSuccess = DocumentDetailEvent::SaveExportedFile,
        )
    }

    fun shareImages() {
        runExportAction(
            progressMessage = "Preparing pages",
            action = { prepareDocumentImageShareUseCase(documentId) },
            onSuccess = DocumentDetailEvent::ShareFiles,
        )
    }

    fun importImages(imageUris: List<Uri>) {
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
                    _events.emit(DocumentDetailEvent.ShowMessage(message))
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isImporting = false) }
                    _events.emit(DocumentDetailEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun changeDocumentGroup(groupId: String?) {
        viewModelScope.launch {
            when (val result = assignDocumentToGroupUseCase(documentId, groupId)) {
                is ScanlyResult.Success -> _events.emit(DocumentDetailEvent.ShowMessage("Document group updated."))
                is ScanlyResult.Failure -> _events.emit(DocumentDetailEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun createGroupForDocument(name: String) {
        viewModelScope.launch {
            when (val createResult = createDocumentGroupUseCase(name)) {
                is ScanlyResult.Success -> {
                    when (val assignResult = assignDocumentToGroupUseCase(documentId, createResult.value)) {
                        is ScanlyResult.Success -> _events.emit(DocumentDetailEvent.ShowMessage("Group created and document moved."))
                        is ScanlyResult.Failure -> _events.emit(DocumentDetailEvent.ShowMessage(assignResult.error.message))
                    }
                }

                is ScanlyResult.Failure -> _events.emit(DocumentDetailEvent.ShowMessage(createResult.error.message))
            }
        }
    }

    private fun mutateSelectedPage(
        successMessage: String? = null,
        action: suspend () -> ScanlyResult<Unit>,
    ) {
        if (_uiState.value.isMutatingPage) {
            return
        }

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isMutatingPage = true) }
            when (val result = action()) {
                is ScanlyResult.Success -> {
                    _uiState.update { current ->
                        current.copy(isMutatingPage = false)
                    }
                    if (successMessage != null) {
                        _events.emit(DocumentDetailEvent.ShowMessage(successMessage))
                    }
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isMutatingPage = false) }
                    _events.emit(DocumentDetailEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    private fun <T> runExportAction(
        progressMessage: String,
        action: suspend () -> ScanlyResult<T>,
        onSuccess: (T) -> DocumentDetailEvent,
    ) {
        if (_uiState.value.isExporting) {
            return
        }

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isExporting = true,
                    exportMessage = progressMessage,
                )
            }
            when (val result = action()) {
                is ScanlyResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            isExporting = false,
                            exportMessage = null,
                        )
                    }
                    _events.emit(onSuccess(result.value))
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { current ->
                        current.copy(
                            isExporting = false,
                            exportMessage = null,
                        )
                    }
                    _events.emit(DocumentDetailEvent.ShowMessage(result.error.message))
                }
            }
        }
    }
}

internal fun resolveSelectedPageId(
    currentSelectedPageId: String?,
    pages: List<ScanPage>,
): String? {
    if (pages.isEmpty()) {
        return null
    }
    return pages.firstOrNull { page -> page.id == currentSelectedPageId }?.id
        ?: pages.first().id
}
