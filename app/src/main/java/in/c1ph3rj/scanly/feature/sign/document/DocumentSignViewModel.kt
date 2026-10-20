package `in`.c1ph3rj.scanly.feature.sign.document

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.PageBitmapLoader
import `in`.c1ph3rj.scanly.domain.model.PageMarkup
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.usecase.LoadDocumentMarkupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveSignaturesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveDocumentMarkupsUseCase
import `in`.c1ph3rj.scanly.feature.document.DocumentDestination
import `in`.c1ph3rj.scanly.feature.sign.editor.SignEditorAction
import `in`.c1ph3rj.scanly.feature.sign.editor.SignEditorController
import `in`.c1ph3rj.scanly.feature.sign.editor.SignEditorPage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.math.abs

/** One-shot events for the document sign screen. */
sealed interface DocumentSignEvent {
    /** A short message to show in a snackbar. */
    data class Message(val text: String) : DocumentSignEvent

    /** The markups were saved; the screen should leave. */
    data object Saved : DocumentSignEvent
}

/** Screen-level state. The editor's own state is exposed through [DocumentSignViewModel.editor]. */
data class DocumentSignUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
)

/**
 * Signs a library document in place. Each page's clean image is the background, existing marks
 * are restored from the saved markups, and saving stores the marks per page so they stay editable.
 */
