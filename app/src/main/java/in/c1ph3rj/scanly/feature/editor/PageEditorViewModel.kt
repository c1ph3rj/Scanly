package `in`.c1ph3rj.scanly.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.usecase.MovePageToTrashUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObservePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.UpdatePageEditsUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PageEditorUiState(
    val page: ScanPage? = null,
    val cropQuad: DocumentCornerQuad? = null,
    val selectedFilter: PageFilterPreset = PageFilterPreset.AUTO,
    val filterAdjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
    val applyFilterToAllPages: Boolean = false,
    val rotationDegrees: Int = 0,
    val isSaving: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val missingPage: Boolean = false,
)

sealed interface PageEditorEvent {
    data class ShowMessage(val message: String) : PageEditorEvent
    data object Saved : PageEditorEvent
    data object PageDeleted : PageEditorEvent
}

object PageEditorDestination {
    const val pageIdArgument = "pageId"
    const val routePattern = "editor/page/{$pageIdArgument}"

    fun route(pageId: String): String = "editor/page/$pageId"
}

@HiltViewModel
class PageEditorViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observePageUseCase: ObservePageUseCase,
    private val updatePageEditsUseCase: UpdatePageEditsUseCase,
    private val movePageToTrashUseCase: MovePageToTrashUseCase,
) : ViewModel() {
    private val pageId: String = checkNotNull(savedStateHandle[PageEditorDestination.pageIdArgument])

    private val _uiState = MutableStateFlow(PageEditorUiState())
    val uiState: StateFlow<PageEditorUiState> = _uiState.asStateFlow()

    // Buffered so events sent while the screen re-subscribes (rotation) are not lost.
    private val _events = Channel<PageEditorEvent>(Channel.BUFFERED)
    val events: Flow<PageEditorEvent> = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            observePageUseCase(pageId).collectLatest { page ->
                _uiState.update { current ->
                    if (page == null) {
                        current.copy(
                            page = null,
                            missingPage = true,
                        )
                    } else if (current.page?.id != page.id || !current.hasUnsavedChanges) {
                        // Pages without a stored crop show the full frame; an inset default would silently crop them on Done.
                        val baseQuad = page.cropQuad ?: DocumentCornerQuad.FullFrame
                        current.copy(
                            page = page,
                            cropQuad = baseQuad,
                            selectedFilter = page.filterPreset,
                            filterAdjustments = page.filterAdjustments,
                            applyFilterToAllPages = false,
                            rotationDegrees = resolveInitialRotation(page),
                            missingPage = false,
                            hasUnsavedChanges = false,
                            isSaving = false,
                        )
                    } else {
                        // Keep local filter/adjustment drafts; adopt crop/rotation from crop screen.
                        current.copy(
                            page = page,
                            cropQuad = page.cropQuad ?: DocumentCornerQuad.FullFrame,
                            rotationDegrees = resolveInitialRotation(page),
                            missingPage = false,
                        )
                    }
                }
            }
        }
    }

    fun selectFilter(filterPreset: PageFilterPreset) {
        _uiState.update { current ->
            current.copy(
                selectedFilter = filterPreset,
                hasUnsavedChanges = true,
            )
        }
    }

    fun setApplyFilterToAllPages(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                applyFilterToAllPages = enabled,
            )
        }
    }

    fun updateFilterAdjustments(adjustments: PageFilterAdjustments) {
        _uiState.update { current ->
            current.copy(
                filterAdjustments = adjustments.sanitized(),
                hasUnsavedChanges = true,
            )
        }
    }

    fun resetFilterAdjustments() {
        _uiState.update { current ->
            current.copy(
                filterAdjustments = PageFilterAdjustments.Default,
                hasUnsavedChanges = true,
            )
        }
    }

    fun saveEdits() {
        val snapshot = _uiState.value
        val page = snapshot.page ?: return
        val cropQuad = snapshot.cropQuad ?: return
        if (snapshot.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (
                val result = updatePageEditsUseCase(
                    pageId = page.id,
                    cropQuad = cropQuad,
                    rotationDegrees = snapshot.rotationDegrees,
                    filterPreset = snapshot.selectedFilter,
                    filterAdjustments = snapshot.filterAdjustments,
                    applyFilterToAllPages = snapshot.applyFilterToAllPages,
                )
            ) {
                is ScanlyResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                            applyFilterToAllPages = false,
                        )
                    }
                    _events.send(
                        PageEditorEvent.ShowMessage(
                            if (snapshot.applyFilterToAllPages) {
                                "Filter applied to all pages."
                            } else {
                                "Page updated."
                            },
                        ),
                    )
                    _events.send(PageEditorEvent.Saved)
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(PageEditorEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun deletePage() {
        val page = _uiState.value.page ?: return
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = movePageToTrashUseCase(page.id, page.pageIndex + 1)) {
                is ScanlyResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    // The app-wide snackbar says where it went and offers Undo.
                    _events.send(PageEditorEvent.PageDeleted)
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.send(PageEditorEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    private fun resolveInitialRotation(page: ScanPage): Int {
        val normalizedRotation = normalizeEditorRotation(page.rotationDegrees)
        return if (page.processingState == PageProcessingState.CAPTURED && normalizedRotation % 180 != 0) {
            0
        } else {
            normalizedRotation
        }
    }
}
