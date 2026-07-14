package `in`.c1ph3rj.scanly.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.editing.CropQuadEditor
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.usecase.DeletePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObservePageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.UpdatePageEditsUseCase
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
    private val deletePageUseCase: DeletePageUseCase,
) : ViewModel() {
    private val pageId: String = checkNotNull(savedStateHandle[PageEditorDestination.pageIdArgument])

    private val _uiState = MutableStateFlow(PageEditorUiState())
    val uiState: StateFlow<PageEditorUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PageEditorEvent>()
    val events: SharedFlow<PageEditorEvent> = _events.asSharedFlow()

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
                        val baseQuad = page.cropQuad ?: CropQuadEditor.defaultQuad()
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
                            cropQuad = page.cropQuad ?: CropQuadEditor.defaultQuad(),
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
                    _events.emit(
                        PageEditorEvent.ShowMessage(
                            if (snapshot.applyFilterToAllPages) {
                                "Filter applied to all pages."
                            } else {
                                "Page updated."
                            },
                        ),
                    )
                    _events.emit(PageEditorEvent.Saved)
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(PageEditorEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun deletePage() {
        val page = _uiState.value.page ?: return
        if (_uiState.value.isSaving) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val result = deletePageUseCase(page.id)) {
                is ScanlyResult.Success -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(PageEditorEvent.ShowMessage("Deleted page ${page.pageIndex + 1}."))
                    _events.emit(PageEditorEvent.PageDeleted)
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(PageEditorEvent.ShowMessage(result.error.message))
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
