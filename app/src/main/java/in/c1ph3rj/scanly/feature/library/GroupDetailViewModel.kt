package `in`.c1ph3rj.scanly.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeleteGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ExportGroupPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ExportGroupZippedPdfsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveGroupDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveUngroupedDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PrepareGroupPdfShareUseCase
import `in`.c1ph3rj.scanly.domain.usecase.PrepareGroupZippedPdfsShareUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RenameGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetDocumentGroupUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupDetailUiState(
    val group: DocumentGroup? = null,
    val documents: List<ScanDocument> = emptyList(),
    val ungroupedDocuments: List<ScanDocument> = emptyList(),
    val exportProgress: ExportProgress? = null,
    val isLoading: Boolean = true,
)

data class ExportProgress(
    val current: Int,
    val total: Int,
    val label: String,
)

sealed interface GroupDetailEvent {
    data class ExportReady(val filePath: String, val fileName: String, val mimeType: String) :
        GroupDetailEvent
    data class ShareFiles(val artifact: ShareArtifact) : GroupDetailEvent
    data class ShowMessage(val message: String) : GroupDetailEvent
    data class OpenDocument(val documentId: String) : GroupDetailEvent
    data object GroupDeleted : GroupDetailEvent
}

@HiltViewModel
class GroupDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeGroupUseCase: ObserveGroupUseCase,
    observeGroupDocumentsUseCase: ObserveGroupDocumentsUseCase,
    observeUngroupedDocumentsUseCase: ObserveUngroupedDocumentsUseCase,
    private val renameGroupUseCase: RenameGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val setDocumentGroupUseCase: SetDocumentGroupUseCase,
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val exportGroupPdfUseCase: ExportGroupPdfUseCase,
    private val exportGroupZippedPdfsUseCase: ExportGroupZippedPdfsUseCase,
    private val prepareGroupPdfShareUseCase: PrepareGroupPdfShareUseCase,
    private val prepareGroupZippedPdfsShareUseCase: PrepareGroupZippedPdfsShareUseCase,
) : ViewModel() {

    val groupId: String = checkNotNull(savedStateHandle["groupId"])

    private val _exportProgress = MutableStateFlow<ExportProgress?>(null)
    private val _events = MutableSharedFlow<GroupDetailEvent>()
    val events: SharedFlow<GroupDetailEvent> = _events.asSharedFlow()

    private var exportJob: Job? = null

    val uiState: StateFlow<GroupDetailUiState> = combine(
        observeGroupUseCase(groupId),
        observeGroupDocumentsUseCase(groupId),
        observeUngroupedDocumentsUseCase(),
        _exportProgress,
    ) { group, documents, ungrouped, progress ->
        GroupDetailUiState(
            group = group,
            documents = documents,
            ungroupedDocuments = ungrouped,
            exportProgress = progress,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = GroupDetailUiState(),
    )

    fun renameGroup(title: String) {
        viewModelScope.launch {
            when (val result = renameGroupUseCase(groupId, title)) {
                is ScanlyResult.Success -> _events.emit(GroupDetailEvent.ShowMessage("Group renamed."))
                is ScanlyResult.Failure -> _events.emit(GroupDetailEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun deleteGroup() {
        viewModelScope.launch {
            when (val result = deleteGroupUseCase(groupId)) {
                is ScanlyResult.Success -> _events.emit(GroupDetailEvent.GroupDeleted)
                is ScanlyResult.Failure -> _events.emit(GroupDetailEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun createDocumentInGroup(title: String) {
        viewModelScope.launch {
            when (val result = createDocumentUseCase(title, groupId)) {
                is ScanlyResult.Success -> _events.emit(GroupDetailEvent.OpenDocument(result.value))
                is ScanlyResult.Failure -> _events.emit(GroupDetailEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun addDocumentToGroup(documentId: String) {
        viewModelScope.launch {
            when (val result = setDocumentGroupUseCase(documentId, groupId)) {
                is ScanlyResult.Success -> Unit
                is ScanlyResult.Failure -> _events.emit(GroupDetailEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun removeDocumentFromGroup(documentId: String) {
        viewModelScope.launch {
            when (val result = setDocumentGroupUseCase(documentId, null)) {
                is ScanlyResult.Success -> _events.emit(GroupDetailEvent.ShowMessage("Document removed from group."))
                is ScanlyResult.Failure -> _events.emit(GroupDetailEvent.ShowMessage(result.error.message))
            }
        }
    }

    /** Save all documents merged into one PDF. */
    fun saveMergedPdf(options: PdfExportOptions = PdfExportOptions()) {
        runGroupExport(
            progressLabel = { current, total -> "Generating page $current of $total…" },
            action = { onProgress -> exportGroupPdfUseCase(groupId, options, onProgress) },
            onSuccess = { artifact ->
                _events.emit(
                    GroupDetailEvent.ExportReady(
                        filePath = artifact.filePath,
                        fileName = artifact.fileName,
                        mimeType = artifact.mimeType,
                    ),
                )
            },
        )
    }

    /** Share all documents merged into one PDF. */
    fun shareMergedPdf(options: PdfExportOptions = PdfExportOptions()) {
        runGroupExport(
            progressLabel = { current, total -> "Generating page $current of $total…" },
            action = { onProgress -> prepareGroupPdfShareUseCase(groupId, options, onProgress) },
            onSuccess = { artifact -> _events.emit(GroupDetailEvent.ShareFiles(artifact)) },
        )
    }

    /** Save each document as its own PDF, bundled in a ZIP. */
    fun saveZippedPdfs(options: PdfExportOptions = PdfExportOptions()) {
        runGroupExport(
            progressLabel = { current, total -> "Generating PDF $current of $total…" },
            action = { onProgress -> exportGroupZippedPdfsUseCase(groupId, options, onProgress) },
            onSuccess = { artifact ->
                _events.emit(
                    GroupDetailEvent.ExportReady(
                        filePath = artifact.filePath,
                        fileName = artifact.fileName,
                        mimeType = artifact.mimeType,
                    ),
                )
            },
        )
    }

    /** Share each document as its own PDF, bundled in a ZIP. */
    fun shareZippedPdfs(options: PdfExportOptions = PdfExportOptions()) {
        runGroupExport(
            progressLabel = { current, total -> "Generating PDF $current of $total…" },
            action = { onProgress -> prepareGroupZippedPdfsShareUseCase(groupId, options, onProgress) },
            onSuccess = { artifact -> _events.emit(GroupDetailEvent.ShareFiles(artifact)) },
        )
    }

    fun cancelExport() {
        exportJob?.cancel()
        _exportProgress.value = null
    }

    private fun <T> runGroupExport(
        progressLabel: (current: Int, total: Int) -> String,
        action: suspend (onProgress: (Int, Int) -> Unit) -> ScanlyResult<T>,
        onSuccess: suspend (T) -> Unit,
    ) {
        if (exportJob?.isActive == true) return
        exportJob = viewModelScope.launch {
            try {
                val result = action { current, total ->
                    _exportProgress.value = ExportProgress(
                        current = current,
                        total = total,
                        label = progressLabel(current, total),
                    )
                }
                when (result) {
                    is ScanlyResult.Success -> {
                        _exportProgress.value = null
                        onSuccess(result.value)
                    }
                    is ScanlyResult.Failure -> {
                        _exportProgress.value = null
                        _events.emit(GroupDetailEvent.ShowMessage(result.error.message))
                    }
                }
            } finally {
                _exportProgress.value = null
            }
        }
    }
}
