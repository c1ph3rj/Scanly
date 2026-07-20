package `in`.c1ph3rj.scanly.feature.document

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.usecase.DocumentWorkspace
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
    val isImporting: Boolean = false,
    val importCurrent: Int = 0,
    val importTotal: Int = 0,
    val importStageLabel: String = "",
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
    data class ShareFiles(val artifact: ShareArtifact) : DocumentDetailEvent
    data object DocumentDeleted : DocumentDetailEvent
}

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val workspace: DocumentWorkspace,
) : ViewModel() {
    private val documentId: String =
        checkNotNull(savedStateHandle[DocumentDestination.documentIdArgument])

    private val _uiState = MutableStateFlow(DocumentDetailUiState())
    val uiState: StateFlow<DocumentDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DocumentDetailEvent>()
    val events: SharedFlow<DocumentDetailEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            workspace.observeDocument(documentId).collectLatest { document ->
                _uiState.update { current ->
                    current.copy(
                        document = document,
                        missingDocument = document == null,
                    )
                }
            }
        }

        viewModelScope.launch {
            workspace.observePages(documentId).collectLatest { pages ->
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
            workspace.observeGroups().collectLatest { groups ->
                _uiState.update { current -> current.copy(availableGroups = groups) }
            }
        }
    }

    fun moveToGroup(groupId: String?) {
        viewModelScope.launch {
            when (val result = workspace.setDocumentGroup(documentId, groupId)) {
                is ScanlyResult.Success -> _events.emit(
                    DocumentDetailEvent.ShowMessage(moveConfirmationMessage(groupId)),
                )

                is ScanlyResult.Failure -> _events.emit(
                    DocumentDetailEvent.ShowMessage(result.error.message),
                )
            }
        }
    }

    suspend fun suggestGroupTitle(format: GroupTitleFormat): String =
        workspace.suggestGroupTitle(format)

    fun createFolderAndMove(name: String) {
        viewModelScope.launch {
            when (val createResult = workspace.createGroup(name)) {
                is ScanlyResult.Success -> {
                    when (val moveResult = workspace.setDocumentGroup(documentId, createResult.value)) {
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
            workspace.movePage(
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
            workspace.deletePage(selectedPage.id)
        }
    }

    fun exportPdf(options: PdfExportOptions) {
        runExportAction(
            progressMessage = "Generating and saving PDF",
            action = { workspace.exportPdfAndSave(documentId, options) },
            onSuccess = { saved ->
                DocumentDetailEvent.ShowMessage(
                    "Saved ${saved.fileName} to ${saved.destinationLabel}",
                )
            },
        )
    }

    fun sharePdf(options: PdfExportOptions) {
        runExportAction(
            progressMessage = "Preparing PDF",
            action = { workspace.preparePdfShare(documentId, options) },
            onSuccess = DocumentDetailEvent::ShareFiles,
        )
    }

    fun exportImageArchive() {
        runExportAction(
            progressMessage = "Preparing and saving ZIP",
            action = { workspace.exportImageArchiveAndSave(documentId) },
            onSuccess = { saved ->
                DocumentDetailEvent.ShowMessage(
                    "Saved ${saved.fileName} to ${saved.destinationLabel}",
                )
            },
        )
    }

    fun shareImages() {
        runExportAction(
            progressMessage = "Preparing pages",
            action = { workspace.prepareImageShare(documentId) },
            onSuccess = DocumentDetailEvent::ShareFiles,
        )
    }

    fun shareSelectedPage() {
        val snapshot = _uiState.value
        val selectedPage = snapshot.selectedPage ?: return
        val imagePath = selectedPage.processedImagePath
            ?: selectedPage.rawImagePath
            ?: selectedPage.thumbnailPath

        viewModelScope.launch {
            if (imagePath == null) {
                _events.emit(DocumentDetailEvent.ShowMessage("Page image is not available yet."))
                return@launch
            }

            _events.emit(
                DocumentDetailEvent.ShareFiles(
                    ShareArtifact(
                        mimeType = PageImageMimeType,
                        title = "${snapshot.document?.title ?: "Scanly"} page ${selectedPage.pageIndex + 1}",
                        filePaths = listOf(imagePath),
                    ),
                ),
            )
        }
    }

    fun importImages(uris: List<Uri>) {
        if (_uiState.value.isMutatingPage || _uiState.value.isImporting || uris.isEmpty()) return

        val cappedSelection = ImageImportSupport.capSelection(uris)

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(
                    isMutatingPage = true,
                    isImporting = true,
                    importCurrent = 0,
                    importTotal = cappedSelection.items.size,
                    importStageLabel = "Starting import",
                )
            }
            try {
                when (
                    val result = workspace.importImages(
                        documentId = documentId,
                        imageUriStrings = cappedSelection.items.map { it.toString() },
                        onProgress = { progress ->
                            _uiState.update { current ->
                                current.copy(
                                    importCurrent = progress.currentIndex,
                                    importTotal = progress.totalCount,
                                    importStageLabel = progress.stageLabel,
                                )
                            }
                        },
                    )
                ) {
                    is ScanlyResult.Success -> {
                        _events.emit(
                            DocumentDetailEvent.ShowMessage(
                                ImageImportSupport.importResultMessage(
                                    importedCount = cappedSelection.items.size,
                                    truncated = cappedSelection.truncated,
                                ),
                            ),
                        )
                    }
                    is ScanlyResult.Failure -> {
                        _events.emit(DocumentDetailEvent.ShowMessage(result.error.message))
                    }
                }
            } finally {
                _uiState.update { current ->
                    current.copy(
                        isMutatingPage = false,
                        isImporting = false,
                        importCurrent = 0,
                        importTotal = 0,
                        importStageLabel = "",
                    )
                }
            }
        }
    }

    fun renameDocument(title: String) {
        viewModelScope.launch {
            when (val result = workspace.renameDocument(documentId, title)) {
                is ScanlyResult.Success -> {
                    _events.emit(DocumentDetailEvent.ShowMessage("Document renamed."))
                }

                is ScanlyResult.Failure -> {
                    _events.emit(DocumentDetailEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun deleteDocument() {
        viewModelScope.launch {
            when (val result = workspace.deleteDocument(documentId)) {
                is ScanlyResult.Success -> _events.emit(DocumentDetailEvent.DocumentDeleted)
                is ScanlyResult.Failure -> {
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

private const val PageImageMimeType = "image/jpeg"
