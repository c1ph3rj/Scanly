package `in`.c1ph3rj.scanly.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.CreateGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeleteDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeleteGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveUngroupedDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RenameDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RenameGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetDocumentGroupUseCase
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

data class LibraryUiState(
    val groups: List<DocumentGroup> = emptyList(),
    val ungroupedDocuments: List<ScanDocument> = emptyList(),
    val allDocuments: List<ScanDocument> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
) {
    private val normalizedQuery: String get() = searchQuery.trim()

    val filteredGroups: List<DocumentGroup>
        get() = if (normalizedQuery.isEmpty()) groups
        else groups.filter { it.title.contains(normalizedQuery, ignoreCase = true) }

    // Search spans every document, including those nested inside folders, so results
    // are never hidden just because a document lives in a group.
    val filteredDocuments: List<ScanDocument>
        get() = if (normalizedQuery.isEmpty()) ungroupedDocuments
        else allDocuments.filter { it.title.contains(normalizedQuery, ignoreCase = true) }

    val isSearchActive: Boolean get() = normalizedQuery.isNotEmpty()
}

sealed interface LibraryEvent {
    data class OpenDocument(val documentId: String) : LibraryEvent
    data class OpenGroup(val groupId: String) : LibraryEvent
    data class ShowMessage(val message: String) : LibraryEvent
}

@HiltViewModel
class LibraryViewModel @Inject constructor(
    observeGroupsUseCase: ObserveGroupsUseCase,
    observeUngroupedDocumentsUseCase: ObserveUngroupedDocumentsUseCase,
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val renameDocumentUseCase: RenameDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
    private val renameGroupUseCase: RenameGroupUseCase,
    private val deleteGroupUseCase: DeleteGroupUseCase,
    private val setDocumentGroupUseCase: SetDocumentGroupUseCase,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    val uiState: StateFlow<LibraryUiState> = combine(
        observeGroupsUseCase(),
        observeUngroupedDocumentsUseCase(),
        observeDocumentsUseCase(),
        _searchQuery,
    ) { groups, ungrouped, allDocs, query ->
        LibraryUiState(
            groups = groups,
            ungroupedDocuments = ungrouped,
            allDocuments = allDocs,
            searchQuery = query,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(),
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun createDocument(title: String, groupId: String? = null) {
        viewModelScope.launch {
            when (val result = createDocumentUseCase(title, groupId)) {
                is ScanlyResult.Success -> _events.emit(LibraryEvent.OpenDocument(result.value))
                is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun renameDocument(documentId: String, title: String) {
        viewModelScope.launch {
            when (val result = renameDocumentUseCase(documentId, title)) {
                is ScanlyResult.Success -> Unit
                is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun deleteDocument(documentId: String) {
        viewModelScope.launch {
            when (val result = deleteDocumentUseCase(documentId)) {
                is ScanlyResult.Success -> Unit
                is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun createGroup(title: String) {
        viewModelScope.launch {
            when (val result = createGroupUseCase(title)) {
                is ScanlyResult.Success -> _events.emit(LibraryEvent.OpenGroup(result.value))
                is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun renameGroup(groupId: String, title: String) {
        viewModelScope.launch {
            when (val result = renameGroupUseCase(groupId, title)) {
                is ScanlyResult.Success -> Unit
                is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            when (val result = deleteGroupUseCase(groupId)) {
                is ScanlyResult.Success -> Unit
                is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun moveDocumentToGroup(documentId: String, groupId: String?) {
        viewModelScope.launch {
            when (val result = setDocumentGroupUseCase(documentId, groupId)) {
                is ScanlyResult.Success -> _events.emit(
                    LibraryEvent.ShowMessage(moveConfirmationMessage(groupId)),
                )

                is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun moveDocumentToNewGroup(documentId: String, name: String) {
        viewModelScope.launch {
            when (val createResult = createGroupUseCase(name)) {
                is ScanlyResult.Success -> {
                    when (val moveResult = setDocumentGroupUseCase(documentId, createResult.value)) {
                        is ScanlyResult.Success -> _events.emit(LibraryEvent.ShowMessage("Moved to $name"))
                        is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(moveResult.error.message))
                    }
                }

                is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(createResult.error.message))
            }
        }
    }

    private fun moveConfirmationMessage(groupId: String?): String {
        if (groupId == null) {
            return "Removed from folder"
        }
        val folderName = uiState.value.groups.firstOrNull { it.id == groupId }?.title
        return if (folderName != null) "Moved to $folderName" else "Moved to folder"
    }
}
