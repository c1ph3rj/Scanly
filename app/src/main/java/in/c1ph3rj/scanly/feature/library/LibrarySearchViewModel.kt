package `in`.c1ph3rj.scanly.feature.library

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.DocumentKind
import `in`.c1ph3rj.scanly.domain.model.LibrarySearchResults
import `in`.c1ph3rj.scanly.domain.model.PageTextHit
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.SearchScope
import `in`.c1ph3rj.scanly.domain.model.TextIndexProgress
import `in`.c1ph3rj.scanly.domain.search.LibrarySearchRanker
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentKindsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveTextIndexProgressUseCase
import `in`.c1ph3rj.scanly.domain.usecase.AddRecentSearchUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ClearRecentSearchesUseCase
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
    val kindFilter: DocumentKind? = null,
    /** Kinds that exist in the library, so the type filter never offers an empty choice. */
    val availableKinds: List<DocumentKind> = emptyList(),
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
    /** A typed query, or a document type chosen to browse. */
    val isSearchActive: Boolean get() = !SearchQuery.parse(query).isBlank || kindFilter != null
    val isBrowsingKind: Boolean get() = SearchQuery.parse(query).isBlank && kindFilter != null
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
    val kind: DocumentKind?,
)

/** Text hits for one query; [hits] is null while that query's text search is in flight. */
private data class TextHitsState(
    val query: String,
    val scope: SearchScope,
    val hits: List<PageTextHit>?,
    val suggestion: String? = null,
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
    private val kindFilter = MutableStateFlow<DocumentKind?>(null)

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

    private val controls = combine(query, scope, kindFilter, ::SearchControls)

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
                    val hits = when (val result = searchPageTextUseCase(parsed)) {
                        is ScanlyResult.Success -> result.value
                        is ScanlyResult.Failure -> emptyList()
                    }
                    lastSearched = text to searchScope
                    val suggestion = if (hits.isEmpty()) suggestSearchCorrectionUseCase(parsed) else null
                    emit(TextHitsState(text, searchScope, hits, suggestion))
                }
            }
            .onStart { emit(TextHitsState("", SearchScope.EVERYWHERE, emptyList())) }
    }

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
        LibrarySearchUiState(
            query = controls.query,
            scope = controls.scope,
            kindFilter = controls.kind?.takeIf { it in availableKinds },
            availableKinds = availableKinds,
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
                kindFilter = controls.kind?.takeIf { it in availableKinds },
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
        kindFilter.value = value
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
    }
}
