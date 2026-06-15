package `in`.c1ph3rj.scanly.feature.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.usecase.CreateGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeletePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ExportDocumentImageArchiveUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ExportDocumentPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.MovePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PrepareDocumentPdfShareUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PrepareDocumentImageShareUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ImportImagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetDocumentGroupUseCase
import android.net.Uri
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
    val pages: List<ScanPage> = emptyList(),
    val selectedPageId: String? = null,
    val missingDocument: Boolean = false,
    val isMutatingPage: Boolean = false,
    val isExporting: Boolean = false,
    val exportMessage: String? = null,
    val isLoading: Boolean = true,
    val availableGroups: List<DocumentGroup> = emptyList(),
) {
    val selectedPage: ScanPage?
        get() = pages.firstOrNull { page -> page.id == selectedPageId } ?: pages.firstOrNull()

    val currentGroup: DocumentGroup?
        get() = document?.groupId?.let { groupId -> availableGroups.firstOrNull { it.id == groupId } }
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
    private val observeDocumentPagesUseCase: ObserveDocumentPagesUseCase,
    private val movePageUseCase: MovePageUseCase,
    private val deletePageUseCase: DeletePageUseCase,
    private val exportDocumentPdfUseCase: ExportDocumentPdfUseCase,
    private val exportDocumentImageArchiveUseCase: ExportDocumentImageArchiveUseCase,
    private val prepareDocumentPdfShareUseCase: PrepareDocumentPdfShareUseCase,
    private val prepareDocumentImageShareUseCase: PrepareDocumentImageShareUseCase,
    private val importImagesUseCase: ImportImagesUseCase,
    private val observeGroupsUseCase: ObserveGroupsUseCase,
    private val setDocumentGroupUseCase: SetDocumentGroupUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
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
            observeDocumentPagesUseCase(documentId).collectLatest { pages ->
                _uiState.update { current ->
                    current.copy(
                        pages = pages,
                        isLoading = false,
                        selectedPageId = resolveSelectedPageId(
                            currentSelectedPageId = current.selectedPageId,
                            pages = pages,
                        ),
                    )
                }
            }
        }

        viewModelScope.launch {
            observeGroupsUseCase().collectLatest { groups ->
                _uiState.update { current -> current.copy(availableGroups = groups) }
            }
        }
    }

    fun moveToGroup(groupId: String?) {
        viewModelScope.launch {
            when (val result = setDocumentGroupUseCase(documentId, groupId)) {
                is ScanlyResult.Success -> _events.emit(
                    DocumentDetailEvent.ShowMessage(moveConfirmationMessage(groupId)),
                )

                is ScanlyResult.Failure -> _events.emit(
                    DocumentDetailEvent.ShowMessage(result.error.message),
                )
            }
        }
    }

    fun createFolderAndMove(name: String) {
        viewModelScope.launch {
            when (val createResult = createGroupUseCase(name)) {
                is ScanlyResult.Success -> {
                    when (val moveResult = setDocumentGroupUseCase(documentId, createResult.value)) {
                        is ScanlyResult.Success -> _events.emit(
                            DocumentDetailEvent.ShowMessage("Moved to $name"),
                        )

                        is ScanlyResult.Failure -> _events.emit(
                            DocumentDetailEvent.ShowMessage(moveResult.error.message),
                        )
                    }
                }

                is ScanlyResult.Failure -> _events.emit(
                    DocumentDetailEvent.ShowMessage(createResult.error.message),
                )
            }
        }
    }

    private fun moveConfirmationMessage(groupId: String?): String {
        if (groupId == null) {
            return "Removed from folder"
        }
        val folderName = _uiState.value.availableGroups.firstOrNull { it.id == groupId }?.title
        return if (folderName != null) "Moved to $folderName" else "Moved to folder"
    }

    fun selectPage(pageId: String) {
        _uiState.update { current ->
            current.copy(selectedPageId = pageId)
        }
    }

    fun movePage(pageId: String, targetIndex: Int) {
        val snapshot = _uiState.value
        val page = snapshot.pages.firstOrNull { candidate -> candidate.id == pageId } ?: return
        val clampedTargetIndex = targetIndex.coerceIn(0, snapshot.pages.lastIndex)
        if (page.pageIndex == clampedTargetIndex) return
        _uiState.update { current -> current.copy(selectedPageId = pageId) }
        mutateSelectedPage(
            successMessage = "Reordered pages.",
        ) {
            movePageUseCase(
                pageId = pageId,
                targetIndex = clampedTargetIndex,
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

    fun importImages(uris: List<Uri>) {
        if (_uiState.value.isMutatingPage) return
        
        viewModelScope.launch {
            _uiState.update { current -> current.copy(isMutatingPage = true) }
            when (val result = importImagesUseCase(documentId, uris)) {
                is ScanlyResult.Success -> {
                    _uiState.update { current -> current.copy(isMutatingPage = false) }
                    _events.emit(DocumentDetailEvent.ShowMessage("Imported ${uris.size} image(s)."))
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isMutatingPage = false) }
                    _events.emit(DocumentDetailEvent.ShowMessage(result.error.message))
                }
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
