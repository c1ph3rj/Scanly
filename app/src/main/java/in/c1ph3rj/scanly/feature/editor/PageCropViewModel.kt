package `in`.c1ph3rj.scanly.feature.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.editing.CropHandle
import `in`.c1ph3rj.scanly.core.editing.CropQuadEditor
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.PageProcessingState
import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.usecase.DetectDocumentCornersUseCase
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

data class PageCropUiState(
    val page: ScanPage? = null,
    val cropQuad: DocumentCornerQuad? = null,
    val referenceCropQuad: DocumentCornerQuad? = null,
    val rotationDegrees: Int = 0,
    val selectedFilter: PageFilterPreset = PageFilterPreset.ORIGINAL,
    val isSaving: Boolean = false,
    val isDetecting: Boolean = false,
    val hasUnsavedChanges: Boolean = false,
    val missingPage: Boolean = false,
)

sealed interface PageCropEvent {
    data class ShowMessage(val message: String) : PageCropEvent
    data object Saved : PageCropEvent
}

object PageCropDestination {
    const val pageIdArgument = "pageId"
    const val routePattern = "crop/page/{$pageIdArgument}"

    fun route(pageId: String): String = "crop/page/$pageId"
}

@HiltViewModel
class PageCropViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val observePageUseCase: ObservePageUseCase,
    private val updatePageEditsUseCase: UpdatePageEditsUseCase,
    private val detectDocumentCornersUseCase: DetectDocumentCornersUseCase,
) : ViewModel() {
    private val pageId: String = checkNotNull(savedStateHandle[PageCropDestination.pageIdArgument])

    private val _uiState = MutableStateFlow(PageCropUiState())
    val uiState: StateFlow<PageCropUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PageCropEvent>()
    val events: SharedFlow<PageCropEvent> = _events.asSharedFlow()

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
                            referenceCropQuad = baseQuad,
                            rotationDegrees = resolveInitialRotation(page),
                            selectedFilter = page.filterPreset,
                            missingPage = false,
                            hasUnsavedChanges = false,
                            isSaving = false,
                            isDetecting = false,
                        )
                    } else {
                        current.copy(page = page, missingPage = false)
                    }
                }
            }
        }
    }

    fun moveHandle(
        handle: CropHandle,
        point: NormalizedPoint,
    ) {
        _uiState.update { current ->
            val currentQuad = current.cropQuad ?: return@update current
            current.copy(
                cropQuad = CropQuadEditor.moveHandle(currentQuad, handle, point),
                hasUnsavedChanges = true,
            )
        }
    }

    fun resetCrop() {
        _uiState.update { current ->
            val reference = current.referenceCropQuad ?: return@update current
            current.copy(
                cropQuad = reference,
                hasUnsavedChanges = true,
            )
        }
    }

    fun rotateLeft() {
        rotate { CropQuadEditor.rotateCounterClockwise(it) to -90 }
    }

    fun rotateRight() {
        rotate { CropQuadEditor.rotateClockwise(it) to 90 }
    }

    fun detectDocument() {
        val snapshot = _uiState.value
        val page = snapshot.page ?: return
        val rawImagePath = page.rawImagePath
        if (rawImagePath.isNullOrBlank()) {
            viewModelScope.launch {
                _events.emit(PageCropEvent.ShowMessage("Raw image is not available for detection."))
            }
            return
        }
        if (snapshot.isSaving || snapshot.isDetecting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isDetecting = true) }
            when (
                val result = detectDocumentCornersUseCase(
                    rawImagePath = rawImagePath,
                    rotationDegrees = snapshot.rotationDegrees,
                )
            ) {
                is ScanlyResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            cropQuad = result.value,
                            isDetecting = false,
                            hasUnsavedChanges = true,
                        )
                    }
                    _events.emit(PageCropEvent.ShowMessage("Document detected."))
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isDetecting = false) }
                    _events.emit(PageCropEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun applyCrop() {
        val snapshot = _uiState.value
        val page = snapshot.page ?: return
        val cropQuad = snapshot.cropQuad ?: return
        if (snapshot.isSaving || snapshot.isDetecting) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (
                val result = updatePageEditsUseCase(
                    pageId = page.id,
                    cropQuad = cropQuad,
                    rotationDegrees = snapshot.rotationDegrees,
                    filterPreset = snapshot.selectedFilter,
                    filterAdjustments = page.filterAdjustments,
                    applyFilterToAllPages = false,
                )
            ) {
                is ScanlyResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            hasUnsavedChanges = false,
                        )
                    }
                    _events.emit(PageCropEvent.ShowMessage("Crop updated."))
                    _events.emit(PageCropEvent.Saved)
                }

                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isSaving = false) }
                    _events.emit(PageCropEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    private fun rotate(transform: (DocumentCornerQuad) -> Pair<DocumentCornerQuad, Int>) {
        _uiState.update { current ->
            if (current.isDetecting || current.isSaving) return@update current
            val currentQuad = current.cropQuad ?: return@update current
            val referenceQuad = current.referenceCropQuad ?: currentQuad
            val (rotatedCurrentQuad, rotationDelta) = transform(currentQuad)
            val (rotatedReferenceQuad, _) = transform(referenceQuad)
            current.copy(
                cropQuad = rotatedCurrentQuad,
                referenceCropQuad = rotatedReferenceQuad,
                rotationDegrees = normalizeEditorRotation(current.rotationDegrees + rotationDelta),
                hasUnsavedChanges = true,
            )
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
