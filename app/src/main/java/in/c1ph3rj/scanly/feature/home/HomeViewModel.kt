package `in`.c1ph3rj.scanly.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeleteDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RenameDocumentUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val documents: List<ScanDocument> = emptyList(),
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
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val renameDocumentUseCase: RenameDocumentUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState.initial())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            observeDocumentsUseCase().collectLatest { documents ->
                _uiState.value = HomeUiState(
                    documents = documents,
                    isLoading = false,
                )
            }
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
}
