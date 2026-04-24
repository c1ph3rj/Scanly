package `in`.c1ph3rj.scanly.feature.groups

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.AssignDocumentToGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeleteDocumentGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RenameDocumentGroupUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class GroupsUiState(
    val groups: List<DocumentGroup> = emptyList(),
    val documents: List<ScanDocument> = emptyList(),
    val isLoading: Boolean = true,
) {
    val ungroupedDocuments: List<ScanDocument>
        get() = documents.filter { document -> document.groupId == null }
}

sealed interface GroupsEvent {
    data class ShowMessage(val message: String) : GroupsEvent
}

@HiltViewModel
class GroupsViewModel @Inject constructor(
    observeDocumentGroupsUseCase: ObserveDocumentGroupsUseCase,
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val createDocumentGroupUseCase: CreateDocumentGroupUseCase,
    private val renameDocumentGroupUseCase: RenameDocumentGroupUseCase,
    private val deleteDocumentGroupUseCase: DeleteDocumentGroupUseCase,
    private val assignDocumentToGroupUseCase: AssignDocumentToGroupUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GroupsUiState())
    val uiState: StateFlow<GroupsUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<GroupsEvent>()
    val events: SharedFlow<GroupsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                observeDocumentGroupsUseCase(),
                observeDocumentsUseCase(),
            ) { groups, documents -> groups to documents }
                .collectLatest { (groups, documents) ->
                    _uiState.update { current ->
                        current.copy(
                            groups = groups,
                            documents = documents,
                            isLoading = false,
                        )
                    }
                }
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            when (val result = createDocumentGroupUseCase(name)) {
                is ScanlyResult.Success -> _events.emit(GroupsEvent.ShowMessage("Group created."))
                is ScanlyResult.Failure -> _events.emit(GroupsEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun renameGroup(
        groupId: String,
        name: String,
    ) {
        viewModelScope.launch {
            when (val result = renameDocumentGroupUseCase(groupId, name)) {
                is ScanlyResult.Success -> _events.emit(GroupsEvent.ShowMessage("Group renamed."))
                is ScanlyResult.Failure -> _events.emit(GroupsEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun deleteGroup(groupId: String) {
        viewModelScope.launch {
            when (val result = deleteDocumentGroupUseCase(groupId)) {
                is ScanlyResult.Success -> _events.emit(GroupsEvent.ShowMessage("Group removed. Documents were kept."))
                is ScanlyResult.Failure -> _events.emit(GroupsEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun assignDocument(
        documentId: String,
        groupId: String?,
    ) {
        viewModelScope.launch {
            when (val result = assignDocumentToGroupUseCase(documentId, groupId)) {
                is ScanlyResult.Success -> _events.emit(GroupsEvent.ShowMessage("Document updated."))
                is ScanlyResult.Failure -> _events.emit(GroupsEvent.ShowMessage(result.error.message))
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
                        is ScanlyResult.Success -> _events.emit(GroupsEvent.ShowMessage("Group created and document moved."))
                        is ScanlyResult.Failure -> _events.emit(GroupsEvent.ShowMessage(assignResult.error.message))
                    }
                }

                is ScanlyResult.Failure -> _events.emit(GroupsEvent.ShowMessage(createResult.error.message))
            }
        }
    }
}