@HiltViewModel
class DocumentSignViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observeDocumentPagesUseCase: ObserveDocumentPagesUseCase,
    observeSignaturesUseCase: ObserveSignaturesUseCase,
    private val loadDocumentMarkupsUseCase: LoadDocumentMarkupsUseCase,
    private val saveDocumentMarkupsUseCase: SaveDocumentMarkupsUseCase,
    private val pageBitmapLoader: PageBitmapLoader,
) : ViewModel() {
    private val documentId: String =
        checkNotNull(savedStateHandle.get<String>(DocumentDestination.documentIdArgument))

    /** The page to open on; the key may arrive as either a String or an Int. */
    private val initialPage: Int = when (val raw = savedStateHandle.get<Any>(PageArgument)) {
        is Int -> raw
        is String -> raw.toIntOrNull() ?: 0
        else -> 0
    }

    /** Editor state and actions for the shared sign editor. */
    val editor = SignEditorController()

    private val _uiState = MutableStateFlow(DocumentSignUiState())
    val uiState: StateFlow<DocumentSignUiState> = _uiState.asStateFlow()

    private val _pageBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** Decoded page images keyed by page position; only pages near the current page are kept. */
    val pageBitmaps: StateFlow<Map<Int, Bitmap>> = _pageBitmaps.asStateFlow()

    private val _events = Channel<DocumentSignEvent>(Channel.BUFFERED)
    val events: Flow<DocumentSignEvent> = _events.receiveAsFlow()

    /** Page IDs by position, matching the editor's page indices. */
    private var pageIds: List<String> = emptyList()

    /** Background image path by position; null when the page is still processing. */
    private var backgroundPaths: List<String?> = emptyList()

    /** False when existing markups could not be loaded; saving is refused so nothing is lost. */
    private var markupsLoaded = false

    private val inFlightPages = mutableSetOf<Int>()

    init {
        viewModelScope.launch {
            observeSignaturesUseCase().collect { signatures -> editor.setSignatures(signatures) }
        }
        viewModelScope.launch { loadDocument() }
    }

    /** Forwards an editor intent and drops decoded pages that are no longer near the current page. */
    fun onEditorAction(action: SignEditorAction) {
        editor.onAction(action)
        if (action is SignEditorAction.ChangePage) {
            evictDistantPages()
        }
    }

    /** Decodes the page at [index] in the background, unless it is already loaded or loading. */
    fun requestPage(index: Int) {
        val path = backgroundPaths.getOrNull(index) ?: return
        if (index in _pageBitmaps.value || index in inFlightPages) return
        inFlightPages += index
        viewModelScope.launch {
            val bitmap = pageBitmapLoader.load(path, PageBitmapMaxEdge)
            inFlightPages -= index
            if (bitmap != null) {
                addPageBitmap(index, bitmap)
            }
        }
    }

    /** Stamps and saves the marks for every page. Pages without marks are saved empty so removals stick. */
    fun save() {
        if (_uiState.value.isLoading || _uiState.value.isSaving) return
        viewModelScope.launch {
            if (!markupsLoaded) {
                _events.send(
                    DocumentSignEvent.Message(
                        "Saved signatures couldn't be loaded, so this document can't be signed right now.",
                    ),
                )
                return@launch
            }
            _uiState.update { it.copy(isSaving = true) }
            try {
                when (val result = saveDocumentMarkupsUseCase(documentId, buildMarksByPageId())) {
                    is ScanlyResult.Success -> _events.send(DocumentSignEvent.Saved)
                    is ScanlyResult.Failure -> _events.send(DocumentSignEvent.Message(result.error.message))
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    override fun onCleared() {
        _pageBitmaps.value = emptyMap()
        super.onCleared()
    }

    private suspend fun loadDocument() {
        val pages = observeDocumentPagesUseCase(documentId).first().sortedBy { it.pageIndex }

        val markupsByPageId: Map<String, PageMarkup> =
            when (val result = loadDocumentMarkupsUseCase(documentId)) {
                is ScanlyResult.Success -> {
                    markupsLoaded = true
                    result.value
                }

                is ScanlyResult.Failure -> {
                    _events.send(DocumentSignEvent.Message(result.error.message))
                    emptyMap()
                }
            }

        var hasUnsignablePage = false
        val backgrounds = pages.map { page ->
            val background = markupsByPageId[page.id]?.baseImagePath ?: page.processedImagePath
            if (background == null) hasUnsignablePage = true
            background
        }

        val editorPages = backgrounds.mapIndexed { position, path ->
            SignEditorPage(
                index = position,
                aspectRatio = path?.let { pageBitmapLoader.aspectRatio(it) } ?: FallbackPageAspect,
            )
        }
        val marks = pages.flatMapIndexed { position, page ->
            markupsByPageId[page.id]?.marks.orEmpty().map { mark -> mark.onPage(position) }
        }

        pageIds = pages.map { it.id }
        backgroundPaths = backgrounds
        editor.setPages(editorPages)
        editor.setMarks(marks)
        editor.onAction(SignEditorAction.ChangePage(initialPage))

        if (hasUnsignablePage) {
            _events.send(DocumentSignEvent.Message(UnprocessedPagesMessage))
        }
        _uiState.update { it.copy(isLoading = false) }
    }

    private fun buildMarksByPageId(): Map<String, List<SignMark>> {
        val marksByPosition = editor.marks().groupBy { it.pageIndex }
        return pageIds.withIndex().associate { (position, pageId) ->
            val signable = backgroundPaths.getOrNull(position) != null
            pageId to if (signable) marksByPosition[position].orEmpty() else emptyList()
        }
    }

    private fun addPageBitmap(index: Int, bitmap: Bitmap) {
        val center = editor.state.value.currentPage
        if (index in _pageBitmaps.value || abs(index - center) > KeptPageRadius) {
            bitmap.recycle()
            return
        }
        _pageBitmaps.update { current -> current + (index to bitmap) }
        evictDistantPages()
    }

    /**
     * Drops decoded pages farther than [KeptPageRadius] from the current page. They are not
     * recycled: the UI may still draw them for a frame, and drawing a recycled bitmap crashes.
     */
    private fun evictDistantPages() {
        val center = editor.state.value.currentPage
        val (kept, evicted) = _pageBitmaps.value.entries.partition { entry ->
            abs(entry.key - center) <= KeptPageRadius
        }
        if (evicted.isEmpty()) return
        _pageBitmaps.value = kept.associate { entry -> entry.key to entry.value }
    }

    private companion object {
        const val PageArgument = "page"
        const val PageBitmapMaxEdge = 1400
        const val KeptPageRadius = 2
        const val FallbackPageAspect = 0.707f
        const val UnprocessedPagesMessage = "Some pages are still processing and can't be signed yet."
    }
}

/** Re-keys a mark to a page position after the page order is known. */
private fun SignMark.onPage(position: Int): SignMark = when (this) {
    is SignMark.Image -> copy(pageIndex = position)
    is SignMark.Text -> copy(pageIndex = position)
    is SignMark.Date -> copy(pageIndex = position)
    is SignMark.Checkbox -> copy(pageIndex = position)
}
