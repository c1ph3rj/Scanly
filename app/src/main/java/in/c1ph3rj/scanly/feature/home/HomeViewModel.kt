package `in`.c1ph3rj.scanly.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.DocumentGroup
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveRecentDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveRecentGroupsUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val recentGroups: List<DocumentGroup> = emptyList(),
    val recentDocuments: List<ScanDocument> = emptyList(),
    val isLoading: Boolean = false,
)

sealed interface HomeEvent {
    data class OpenDocument(val documentId: String) : HomeEvent
    data class ShowMessage(val message: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeRecentGroupsUseCase: ObserveRecentGroupsUseCase,
    observeRecentDocumentsUseCase: ObserveRecentDocumentsUseCase,
    private val createDocumentUseCase: CreateDocumentUseCase,
) : ViewModel() {

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    val uiState: StateFlow<HomeUiState> = combine(
        observeRecentGroupsUseCase(limit = 6),
        observeRecentDocumentsUseCase(limit = 8),
    ) { groups, docs ->
        HomeUiState(recentGroups = groups, recentDocuments = docs, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(isLoading = false),
    )

    fun createDocument(title: String) {
        viewModelScope.launch {
            when (val result = createDocumentUseCase(title)) {
                is ScanlyResult.Success -> _events.emit(HomeEvent.OpenDocument(result.value))
                is ScanlyResult.Failure -> _events.emit(HomeEvent.ShowMessage(result.error.message))
            }
        }
    }
}
