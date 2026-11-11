package `in`.c1ph3rj.scanly.feature.document

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SplitDocumentUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Groups page positions 0 until [pageCount] into parts, cutting after each position in
 * [cuts] (a cut after the last page means nothing). Pure, so it is unit tested.
 */
fun partsFromCuts(pageCount: Int, cuts: Set<Int>): List<List<Int>> {
    if (pageCount <= 0) return emptyList()
    val parts = mutableListOf<List<Int>>()
    var start = 0
    for (position in 0 until pageCount) {
        if (position in cuts || position == pageCount - 1) {
            parts += (start..position).toList()
            start = position + 1
        }
    }
    return parts
}

/** A cut after every page but the last: each page becomes its own document. */
fun everyPageCuts(pageCount: Int): Set<Int> = (0 until (pageCount - 1).coerceAtLeast(0)).toSet()

data class DocumentSplitUiState(
    val document: ScanDocument? = null,
    val pages: List<ScanPage> = emptyList(),
    val isLoading: Boolean = true,
    /** 0-based positions to cut after. */
    val cuts: Set<Int> = emptySet(),
    val keepOriginal: Boolean = true,
    val isSplitting: Boolean = false,
) {
    val parts: List<List<Int>> get() = partsFromCuts(pages.size, cuts.filter { it < pages.size - 1 }.toSet())

    val canSplit: Boolean get() = !isSplitting && document != null && parts.size >= 2
}

sealed interface DocumentSplitEvent {
    data class ShowMessage(val message: String) : DocumentSplitEvent
    /** Finished: the new documents sit in [groupId] (null = the Library, outside folders). */
    data class Done(val groupId: String?, val originalKept: Boolean, val created: Int) : DocumentSplitEvent
}

@HiltViewModel
class DocumentSplitViewModel @Inject constructor(
    observeDocumentUseCase: ObserveDocumentUseCase,
    observeDocumentPagesUseCase: ObserveDocumentPagesUseCase,
    private val splitDocumentUseCase: SplitDocumentUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val documentId: String = savedStateHandle.get<String>(DocumentSplitDestination.documentIdArgument).orEmpty()

    private val _uiState = MutableStateFlow(
        DocumentSplitUiState(
            cuts = savedStateHandle.get<IntArray>(KeyCuts)?.toSet().orEmpty(),
            keepOriginal = savedStateHandle.get<Boolean>(KeyKeepOriginal) ?: true,
        ),
    )
    val uiState: StateFlow<DocumentSplitUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DocumentSplitEvent>()
    val events: SharedFlow<DocumentSplitEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(observeDocumentUseCase(documentId), observeDocumentPagesUseCase(documentId)) { document, pages ->
                document to pages
            }.collect { (document, pages) ->
                // Once split without keeping the original, the document leaves the live list;
                // keep showing the last state until the screen closes.
                if (_uiState.value.isSplitting && document == null) return@collect
                _uiState.update { it.copy(document = document, pages = pages, isLoading = false) }
            }
        }
    }

    fun toggleCut(position: Int) {
        updateCuts { if (position in it) it - position else it + position }
    }

    fun cutEveryPage() = updateCuts { everyPageCuts(_uiState.value.pages.size) }

    fun clearCuts() = updateCuts { emptySet() }

    private fun updateCuts(change: (Set<Int>) -> Set<Int>) {
        _uiState.update { it.copy(cuts = change(it.cuts)) }
        savedStateHandle[KeyCuts] = _uiState.value.cuts.toIntArray()
    }

    fun setKeepOriginal(keep: Boolean) {
        _uiState.update { it.copy(keepOriginal = keep) }
        savedStateHandle[KeyKeepOriginal] = keep
    }

    fun split() {
        val state = _uiState.value
        if (!state.canSplit) return
        val parts = state.parts
        val groupId = state.document?.groupId
        _uiState.update { it.copy(isSplitting = true) }
        viewModelScope.launch {
            when (val result = splitDocumentUseCase(documentId, parts, state.keepOriginal)) {
                is ScanlyResult.Success -> _events.emit(
                    DocumentSplitEvent.Done(groupId = groupId, originalKept = state.keepOriginal, created = result.value.size),
                )
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isSplitting = false) }
                    _events.emit(DocumentSplitEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    private companion object {
        const val KeyCuts = "document_split_cuts"
        const val KeyKeepOriginal = "document_split_keep_original"
    }
}
