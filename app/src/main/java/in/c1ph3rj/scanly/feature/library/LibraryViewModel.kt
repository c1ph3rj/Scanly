package `in`.c1ph3rj.scanly.feature.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.model.GroupTitleFormat
import `in`.c1ph3rj.scanly.domain.usecase.SuggestDocumentTitleUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SuggestGroupTitleUseCase
import `in`.c1ph3rj.scanly.domain.usecase.CreateGroupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.MoveDocumentToTrashUseCase
import `in`.c1ph3rj.scanly.domain.usecase.MoveGroupToTrashUseCase
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
    RecentlyUpdated("Last modified", "Latest changes first"),
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
    val selectedGroupId: String? = null,
    val isLoading: Boolean = false,
) {
    private val normalizedQuery: String get() = searchQuery.trim()

    val visibleGroups: List<DocumentGroup>
        get() {
            if (selectedTab == LibraryTab.Documents || selectedGroupId != null) return emptyList()
            return groups
                .filter { normalizedQuery.isEmpty() || it.title.contains(normalizedQuery, ignoreCase = true) }
                .sortedWith(groupComparator(sortOption))
        }

    val visibleDocuments: List<ScanDocument>
        get() {
            if (selectedTab == LibraryTab.Folders) return emptyList()
            return allDocuments
                .filter { selectedGroupId == null || it.groupId == selectedGroupId }
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
    val groupId: String?,
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
    private val moveDocumentToTrashUseCase: MoveDocumentToTrashUseCase,
    private val createGroupUseCase: CreateGroupUseCase,
    private val renameGroupUseCase: RenameGroupUseCase,
    private val moveGroupToTrashUseCase: MoveGroupToTrashUseCase,
    private val setDocumentGroupUseCase: SetDocumentGroupUseCase,
    private val suggestDocumentTitleUseCase: SuggestDocumentTitleUseCase,
    private val suggestGroupTitleUseCase: SuggestGroupTitleUseCase,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _selectedTab = MutableStateFlow(LibraryTab.All)
    private val _sortOption = MutableStateFlow(LibrarySortOption.RecentlyUpdated)
    private val _selectedGroupId = MutableStateFlow<String?>(null)
    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()

    // Guards against a fast double-tap on a dialog's confirm creating the item twice.
    private var isCreatingDocument = false
    private var isCreatingGroup = false

    private val controls = combine(
        _searchQuery,
        _selectedTab,
        _sortOption,
        _selectedGroupId,
    ) { query, tab, sortOption, groupId ->
        LibraryControls(query, tab, sortOption, groupId)
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
            // The Folders tab has no content (or back control) once the last folder is gone.
            selectedTab = if (controls.tab == LibraryTab.Folders && groups.isEmpty()) LibraryTab.All else controls.tab,
            sortOption = controls.sortOption,
            selectedGroupId = controls.groupId?.takeIf { id -> groups.any { it.id == id } },
            isLoading = false,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LibraryUiState(isLoading = true),
    )

    init {
        // Drop the stored Folders tab when the last folder disappears so it doesn't snap back
        // the next time a folder is created.
        viewModelScope.launch {
            observeGroupsUseCase().collect { groups ->
                if (groups.isEmpty() && _selectedTab.value == LibraryTab.Folders) {
                    _selectedTab.value = LibraryTab.All
                }
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun selectTab(tab: LibraryTab) {
        _selectedTab.value = tab
        _selectedGroupId.value = null
    }

    fun selectGroup(groupId: String) {
        _selectedTab.value = LibraryTab.Documents
        _selectedGroupId.value = groupId
    }

    fun selectSortOption(option: LibrarySortOption) {
        _sortOption.value = option
    }

    fun createDocument(title: String, groupId: String? = null) {
        if (isCreatingDocument) return
        isCreatingDocument = true
        viewModelScope.launch {
            try {
                when (val result = createDocumentUseCase(title, groupId)) {
                    is ScanlyResult.Success -> _events.emit(LibraryEvent.OpenDocument(result.value))
                    is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
                }
            } finally {
                isCreatingDocument = false
            }
        }
    }

    suspend fun suggestDocumentTitle(format: DocumentTitleFormat): String =
        suggestDocumentTitleUseCase(format)

    suspend fun suggestGroupTitle(format: GroupTitleFormat): String =
        suggestGroupTitleUseCase(format)

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
            val title = uiState.value.allDocuments.firstOrNull { it.id == documentId }?.title ?: "Document"
            when (val result = moveDocumentToTrashUseCase(documentId, title)) {
                is ScanlyResult.Success -> Unit
                is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun createGroup(title: String) {
        if (isCreatingGroup) return
        isCreatingGroup = true
        viewModelScope.launch {
            try {
                when (val result = createGroupUseCase(title)) {
                    is ScanlyResult.Success -> _events.emit(LibraryEvent.OpenGroup(result.value))
                    is ScanlyResult.Failure -> _events.emit(LibraryEvent.ShowMessage(result.error.message))
                }
            } finally {
                isCreatingGroup = false
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

    fun deleteGroup(groupId: String, includeDocuments: Boolean) {
        viewModelScope.launch {
            val title = uiState.value.groups.firstOrNull { it.id == groupId }?.title ?: "Folder"
            when (val result = moveGroupToTrashUseCase(groupId, title, includeDocuments)) {
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
