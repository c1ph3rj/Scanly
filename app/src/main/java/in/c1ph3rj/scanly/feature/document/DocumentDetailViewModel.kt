package `in`.c1ph3rj.scanly.feature.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.usecase.DeletePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.MovePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentUseCase
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

data class DocumentDetailUiState(
    val document: ScanDocument? = null,
    val pages: List<ScanPage> = emptyList(),
    val selectedPageId: String? = null,
    val missingDocument: Boolean = false,
    val isMutatingPage: Boolean = false,
) {
    val selectedPage: ScanPage?
        get() = pages.firstOrNull { page -> page.id == selectedPageId } ?: pages.firstOrNull()
}

sealed interface DocumentDetailEvent {
    data class ShowMessage(val message: String) : DocumentDetailEvent
}

@HiltViewModel
class DocumentDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeDocumentUseCase: ObserveDocumentUseCase,
    private val observeDocumentPagesUseCase: ObserveDocumentPagesUseCase,
    private val movePageUseCase: MovePageUseCase,
    private val deletePageUseCase: DeletePageUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[DocumentDestination.documentIdArgument])

    private val _uiState = MutableStateFlow(DocumentDetailUiState())
    val uiState: StateFlow<DocumentDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DocumentDetailEvent>()
    val events: SharedFlow<DocumentDetailEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            observeDocumentUseCase(documentId).collectLatest { document ->
                _uiState.update { current ->
                    current.copy(
                        document = document,
                        missingDocument = document == null,
                    )
                }
            }
        }

        viewModelScope.launch {
            observeDocumentPagesUseCase(documentId).collectLatest { pages ->
                _uiState.update { current ->
                    current.copy(
                        pages = pages,
                        selectedPageId = resolveSelectedPageId(
                            currentSelectedPageId = current.selectedPageId,
                            pages = pages,
                        ),
                    )
                }
            }
        }
    }

    fun selectPage(pageId: String) {
        _uiState.update { current ->
            current.copy(selectedPageId = pageId)
        }
    }

    fun moveSelectedPageLeft() {
        val selectedPage = _uiState.value.selectedPage ?: return
        if (selectedPage.pageIndex <= 0) return
        mutateSelectedPage(
            successMessage = "Moved page earlier in the document.",
        ) {
            movePageUseCase(
                pageId = selectedPage.id,
                targetIndex = selectedPage.pageIndex - 1,
            )
        }
    }

    fun moveSelectedPageRight() {
        val snapshot = _uiState.value
        val selectedPage = snapshot.selectedPage ?: return
        if (selectedPage.pageIndex >= snapshot.pages.lastIndex) return
        mutateSelectedPage(
            successMessage = "Moved page later in the document.",
        ) {
            movePageUseCase(
                pageId = selectedPage.id,
                targetIndex = selectedPage.pageIndex + 1,
            )
        }
    }

    fun deleteSelectedPage() {
        val selectedPage = _uiState.value.selectedPage ?: return
        mutateSelectedPage(
            successMessage = "Deleted page ${selectedPage.pageIndex + 1}.",
        ) {
            deletePageUseCase(selectedPage.id)
        }
    }

    private fun mutateSelectedPage(
        successMessage: String? = null,
        action: suspend () -> ScanlyResult<Unit>,
    ) {
        if (_uiState.value.isMutatingPage) {
            return
        }

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isMutatingPage = true) }
            when (val result = action()) {
                is ScanlyResult.Success -> {
                    _uiState.update { current ->
                        current.copy(isMutatingPage = false)
                    }
                    if (successMessage != null) {
                        _events.emit(DocumentDetailEvent.ShowMessage(successMessage))
                    }
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isMutatingPage = false) }
                    _events.emit(DocumentDetailEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

}

internal fun resolveSelectedPageId(
    currentSelectedPageId: String?,
    pages: List<ScanPage>,
): String? {
    if (pages.isEmpty()) {
        return null
    }
    return pages.firstOrNull { page -> page.id == currentSelectedPageId }?.id
        ?: pages.first().id
}
