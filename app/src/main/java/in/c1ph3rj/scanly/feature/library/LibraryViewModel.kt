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

enum class LibraryTab {
    All,
    Folders,
    Documents,
}

enum class LibrarySortOption(
    val title: String,
    val description: String,
) {
    RecentlyUpdated("Recently updated", "Latest changes first"),
    OldestUpdated("Oldest updated", "Earliest changes first"),
    NameAscending("Name A–Z", "Alphabetical order"),
    NameDescending("Name Z–A", "Reverse alphabetical order"),
    NewestCreated("Newest created", "Recently added first"),
    OldestCreated("Oldest created", "Earliest added first"),
}

data class LibraryUiState(
    val groups: List<DocumentGroup> = emptyList(),
    val ungroupedDocuments: List<ScanDocument> = emptyList(),
    val allDocuments: List<ScanDocument> = emptyList(),
    val searchQuery: String = "",
    val selectedTab: LibraryTab = LibraryTab.All,
    val sortOption: LibrarySortOption = LibrarySortOption.RecentlyUpdated,
    val isLoading: Boolean = false,
) {
    private val normalizedQuery: String get() = searchQuery.trim()

    val visibleGroups: List<DocumentGroup>
        get() {
            if (selectedTab == LibraryTab.Documents) return emptyList()
            return groups
                .filter { normalizedQuery.isEmpty() || it.title.contains(normalizedQuery, ignoreCase = true) }
                .sortedWith(groupComparator(sortOption))
        }

    val visibleDocuments: List<ScanDocument>
        get() {
            if (selectedTab == LibraryTab.Folders) return emptyList()
            // All avoids duplicating foldered documents in its normal overview. Search and the
            // Documents tab deliberately span every document, including documents in folders.
            val source = if (selectedTab == LibraryTab.Documents || isSearchActive) {
                allDocuments
            } else {
                ungroupedDocuments
            }
            return source
                .filter { normalizedQuery.isEmpty() || it.title.contains(normalizedQuery, ignoreCase = true) }
                .sortedWith(documentComparator(sortOption))
        }

    val isSearchActive: Boolean get() = normalizedQuery.isNotEmpty()
    val hasAnyItems: Boolean get() = groups.isNotEmpty() || allDocuments.isNotEmpty()
}

private data class LibraryControls(
    val query: String,
    val tab: LibraryTab,
    val sortOption: LibrarySortOption,
)

private fun groupComparator(option: LibrarySortOption): Comparator<DocumentGroup> = when (option) {
    LibrarySortOption.RecentlyUpdated ->
        compareByDescending<DocumentGroup> { it.updatedAtMillis }.thenByGroupTitle().thenBy { it.id }
    LibrarySortOption.OldestUpdated ->
        compareBy<DocumentGroup> { it.updatedAtMillis }.thenByGroupTitle().thenBy { it.id }
    LibrarySortOption.NameAscending ->
        titleComparator<DocumentGroup> { it.title }.thenBy { it.id }
    LibrarySortOption.NameDescending ->
        titleComparator<DocumentGroup> { it.title }.reversed().thenBy { it.id }
    LibrarySortOption.NewestCreated ->
        compareByDescending<DocumentGroup> { it.createdAtMillis }.thenByGroupTitle().thenBy { it.id }
    LibrarySortOption.OldestCreated ->
        compareBy<DocumentGroup> { it.createdAtMillis }.thenByGroupTitle().thenBy { it.id }
}

private fun documentComparator(option: LibrarySortOption): Comparator<ScanDocument> = when (option) {
    LibrarySortOption.RecentlyUpdated ->
        compareByDescending<ScanDocument> { it.updatedAtMillis }.thenByDocumentTitle().thenBy { it.id }
    LibrarySortOption.OldestUpdated ->
        compareBy<ScanDocument> { it.updatedAtMillis }.thenByDocumentTitle().thenBy { it.id }
    LibrarySortOption.NameAscending ->
        titleComparator<ScanDocument> { it.title }.thenBy { it.id }
    LibrarySortOption.NameDescending ->
        titleComparator<ScanDocument> { it.title }.reversed().thenBy { it.id }
    LibrarySortOption.NewestCreated ->
        compareByDescending<ScanDocument> { it.createdAtMillis }.thenByDocumentTitle().thenBy { it.id }
    LibrarySortOption.OldestCreated ->
        compareBy<ScanDocument> { it.createdAtMillis }.thenByDocumentTitle().thenBy { it.id }
}

private fun <T> titleComparator(selector: (T) -> String): Comparator<T> =
    Comparator { left, right -> selector(left).compareTo(selector(right), ignoreCase = true) }

private fun Comparator<DocumentGroup>.thenByGroupTitle(): Comparator<DocumentGroup> =
    then(titleComparator { it.title })

private fun Comparator<ScanDocument>.thenByDocumentTitle(): Comparator<ScanDocument> =
    then(titleComparator { it.title })

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
    private val _selectedTab = MutableStateFlow(LibraryTab.All)
    private val _sortOption = MutableStateFlow(LibrarySortOption.RecentlyUpdated)
    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    private val controls = combine(
        _searchQuery,
        _selectedTab,
        _sortOption,
    ) { query, tab, sortOption ->
        LibraryControls(query, tab, sortOption)
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        observeGroupsUseCase(),
        observeUngroupedDocumentsUseCase(),
        observeDocumentsUseCase(),
        controls,
    ) { groups, ungrouped, allDocs, controls ->
        LibraryUiState(
            groups = groups,
            ungroupedDocuments = ungrouped,
            allDocuments = allDocs,
            searchQuery = controls.query,
            selectedTab = controls.tab,
            sortOption = controls.sortOption,
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(isLoading = true),
    )

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
    }

    fun selectSortOption(option: LibrarySortOption) {
        _sortOption.value = option
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
