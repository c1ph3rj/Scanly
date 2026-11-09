package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.PdfWorkingCopy
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.ClosePdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.OpenPdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val READER_PAGE_MAX_WIDTH = 1600
private const val READER_THUMBNAIL_MAX_WIDTH = 240
private const val READER_THUMBNAIL_CACHE_LIMIT = 30

enum class PdfReaderPhase {
    Empty,
    Opening,
    Locked,
    Ready,
}

enum class PdfReaderLayout {
    Paged,
    Continuous,
}

data class PdfReaderUiState(
    val phase: PdfReaderPhase = PdfReaderPhase.Empty,
    val source: PdfToolSource? = null,
    val documentTitle: String = "",
    val pageCount: Int = 0,
    val currentPageIndex: Int = 0,
    val pageBitmaps: Map<Int, Bitmap> = emptyMap(),
    val loadingPages: Set<Int> = emptySet(),
    /** Small grid thumbnails for the Pages sheet. Separate from [pageBitmaps]. */
    val thumbnails: Map<Int, Bitmap> = emptyMap(),
    val loadingThumbnails: Set<Int> = emptySet(),
    val passwordDraft: String = "",
    val passwordError: String? = null,
    val chromeVisible: Boolean = true,
    val readerLayout: PdfReaderLayout = PdfReaderLayout.Paged,
    val libraryDocuments: List<ScanDocument> = emptyList(),
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val openPdfWorkingCopyUseCase: OpenPdfWorkingCopyUseCase,
    private val closePdfWorkingCopyUseCase: ClosePdfWorkingCopyUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PdfToolEvent>()
    val events: SharedFlow<PdfToolEvent> = _events.asSharedFlow()

    /** Bumped whenever a new document opens, so renders started for an old document are dropped. */
    private var documentGeneration = 0

    /** Access-ordered, so the least recently requested thumbnail is evicted first. Main thread only. */
    private val thumbnailCache = LinkedHashMap<Int, Bitmap>(16, 0.75f, true)
    private val pendingThumbnails = HashSet<Int>()

    /**
     * Decrypted local copy of the open PDF. Every page and thumbnail renders from it, so the
     * source is copied, exported or decrypted once per document instead of once per page.
     */
    private var workingCopy: PdfWorkingCopy? = null

    init {
        viewModelScope.launch {
            observeDocumentsUseCase().collect { docs ->
                _uiState.update { it.copy(libraryDocuments = docs) }
            }
        }
    }

    fun onSourcesChosen(sources: List<PdfToolSource>) {
        val source = sources.firstOrNull() ?: return
        releaseAll()
        _uiState.update {
            it.copy(
                source = source,
                documentTitle = source.label(),
                phase = PdfReaderPhase.Opening,
                pageCount = 0,
                currentPageIndex = 0,
                pageBitmaps = emptyMap(),
                loadingPages = emptySet(),
                thumbnails = emptyMap(),
                loadingThumbnails = emptySet(),
                // A new document never inherits the previous one's password.
                passwordDraft = "",
                passwordError = null,
                chromeVisible = true,
            )
        }
        openSource()
    }

    fun setPasswordDraft(value: String) {
        _uiState.update { it.copy(passwordDraft = value, passwordError = null) }
    }

    fun unlock() = openSource()

    fun goToPage(index: Int) {
        val count = _uiState.value.pageCount
        if (count <= 0) return
        val target = clampPdfReaderPageIndex(index, count)
        _uiState.update { it.copy(currentPageIndex = target) }
        ensurePagesAround(target)
    }

    fun toggleChrome() {
        _uiState.update { it.copy(chromeVisible = togglePdfReaderChrome(it.chromeVisible)) }
    }

    fun toggleReaderLayout() {
        _uiState.update {
            it.copy(readerLayout = togglePdfReaderLayout(it.readerLayout))
        }
    }

    /** Renders the page thumbnail the first time it is asked for; cached thumbnails are just touched. */
    fun requestThumbnail(index: Int) {
        val state = _uiState.value
        if (index !in 0 until state.pageCount) return
        // Reading the access-ordered cache also marks the entry as most recently used.
        if (thumbnailCache[index] != null || index in pendingThumbnails) return
        val copy = workingCopy ?: return
        pendingThumbnails += index
        val generation = documentGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(loadingThumbnails = it.loadingThumbnails + index) }
            val result = renderPdfPageUseCase(
                source = copy.file,
                pageIndex = index,
                maxWidth = READER_THUMBNAIL_MAX_WIDTH,
            )
            if (generation != documentGeneration) {
                (result as? ScanlyResult.Success<Bitmap>)?.value?.recycle()
                return@launch
            }
            pendingThumbnails -= index
            when (result) {
                is ScanlyResult.Success -> cacheThumbnail(index, result.value)
                is ScanlyResult.Failure -> _uiState.update {
                    it.copy(loadingThumbnails = it.loadingThumbnails - index)
                }
            }
        }
    }

    private fun openSource() {
        val source = _uiState.value.source ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = PdfReaderPhase.Opening, passwordError = null)
            }
            val password = _uiState.value.passwordDraft.ifBlank { null }
            when (val result = openPdfWorkingCopyUseCase(source, password)) {
                is ScanlyResult.Success -> {
                    if (_uiState.value.source != source) {
                        // Another file was chosen while this one was opening.
                        closeQuietly(result.value)
                        return@launch
                    }
                    releaseAll()
                    workingCopy = result.value
                    _uiState.update {
                        it.copy(
                            phase = PdfReaderPhase.Ready,
                            pageCount = result.value.pageCount,
                            documentTitle = source.label(),
                            currentPageIndex = 0,
                            pageBitmaps = emptyMap(),
                            loadingPages = emptySet(),
                            thumbnails = emptyMap(),
                            loadingThumbnails = emptySet(),
                        )
                    }
                    ensurePagesAround(0)
                }
                is ScanlyResult.Failure -> {
                    // A late failure for a file the user already replaced must not touch the new one.
                    if (_uiState.value.source != source) return@launch
                    val needsPw = result.error.message.contains("password", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            phase = if (needsPw) PdfReaderPhase.Locked else PdfReaderPhase.Empty,
                            // The first "it's locked" is shown by the lock screen itself; only a
                            // password the user actually typed can be reported as wrong.
                            passwordError = if (needsPw && password != null) result.error.message else null,
                        )
                    }
                    if (!needsPw) {
                        _events.emit(PdfToolEvent.ShowMessage(result.error.message))
                    }
                }
            }
        }
    }

    private fun ensurePagesAround(center: Int) {
        val count = _uiState.value.pageCount
        if (count <= 0) return
        // Continuous mode reports the first visible page as [center], and short or landscape pages
        // can put several pages below it on screen, so keep more pages ahead than behind.
        val range = if (_uiState.value.readerLayout == PdfReaderLayout.Continuous) {
            (center - 1)..(center + 4)
        } else {
            (center - 1)..(center + 1)
        }.filter { it in 0 until count }
        range.forEach { renderIfNeeded(it) }
        // Evict far pages. Never recycle() them here: Compose may still draw the previous state
        // for a frame, and drawing a recycled bitmap crashes. Dropping the reference is enough.
        val keep = range.toSet()
        _uiState.update { state ->
            state.copy(pageBitmaps = state.pageBitmaps.filterKeys { it in keep })
        }
    }

    private fun renderIfNeeded(index: Int) {
        val state = _uiState.value
        if (state.pageBitmaps.containsKey(index) || index in state.loadingPages) return
        val copy = workingCopy ?: return
        val generation = documentGeneration
        viewModelScope.launch {
            _uiState.update { it.copy(loadingPages = it.loadingPages + index) }
            when (
                val result = renderPdfPageUseCase(
                    source = copy.file,
                    pageIndex = index,
                    maxWidth = READER_PAGE_MAX_WIDTH,
                )
            ) {
                is ScanlyResult.Success -> {
                    if (generation != documentGeneration) {
                        result.value.recycle()
                        return@launch
                    }
                    _uiState.update {
                        it.copy(
                            pageBitmaps = it.pageBitmaps + (index to result.value),
                            loadingPages = it.loadingPages - index,
                        )
                    }
                }
                is ScanlyResult.Failure -> {
                    if (generation != documentGeneration) return@launch
                    _uiState.update { it.copy(loadingPages = it.loadingPages - index) }
                    _events.emit(PdfToolEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    private fun cacheThumbnail(index: Int, bitmap: Bitmap) {
        // Evicted thumbnails may still be on screen this frame, so they are released, not recycled.
        thumbnailCache[index] = bitmap
        while (thumbnailCache.size > READER_THUMBNAIL_CACHE_LIMIT) {
            thumbnailCache.remove(thumbnailCache.keys.first())
        }
        _uiState.update {
            it.copy(
                thumbnails = thumbnailCache.toMap(),
                loadingThumbnails = it.loadingThumbnails - index,
            )
        }
    }

    /**
     * Drops every page and thumbnail of the current document. Bitmaps are left to the GC rather
     * than recycled, because the UI may still be drawing them until the new state arrives.
     */
    private fun releaseAll() {
        documentGeneration++
        thumbnailCache.clear()
        pendingThumbnails.clear()
        workingCopy?.let(::closeQuietly)
        workingCopy = null
    }

    private fun closeQuietly(copy: PdfWorkingCopy) {
        CoroutineScope(Dispatchers.IO + NonCancellable).launch { closePdfWorkingCopyUseCase(copy) }
    }

    override fun onCleared() {
        releaseAll()
        super.onCleared()
    }
}
