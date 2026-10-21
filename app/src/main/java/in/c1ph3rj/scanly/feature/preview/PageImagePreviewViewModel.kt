package `in`.c1ph3rj.scanly.feature.preview

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.usecase.MovePageToTrashUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObservePageUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PageImagePreviewUiState(
    val pages: List<ScanPage> = emptyList(),
    val selectedPageId: String? = null,
    val isLoading: Boolean = true,
    val missingPage: Boolean = false,
    val isDeleting: Boolean = false,
) {
    val page: ScanPage?
        get() = pages.firstOrNull { it.id == selectedPageId }
}

sealed interface PageImagePreviewEvent {
    data class ShowMessage(val message: String) : PageImagePreviewEvent
    data class ShareFiles(val artifact: ShareArtifact) : PageImagePreviewEvent
    data class PageDeleted(val wasLastPage: Boolean) : PageImagePreviewEvent
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
    private val movePageToTrashUseCase: MovePageToTrashUseCase,
) : ViewModel() {
    private val pageId: String = checkNotNull(savedStateHandle[PageImagePreviewDestination.pageIdArgument])

    private val _uiState = MutableStateFlow(PageImagePreviewUiState())
    val uiState: StateFlow<PageImagePreviewUiState> = _uiState.asStateFlow()

    // Buffered so events sent while the screen re-subscribes (rotation) are not lost.
    private val _events = Channel<PageImagePreviewEvent>(Channel.BUFFERED)
    val events: Flow<PageImagePreviewEvent> = _events.receiveAsFlow()

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
                _uiState.update { currentState ->
                    val selectedPageId = resolvePreviewPageId(
                        currentSelectedPageId = currentState.selectedPageId,
                        openedPageId = pageId,
                        pages = orderedPages,
                    )
                    PageImagePreviewUiState(
                        pages = orderedPages,
                        selectedPageId = selectedPageId,
                        isLoading = false,
                        missingPage = selectedPageId == null,
                        isDeleting = currentState.isDeleting,
                    )
                }
            }
        }
    }

    fun selectPage(pageId: String) {
        _uiState.update { currentState ->
            if (currentState.pages.none { it.id == pageId }) {
                currentState
            } else {
                currentState.copy(selectedPageId = pageId)
            }
        }
    }

    fun sharePage(pageId: String) {
        val page = _uiState.value.pages.firstOrNull { it.id == pageId } ?: return
        val imagePath = page.processedImagePath ?: page.rawImagePath ?: page.thumbnailPath

        viewModelScope.launch {
            if (imagePath == null) {
                _events.send(PageImagePreviewEvent.ShowMessage("Page image is not available yet."))
                return@launch
            }

            _events.send(
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

    fun deletePage(pageId: String) {
        val state = _uiState.value
        val deleteIndex = state.pages.indexOfFirst { it.id == pageId }
        val page = state.pages.getOrNull(deleteIndex) ?: return
        if (state.isDeleting) return
        val fallbackPageId = resolvePageIdAfterDeletion(pageId, state.pages)

        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true) }
            when (val result = movePageToTrashUseCase(page.id, page.pageIndex + 1)) {
                is ScanlyResult.Success -> {
                    _uiState.update {
                        it.copy(
                            selectedPageId = fallbackPageId,
                            isDeleting = false,
                        )
                    }
                    // The app-wide snackbar says where it went and offers Undo.
                    _events.send(PageImagePreviewEvent.PageDeleted(wasLastPage = fallbackPageId == null))
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isDeleting = false) }
                    _events.send(PageImagePreviewEvent.ShowMessage(result.error.message))
                }
            }
        }
    }
}

private const val PageImageMimeType = "image/*"

internal fun resolvePreviewPageId(
    currentSelectedPageId: String?,
    openedPageId: String,
    pages: List<ScanPage>,
): String? = when {
    pages.any { it.id == currentSelectedPageId } -> currentSelectedPageId
    pages.any { it.id == openedPageId } -> openedPageId
    else -> pages.firstOrNull()?.id
}

internal fun resolvePageIdAfterDeletion(
    deletedPageId: String,
    pages: List<ScanPage>,
): String? {
    val deletedIndex = pages.indexOfFirst { it.id == deletedPageId }
    if (deletedIndex < 0) return null
    return pages.getOrNull(deletedIndex + 1)?.id
        ?: pages.getOrNull(deletedIndex - 1)?.id
}
