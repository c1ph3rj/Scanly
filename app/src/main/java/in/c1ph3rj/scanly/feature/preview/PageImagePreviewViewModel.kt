package `in`.c1ph3rj.scanly.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.RecognizedPageText
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObservePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecognizePageTextUseCase
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PageImagePreviewUiState(
    val pages: List<ScanPage> = emptyList(),
    val selectedPageId: String? = null,
    val isLoading: Boolean = true,
    val missingPage: Boolean = false,
    val textMode: PageTextModeState = PageTextModeState.Inactive,
) {
    val page: ScanPage?
        get() = pages.firstOrNull { it.id == selectedPageId }
}

sealed interface PageTextModeState {
    data object Inactive : PageTextModeState

    data class Loading(
        val cacheKey: PageOcrCacheKey,
    ) : PageTextModeState

    data class Ready(
        val cacheKey: PageOcrCacheKey,
        val recognizedText: RecognizedPageText,
        val selection: IntRange? = null,
    ) : PageTextModeState

    data class Empty(
        val cacheKey: PageOcrCacheKey,
    ) : PageTextModeState

    data class Error(
        val cacheKey: PageOcrCacheKey,
        val message: String,
    ) : PageTextModeState
}

data class PageOcrCacheKey(
    val pageId: String,
    val imagePath: String,
    val updatedAtMillis: Long,
)

sealed interface PageImagePreviewEvent {
    data class ShowMessage(val message: String) : PageImagePreviewEvent
    data class ShareFiles(val artifact: ShareArtifact) : PageImagePreviewEvent
    data class CopyText(val text: String) : PageImagePreviewEvent
}

object PageImagePreviewDestination {
    const val pageIdArgument = "pageId"
    const val routePattern = "preview/page/{$pageIdArgument}"

    fun route(pageId: String): String = "preview/page/$pageId"
}

