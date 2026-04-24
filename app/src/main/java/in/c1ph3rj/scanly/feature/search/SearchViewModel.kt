package `in`.c1ph3rj.scanly.feature.search

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

data class SearchUiState(
    val documents: List<ScanDocument> = emptyList(),
    val groups: List<DocumentGroup> = emptyList(),
)

@HiltViewModel
class SearchViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    observeDocumentGroupsUseCase: ObserveDocumentGroupsUseCase,
) : ViewModel() {
    val uiState: StateFlow<SearchUiState> = combine(
        observeDocumentsUseCase(),
        observeDocumentGroupsUseCase(),
    ) { documents, groups ->
        SearchUiState(
            documents = documents,
            groups = groups,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SearchUiState(),
    )
}
