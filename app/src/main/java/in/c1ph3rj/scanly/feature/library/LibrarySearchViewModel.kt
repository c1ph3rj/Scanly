package `in`.c1ph3rj.scanly.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.FolderFilter
import `in`.c1ph3rj.scanly.domain.model.LibrarySearchResults
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.PageTextSearch
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.SearchFilters
import `in`.c1ph3rj.scanly.domain.model.SearchScope
import `in`.c1ph3rj.scanly.domain.model.TextIndexProgress
import `in`.c1ph3rj.scanly.domain.search.LibrarySearchRanker
import `in`.c1ph3rj.scanly.domain.search.Completion
import `in`.c1ph3rj.scanly.domain.search.SearchCompletion
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import `in`.c1ph3rj.scanly.domain.search.TextFolding
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentKindsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveTextIndexProgressUseCase
import `in`.c1ph3rj.scanly.domain.usecase.AddRecentSearchUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ClearRecentSearchesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.CompleteSearchWordUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveRecentSearchesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RemoveRecentSearchUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SearchPageTextUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SuggestSearchCorrectionUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LibrarySearchUiState(
    val query: String = "",
    val scope: SearchScope = SearchScope.EVERYWHERE,
    /** Type, date, folder and sort from the filter sheet. */
    val filters: SearchFilters = SearchFilters(),
    /** Kinds that exist in the library, so the type filter never offers an empty choice. */
    val availableKinds: List<DocumentKind> = emptyList(),
    /** Folders to offer in the filter sheet. */
    val folders: List<DocumentGroup> = emptyList(),
    /** How many documents of each type exist, for the "Browse by type" chips. */
    val kindCounts: Map<DocumentKind, Int> = emptyMap(),
    val recentSearches: List<String> = emptyList(),
    val results: LibrarySearchResults = LibrarySearchResults(""),
    val indexProgress: TextIndexProgress? = null,
    /** A corrected query from words in the user's pages, offered when nothing matched. */
    val suggestion: String? = null,
    val isLoading: Boolean = true,
    val hasLibrary: Boolean = false,
) {
    val kindFilter: DocumentKind? get() = filters.kind

    /** A typed query, or filters chosen to browse by. */
    val isSearchActive: Boolean get() = !SearchQuery.parse(query).isBlank || filters.narrows

    /** No query, just filters: every document they allow is listed. */
    val isBrowsing: Boolean get() = SearchQuery.parse(query).isBlank && filters.narrows
    val highlightWords: List<String> get() = SearchQuery.parse(query).allWords
    val isIndexing: Boolean get() = indexProgress?.isComplete == false
}

private data class LibrarySnapshot(
    val documents: List<ScanDocument>,
    val groups: List<DocumentGroup>,
    val kinds: Map<String, DocumentKind>,
)

private data class SearchControls(
    val query: String,
    val scope: SearchScope,
    val filters: SearchFilters,
)

/** Autocomplete for [query] (the text it was computed for, so stale words never show). */
data class CompletionState(
    val query: String = "",
    val completions: List<Completion> = emptyList(),
)

/** Text hits for one query; [hits] is null while that query's text search is in flight. */
private data class TextHitsState(
    val query: String,
    val scope: SearchScope,
    val hits: List<PageTextHit>?,
    val suggestion: String? = null,
    /** Similar words searched because nothing matched the typed ones. */
    val closeTerms: List<String> = emptyList(),
)