@HiltViewModel
class PageImagePreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observePageUseCase: ObservePageUseCase,
    observeDocumentPagesUseCase: ObserveDocumentPagesUseCase,
    private val recognizePageTextUseCase: RecognizePageTextUseCase,
) : ViewModel() {
    private val pageId: String = checkNotNull(savedStateHandle[PageImagePreviewDestination.pageIdArgument])

    private val _uiState = MutableStateFlow(PageImagePreviewUiState())
    val uiState: StateFlow<PageImagePreviewUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PageImagePreviewEvent>()
    val events: SharedFlow<PageImagePreviewEvent> = _events.asSharedFlow()

    private val recognitionCache = mutableMapOf<PageOcrCacheKey, RecognizedPageText>()
    private var recognitionJob: Job? = null

    init {
        viewModelScope.launch {
            val openedPage = observePageUseCase(pageId).first()
            if (openedPage == null) {
                _uiState.update {
                    PageImagePreviewUiState(
                        isLoading = false,
                        missingPage = true,
                    )
                }
                return@launch
            }

            observeDocumentPagesUseCase(openedPage.documentId).collectLatest { documentPages ->
                val orderedPages = documentPages.sortedBy(ScanPage::pageIndex)
                val currentState = _uiState.value
                val selectedPageId = resolvePreviewPageId(
                    currentSelectedPageId = currentState.selectedPageId,
                    openedPageId = pageId,
                    pages = orderedPages,
                )
                val selectedPage = orderedPages.firstOrNull { it.id == selectedPageId }
                val nextCacheKey = selectedPage?.ocrCacheKeyOrNull()
                val preserveTextMode = currentState.textMode.cacheKeyOrNull() == nextCacheKey
                if (!preserveTextMode) {
                    recognitionJob?.cancel()
                }
                _uiState.value = PageImagePreviewUiState(
                    pages = orderedPages,
                    selectedPageId = selectedPageId,
                    isLoading = false,
                    missingPage = selectedPageId == null,
                    textMode = currentState.textMode.takeIf { preserveTextMode }
                        ?: PageTextModeState.Inactive,
                )
            }
        }
    }

    fun selectPage(pageId: String) {
        _uiState.update { currentState ->
            if (currentState.pages.none { it.id == pageId }) {
                currentState
            } else {
                if (currentState.selectedPageId != pageId) {
                    recognitionJob?.cancel()
                    currentState.copy(
                        selectedPageId = pageId,
                        textMode = PageTextModeState.Inactive,
                    )
                } else {
                    currentState
                }
            }
        }
    }

    fun toggleTextMode(pageId: String) {
        val currentState = _uiState.value
        if (currentState.selectedPageId == pageId && currentState.textMode !is PageTextModeState.Inactive) {
            exitTextMode()
            return
        }
        recognizePageText(pageId)
    }

    fun retryTextRecognition() {
        _uiState.value.selectedPageId?.let { pageId -> recognizePageText(pageId, forceRefresh = true) }
    }

    fun exitTextMode() {
        recognitionJob?.cancel()
        _uiState.update { state -> state.copy(textMode = PageTextModeState.Inactive) }
    }

    fun selectTextToken(tokenIndex: Int) {
        updateReadyTextMode { ready ->
            if (ready.recognizedText.tokens.none { it.index == tokenIndex }) ready
            else ready.copy(selection = tokenIndex..tokenIndex)
        }
    }

    fun selectTextRange(anchorIndex: Int, focusIndex: Int) {
        updateReadyTextMode { ready ->
            val validIndices = ready.recognizedText.tokens.indices
            if (anchorIndex !in validIndices || focusIndex !in validIndices) {
                ready
            } else {
                ready.copy(selection = minOf(anchorIndex, focusIndex)..maxOf(anchorIndex, focusIndex))
            }
        }
    }

    fun clearTextSelection() {
        updateReadyTextMode { ready -> ready.copy(selection = null) }
    }

    fun selectAllText() {
        updateReadyTextMode { ready ->
            val lastIndex = ready.recognizedText.tokens.lastIndex
            ready.copy(selection = if (lastIndex >= 0) 0..lastIndex else null)
        }
    }

    fun copySelectedText() {
        val ready = _uiState.value.textMode as? PageTextModeState.Ready ?: return
        val selection = ready.selection ?: return
        val text = ready.recognizedText.textForSelection(selection)
        if (text.isBlank()) return
        viewModelScope.launch {
            _events.emit(PageImagePreviewEvent.CopyText(text))
        }
    }

    fun sharePage(pageId: String) {
        val page = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return
        val imagePath = page.processedImagePath ?: page.rawImagePath ?: page.thumbnailPath

        viewModelScope.launch {
            if (imagePath == null) {
                _events.emit(PageImagePreviewEvent.ShowMessage("Page image is not available yet."))
                return@launch
            }

            _events.emit(
                PageImagePreviewEvent.ShareFiles(
                    ShareArtifact(
                        mimeType = PageImageMimeType,
                        title = "Scanly page ${page.pageIndex + 1}",
                        filePaths = listOf(imagePath),
                    ),
                ),
            )
        }
    }

    private fun recognizePageText(
        pageId: String,
        forceRefresh: Boolean = false,
    ) {
        val page = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return
        val cacheKey = page.ocrCacheKeyOrNull()
        if (cacheKey == null) {
            viewModelScope.launch {
                _events.emit(PageImagePreviewEvent.ShowMessage("Page image is not available yet."))
            }
            return
        }

        recognitionJob?.cancel()
        recognitionCache[cacheKey]?.takeUnless { forceRefresh }?.let { recognizedText ->
            _uiState.update { state ->
                state.copy(
                    selectedPageId = pageId,
                    textMode = recognizedText.toTextModeState(cacheKey),
                )
            }
            return
        }

        _uiState.update { state ->
            state.copy(
                selectedPageId = pageId,
                textMode = PageTextModeState.Loading(cacheKey),
            )
        }
        recognitionJob = viewModelScope.launch {
            when (val result = recognizePageTextUseCase(cacheKey.imagePath)) {
                is ScanlyResult.Success -> {
                    recognitionCache[cacheKey] = result.value
                    updateTextModeForKey(cacheKey, result.value.toTextModeState(cacheKey))
                }
                is ScanlyResult.Failure -> {
                    updateTextModeForKey(
                        cacheKey,
                        PageTextModeState.Error(
                            cacheKey = cacheKey,
                            message = result.error.message,
                        ),
                    )
                }
            }
        }
    }

    private fun updateTextModeForKey(
        cacheKey: PageOcrCacheKey,
        nextState: PageTextModeState,
    ) {
        _uiState.update { state ->
            if (state.textMode.cacheKeyOrNull() == cacheKey) state.copy(textMode = nextState)
            else state
        }
    }

    private fun updateReadyTextMode(transform: (PageTextModeState.Ready) -> PageTextModeState.Ready) {
        _uiState.update { state ->
            val ready = state.textMode as? PageTextModeState.Ready ?: return@update state
            state.copy(textMode = transform(ready))
        }
    }
}

private const val PageImageMimeType = "image/*"

private fun ScanPage.ocrCacheKeyOrNull(): PageOcrCacheKey? {
    val imagePath = processedImagePath ?: rawImagePath ?: thumbnailPath ?: return null
    return PageOcrCacheKey(
        pageId = id,
        imagePath = imagePath,
        updatedAtMillis = updatedAtMillis,
    )
}

private fun PageTextModeState.cacheKeyOrNull(): PageOcrCacheKey? = when (this) {
    PageTextModeState.Inactive -> null
    is PageTextModeState.Loading -> cacheKey
    is PageTextModeState.Ready -> cacheKey
    is PageTextModeState.Empty -> cacheKey
    is PageTextModeState.Error -> cacheKey
}

private fun RecognizedPageText.toTextModeState(cacheKey: PageOcrCacheKey): PageTextModeState =
    if (tokens.isEmpty()) PageTextModeState.Empty(cacheKey)
    else PageTextModeState.Ready(cacheKey, this)

internal fun resolvePreviewPageId(
    currentSelectedPageId: String?,
    openedPageId: String,
    pages: List<ScanPage>,
): String? = when {
    pages.any { it.id == currentSelectedPageId } -> currentSelectedPageId
    pages.any { it.id == openedPageId } -> openedPageId
    else -> pages.firstOrNull()?.id
}
