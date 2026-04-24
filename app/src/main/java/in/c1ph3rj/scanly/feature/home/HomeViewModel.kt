package `in`.c1ph3rj.scanly.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.AssignDocumentToGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeleteDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RenameDocumentUseCase
import kotlinx.coroutines.flow.combine
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

enum class DocumentSortMode(
    val label: String,
) {
    DATE_MODIFIED("Date modified"),
    DATE_CREATED("Date created"),
    NAME("Name"),
    PAGE_COUNT("Page count"),
}

data class HomeUiState(
    val documents: List<ScanDocument> = emptyList(),
    val groups: List<DocumentGroup> = emptyList(),
    val sortMode: DocumentSortMode = DocumentSortMode.DATE_MODIFIED,
    val isLoading: Boolean = true,
) {
    companion object {
        fun initial(): HomeUiState = HomeUiState()
    }
}

sealed interface HomeEvent {
    data class OpenDocument(val documentId: String) : HomeEvent
    data class ShowMessage(val message: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    observeDocumentGroupsUseCase: ObserveDocumentGroupsUseCase,
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val renameDocumentUseCase: RenameDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val assignDocumentToGroupUseCase: AssignDocumentToGroupUseCase,
    private val createDocumentGroupUseCase: CreateDocumentGroupUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState.initial())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                observeDocumentsUseCase(),
                observeDocumentGroupsUseCase(),
            ) { documents, groups -> documents to groups }
                .collectLatest { (documents, groups) ->
                _uiState.update { current ->
                    current.copy(
                        documents = documents.sortedBy(current.sortMode),
                        groups = groups,
                        isLoading = false,
                    )
                }
            }
        }
    }

    fun updateSortMode(sortMode: DocumentSortMode) {
        _uiState.update { current ->
            current.copy(
                sortMode = sortMode,
                documents = current.documents.sortedBy(sortMode),
            )
        }
    }

    fun createDocument(title: String) {
        viewModelScope.launch {
            when (val result = createDocumentUseCase(title)) {
                is ScanlyResult.Success -> _events.emit(HomeEvent.OpenDocument(result.value))
                is ScanlyResult.Failure -> _events.emit(HomeEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun renameDocument(
        documentId: String,
        title: String,
    ) {
        viewModelScope.launch {
            when (val result = renameDocumentUseCase(documentId, title)) {
                is ScanlyResult.Success -> _events.emit(HomeEvent.ShowMessage("Document renamed."))
                is ScanlyResult.Failure -> _events.emit(HomeEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            when (val result = deleteDocumentUseCase(documentId)) {
                is ScanlyResult.Success -> _events.emit(HomeEvent.ShowMessage("Document deleted."))
                is ScanlyResult.Failure -> _events.emit(HomeEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun changeDocumentGroup(
        documentId: String,
        groupId: String?,
    ) {
        viewModelScope.launch {
            when (val result = assignDocumentToGroupUseCase(documentId, groupId)) {
                is ScanlyResult.Success -> _events.emit(HomeEvent.ShowMessage("Document group updated."))
                is ScanlyResult.Failure -> _events.emit(HomeEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun createGroupForDocument(
        documentId: String,
        name: String,
    ) {
        viewModelScope.launch {
            when (val createResult = createDocumentGroupUseCase(name)) {
                is ScanlyResult.Success -> {
                    when (val assignResult = assignDocumentToGroupUseCase(documentId, createResult.value)) {
                        is ScanlyResult.Success -> _events.emit(HomeEvent.ShowMessage("Group created and document moved."))
                        is ScanlyResult.Failure -> _events.emit(HomeEvent.ShowMessage(assignResult.error.message))
                    }
                }

                is ScanlyResult.Failure -> _events.emit(HomeEvent.ShowMessage(createResult.error.message))
            }
        }
    }
}

private fun List<ScanDocument>.sortedBy(
    sortMode: DocumentSortMode,
): List<ScanDocument> {
    return when (sortMode) {
        DocumentSortMode.DATE_MODIFIED -> sortedByDescending(ScanDocument::updatedAtMillis)
        DocumentSortMode.DATE_CREATED -> sortedByDescending(ScanDocument::createdAtMillis)
        DocumentSortMode.NAME -> sortedBy { document -> document.title.lowercase() }
        DocumentSortMode.PAGE_COUNT -> sortedByDescending(ScanDocument::pageCount)
    }
}
