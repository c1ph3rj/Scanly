package `in`.c1ph3rj.scanly.feature.text

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PageText
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentTextsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecognizeDocumentTextUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecognizePageTextUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecordPositiveMomentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.UpdatePageTextUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

object PageTextDestination {
    const val documentIdArgument = "documentId"
    const val pageArgument = "page"
    const val queryArgument = "query"
    const val routePattern =
        "document/{$documentIdArgument}/text?$pageArgument={$pageArgument}&$queryArgument={$queryArgument}"

    fun route(documentId: String, pageIndex: Int = 0, query: String = ""): String =
        "document/$documentId/text?$pageArgument=$pageIndex&$queryArgument=${Uri.encode(query)}"
}

data class PageTextItem(
    val page: ScanPage,
    val text: PageText?,
) {
    /** The page image changed (retake, crop, filter) after its text was read. */
    val isStale: Boolean get() = text != null && text.sourceUpdatedAtMillis != page.updatedAtMillis
}

sealed interface RecognitionStatus {
    data object Idle : RecognitionStatus
    data class Running(val done: Int, val total: Int) : RecognitionStatus
    data class Failed(val message: String) : RecognitionStatus
}

data class PageTextUiState(
    val document: ScanDocument? = null,
    val pages: List<PageTextItem> = emptyList(),
    val recognition: RecognitionStatus = RecognitionStatus.Idle,
    val query: String = "",
    val initialPageIndex: Int = 0,
    val isLoading: Boolean = true,
) {
    val highlightWords: List<String> get() = SearchQuery.parse(query).allWords

    /** All page text, in page order, for "Copy all" and "Share". */
    val documentText: String
        get() = pages.mapNotNull { item -> item.text?.text?.takeIf(String::isNotBlank) }
            .joinToString("\n\n")

    val hasAnyText: Boolean get() = pages.any { it.text?.hasText == true }
}

sealed interface PageTextEvent {
    data class ShowMessage(val message: String) : PageTextEvent
}

@HiltViewModel
class PageTextViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeDocumentUseCase: ObserveDocumentUseCase,
    observeDocumentPagesUseCase: ObserveDocumentPagesUseCase,
    observeDocumentTextsUseCase: ObserveDocumentTextsUseCase,
    private val recognizeDocumentTextUseCase: RecognizeDocumentTextUseCase,
    private val recognizePageTextUseCase: RecognizePageTextUseCase,
    private val updatePageTextUseCase: UpdatePageTextUseCase,
    private val recordPositiveMomentUseCase: RecordPositiveMomentUseCase,
) : ViewModel() {
    private val documentId: String = checkNotNull(savedStateHandle[PageTextDestination.documentIdArgument])
    // The route argument arrives as a String, but a restored state may hold an Int; read it untyped.
    private val initialPage: Int = when (val raw = savedStateHandle.get<Any?>(PageTextDestination.pageArgument)) {
        is Int -> raw
        is String -> raw.toIntOrNull()
        else -> null
    } ?: 0
    private val query = MutableStateFlow(savedStateHandle.get<String>(PageTextDestination.queryArgument).orEmpty())
    private val recognition = MutableStateFlow<RecognitionStatus>(RecognitionStatus.Idle)
    private val _events = MutableSharedFlow<PageTextEvent>()
    val events: SharedFlow<PageTextEvent> = _events.asSharedFlow()
    private var recognitionJob: Job? = null

    val uiState: StateFlow<PageTextUiState> = combine(
        observeDocumentUseCase(documentId),
        observeDocumentPagesUseCase(documentId),
        observeDocumentTextsUseCase(documentId),
        recognition,
        query,
    ) { document, pages, texts, status, currentQuery ->
        val textsByPage = texts.associateBy { it.pageId }
        PageTextUiState(
            document = document,
            pages = pages.map { page -> PageTextItem(page, textsByPage[page.id]) },
            recognition = status,
            query = currentQuery,
            initialPageIndex = initialPage,
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PageTextUiState(initialPageIndex = initialPage))

    init {
        // Read anything not yet indexed right away, rather than waiting for the background worker.
        viewModelScope.launch {
            val state = uiState.first { !it.isLoading }
            if (state.pages.any { it.text == null || it.isStale }) readDocument(force = false)
        }
    }

    fun readDocument(force: Boolean) {
        if (recognitionJob?.isActive == true) return
        recognitionJob = viewModelScope.launch {
            recognition.value = RecognitionStatus.Running(0, 0)
            val result = recognizeDocumentTextUseCase(documentId, force) { done, total ->
                recognition.value = RecognitionStatus.Running(done, total)
            }
            recognition.value = when (result) {
                is ScanlyResult.Success -> RecognitionStatus.Idle
                is ScanlyResult.Failure -> RecognitionStatus.Failed(result.error.message)
            }
        }
    }

    fun readPage(pageId: String) {
        if (recognitionJob?.isActive == true) return
        recognitionJob = viewModelScope.launch {
            recognition.value = RecognitionStatus.Running(0, 1)
            when (val result = recognizePageTextUseCase(pageId, force = true)) {
                is ScanlyResult.Success -> {
                    recognition.value = RecognitionStatus.Idle
                    _events.emit(PageTextEvent.ShowMessage("Page text read again"))
                }
                is ScanlyResult.Failure -> {
                    recognition.value = RecognitionStatus.Idle
                    _events.emit(PageTextEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun saveEdit(pageId: String, text: String) {
        viewModelScope.launch {
            when (val result = updatePageTextUseCase(pageId, text.trim())) {
                is ScanlyResult.Success -> _events.emit(PageTextEvent.ShowMessage("Changes saved. Search uses your edits."))
                is ScanlyResult.Failure -> _events.emit(PageTextEvent.ShowMessage(result.error.message))
            }
        }
    }

    fun restoreRecognized(pageId: String) {
        viewModelScope.launch {
            when (val result = updatePageTextUseCase(pageId, null)) {
                is ScanlyResult.Success -> _events.emit(PageTextEvent.ShowMessage("Original text restored"))
                is ScanlyResult.Failure -> _events.emit(PageTextEvent.ShowMessage(result.error.message))
            }
        }
    }

    /** Copying or sharing extracted text is a success worth counting toward the rating nudge. */
    fun onTextUsed() {
        viewModelScope.launch { recordPositiveMomentUseCase() }
    }

    fun clearQuery() {
        query.value = ""
    }
}