/**
 * Two-phase search. Name matches are ranked synchronously on every keystroke, so results
 * appear instantly. The page-text query runs after a short pause in typing and, when it lands,
 * the list re-ranks (text matches move above name-only matches). It also re-runs quietly as
 * the background indexer reads more pages, so results fill in without the user retyping.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibrarySearchViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    observeGroupsUseCase: ObserveGroupsUseCase,
    observeDocumentKindsUseCase: ObserveDocumentKindsUseCase,
    observeTextIndexProgressUseCase: ObserveTextIndexProgressUseCase,
    private val searchPageTextUseCase: SearchPageTextUseCase,
    observeRecentSearchesUseCase: ObserveRecentSearchesUseCase,
    private val addRecentSearchUseCase: AddRecentSearchUseCase,
    private val removeRecentSearchUseCase: RemoveRecentSearchUseCase,
    private val clearRecentSearchesUseCase: ClearRecentSearchesUseCase,
    private val suggestSearchCorrectionUseCase: SuggestSearchCorrectionUseCase,
    private val completeSearchWordUseCase: CompleteSearchWordUseCase,
    private val dispatchers: ScanlyDispatchers,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    /**
     * The text field reads this directly. It must update synchronously with each keystroke;
     * routing it through the ranked [uiState] (computed off the main thread) makes the field
     * receive stale values and jump the cursor.
     */
    val query: StateFlow<String> = savedStateHandle.getStateFlow(QUERY_KEY, "")
    private val scope = MutableStateFlow(SearchScope.EVERYWHERE)
    private val filters = MutableStateFlow(SearchFilters())

    private val indexProgress = observeTextIndexProgressUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val library: Flow<LibrarySnapshot> = combine(
        observeDocumentsUseCase(),
        observeGroupsUseCase(),
        observeDocumentKindsUseCase(),
        ::LibrarySnapshot,
    )

    private val recentSearches = observeRecentSearchesUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val controls = combine(query, scope, filters, ::SearchControls)

    private val textHits: Flow<TextHitsState> = run {
        var lastSearched: Pair<String, SearchScope>? = null
        combine(
            query,
            scope,
            // Re-query as pages get read, but not for every count wobble.
            indexProgress.map { it?.indexedPages ?: 0 }.distinctUntilChanged(),
        ) { text, searchScope, _ -> text to searchScope }
            .flatMapLatest { (text, searchScope) ->
                flow {
                    val parsed = SearchQuery.parse(text)
                    if (parsed.isBlank || searchScope == SearchScope.NAMES) {
                        lastSearched = text to searchScope
                        emit(TextHitsState(text, searchScope, emptyList()))
                        return@flow
                    }
                    if (lastSearched != (text to searchScope)) {
                        // New query: show name matches now, search text once typing settles.
                        emit(TextHitsState(text, searchScope, null))
                        delay(TEXT_SEARCH_DEBOUNCE_MILLIS)
                    }
                    val search: PageTextSearch = when (val result = searchPageTextUseCase(parsed)) {
                        is ScanlyResult.Success -> result.value
                        is ScanlyResult.Failure -> PageTextSearch()
                    }
                    lastSearched = text to searchScope
                    // Close matches already answer "did you mean"; only offer it when they found nothing.
                    val suggestion = if (search.hits.isEmpty()) suggestSearchCorrectionUseCase(parsed) else null
                    emit(TextHitsState(text, searchScope, search.hits, suggestion, search.closeTerms))
                }
            }
            .onStart { emit(TextHitsState("", SearchScope.EVERYWHERE, emptyList())) }
    }

    /** Words finishing the one being typed, from page text and document and folder names. */
    val completions: StateFlow<CompletionState> = combine(query, library) { text, snapshot -> text to snapshot }
        .flatMapLatest { (text, snapshot) ->
            flow {
                val typed = SearchCompletion.typedWord(text)
                if (typed == null) {
                    emit(CompletionState(text))
                    return@flow
                }
                delay(COMPLETION_DEBOUNCE_MILLIS)
                val nameWords = (snapshot.documents.map { it.title } + snapshot.groups.map { it.title })
                    .flatMap { title -> TextFolding.words(TextFolding.fold(title)).map { it.value }.toList() }
                val words = SearchCompletion.merge(
                    prefix = typed.prefix,
                    vocabulary = completeSearchWordUseCase(typed.prefix),
                    nameWords = nameWords,
                    limit = MAX_COMPLETIONS,
                )
                emit(CompletionState(text, words))
            }
        }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CompletionState())

    val uiState: StateFlow<LibrarySearchUiState> = combine(
        controls,
        library,
        textHits,
        indexProgress,
        recentSearches,
    ) { controls, snapshot, hitsState, progress, recent ->
        val parsed = SearchQuery.parse(controls.query)
        // Hits from a previous keystroke must never be ranked against the new query.
        val hits = hitsState.hits.takeIf {
            hitsState.query == controls.query && hitsState.scope == controls.scope
        }
        val availableKinds = snapshot.kinds.values.toSet().sortedBy { it.ordinal }
        // A filter whose type or folder no longer exists would hide everything; drop it.
        val activeFilters = controls.filters.let { chosen ->
            chosen.copy(
                kind = chosen.kind?.takeIf { it in availableKinds },
                folder = chosen.folder.takeIf { folder ->
                    folder !is FolderFilter.In || snapshot.groups.any { it.id == folder.groupId }
                } ?: FolderFilter.Any,
            )
        }
        LibrarySearchUiState(
            query = controls.query,
            scope = controls.scope,
            filters = activeFilters,
            availableKinds = availableKinds,
            folders = snapshot.groups.sortedBy { it.title.lowercase() },
            kindCounts = snapshot.documents
                .mapNotNull { snapshot.kinds[it.id] }
                .groupingBy { it }
                .eachCount(),
            recentSearches = recent,
            results = LibrarySearchRanker.rank(
                query = parsed,
                documents = snapshot.documents,
                groups = snapshot.groups,
                documentKinds = snapshot.kinds,
                textHits = hits,
                scope = controls.scope,
                filters = activeFilters,
                closeTerms = hitsState.closeTerms.takeIf { hits != null }.orEmpty(),
            ),
            indexProgress = progress,
            suggestion = hitsState.suggestion.takeIf { hits != null },
            isLoading = false,
            hasLibrary = snapshot.documents.isNotEmpty(),
        )
    }
        .flowOn(dispatchers.default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibrarySearchUiState(query = query.value))

    fun setQuery(value: String) {
        savedStateHandle[QUERY_KEY] = value
    }

    fun clearQuery() = setQuery("")

    fun setScope(value: SearchScope) {
        scope.value = value
    }

    fun setKindFilter(value: DocumentKind?) {
        filters.value = filters.value.copy(kind = value)
    }

    fun setFilters(value: SearchFilters) {
        filters.value = value
    }

    /** Remembers the current query once the user acts on it (opens a result or submits). */
    fun rememberCurrentQuery() {
        val current = query.value
        if (SearchQuery.parse(current).isBlank) return
        viewModelScope.launch { addRecentSearchUseCase(current) }
    }

    fun removeRecentSearch(value: String) {
        viewModelScope.launch { removeRecentSearchUseCase(value) }
    }

    fun clearRecentSearches() {
        viewModelScope.launch { clearRecentSearchesUseCase() }
    }

    private companion object {
        const val QUERY_KEY = "query"
        const val TEXT_SEARCH_DEBOUNCE_MILLIS = 160L
        const val COMPLETION_DEBOUNCE_MILLIS = 120L
        const val MAX_COMPLETIONS = 6
    }
}
