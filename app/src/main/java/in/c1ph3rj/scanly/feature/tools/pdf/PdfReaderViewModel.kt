package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfCompressQuality
import `in`.c1ph3rj.scanly.domain.model.PdfDocumentInfo
import `in`.c1ph3rj.scanly.domain.model.PdfPasswordMode
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions
import `in`.c1ph3rj.scanly.domain.model.WatermarkOrientation
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.domain.model.WatermarkSize
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveExportArtifactUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.CompressPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.InspectPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.MergePdfsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RemovePdfPasswordUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderWatermarkPreviewUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.SetPdfPasswordUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.WatermarkPdfUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

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
    val passwordDraft: String = "",
    val passwordError: String? = null,
    val chromeVisible: Boolean = true,
    val readerLayout: PdfReaderLayout = PdfReaderLayout.Paged,
    val libraryDocuments: List<ScanDocument> = emptyList(),
)

@HiltViewModel
class PdfReaderViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val inspectPdfUseCase: InspectPdfUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfReaderUiState())
    val uiState: StateFlow<PdfReaderUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PdfToolEvent>()
    val events: SharedFlow<PdfToolEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            observeDocumentsUseCase().collect { docs ->
                _uiState.update { it.copy(libraryDocuments = docs) }
            }
        }
    }

    fun onSourcesChosen(sources: List<PdfToolSource>) {
        val source = sources.firstOrNull() ?: return
        recycleAll()
        _uiState.update {
            it.copy(
                source = source,
                documentTitle = source.label(),
                phase = PdfReaderPhase.Opening,
                pageCount = 0,
                currentPageIndex = 0,
                pageBitmaps = emptyMap(),
                loadingPages = emptySet(),
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
        val target = index.coerceIn(0, count - 1)
        _uiState.update { it.copy(currentPageIndex = target) }
        ensurePagesAround(target)
    }

    fun toggleChrome() {
        _uiState.update { it.copy(chromeVisible = !it.chromeVisible) }
    }

    fun toggleReaderLayout() {
        _uiState.update {
            it.copy(
                readerLayout = if (it.readerLayout == PdfReaderLayout.Paged) {
                    PdfReaderLayout.Continuous
                } else {
                    PdfReaderLayout.Paged
                },
            )
        }
    }

    fun clearDocument() {
        recycleAll()
        _uiState.update {
            PdfReaderUiState(libraryDocuments = it.libraryDocuments)
        }
    }

    private fun openSource() {
        val source = _uiState.value.source ?: return
        viewModelScope.launch {
            _uiState.update {
                it.copy(phase = PdfReaderPhase.Opening, passwordError = null)
            }
            val password = _uiState.value.passwordDraft.ifBlank { null }
            when (val result = inspectPdfUseCase(source, password)) {
                is ScanlyResult.Success -> {
                    recycleAll()
                    _uiState.update {
                        it.copy(
                            phase = PdfReaderPhase.Ready,
                            pageCount = result.value.pageCount,
                            documentTitle = result.value.displayName.ifBlank { source.label() },
                            currentPageIndex = 0,
                            pageBitmaps = emptyMap(),
                            loadingPages = emptySet(),
                        )
                    }
                    ensurePagesAround(0)
                }
                is ScanlyResult.Failure -> {
                    val needsPw = result.error.message.contains("password", ignoreCase = true)
                    _uiState.update {
                        it.copy(
                            phase = if (needsPw) PdfReaderPhase.Locked else PdfReaderPhase.Empty,
                            passwordError = if (needsPw) result.error.message else null,
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
        val radius = if (_uiState.value.readerLayout == PdfReaderLayout.Continuous) 2 else 1
        val range = ((center - radius)..(center + radius)).filter { it in 0 until count }
        range.forEach { renderIfNeeded(it) }
        // Evict far pages
        val keep = range.toSet()
        _uiState.update { state ->
            val evicted = state.pageBitmaps.filterKeys { it !in keep }
            evicted.values.forEach { it.recycle() }
            state.copy(pageBitmaps = state.pageBitmaps.filterKeys { it in keep })
        }
    }

    private fun renderIfNeeded(index: Int) {
        val state = _uiState.value
        if (state.pageBitmaps.containsKey(index) || index in state.loadingPages) return
        val source = state.source ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(loadingPages = it.loadingPages + index) }
            when (
                val result = renderPdfPageUseCase(
                    source = source,
                    pageIndex = index,
                    password = state.passwordDraft.ifBlank { null },
                    maxWidth = 1600,
                )
            ) {
                is ScanlyResult.Success -> {
                    _uiState.update {
                        it.copy(
                            pageBitmaps = it.pageBitmaps + (index to result.value),
                            loadingPages = it.loadingPages - index,
                        )
                    }
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(loadingPages = it.loadingPages - index) }
                    _events.emit(PdfToolEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    private fun recycleAll() {
        _uiState.value.pageBitmaps.values.forEach { it.recycle() }
    }

    override fun onCleared() {
        recycleAll()
        super.onCleared()
    }
}
