package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PdfImageExport
import `in`.c1ph3rj.scanly.domain.model.PdfImageFormat
import `in`.c1ph3rj.scanly.domain.model.PdfImageOptions
import `in`.c1ph3rj.scanly.domain.model.PdfImageResolution
import `in`.c1ph3rj.scanly.domain.model.PdfPageImage
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.PdfWorkingCopy
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecordPositiveMomentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.ClosePdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.EstimatePdfPageImageSizeUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.ExportPdfPagesAsImagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.OpenPdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class PdfToImagesUiState(
    val libraryDocuments: List<ScanDocument> = emptyList(),
    val source: PdfToolSource? = null,
    val isOpening: Boolean = false,
    val needsPassword: Boolean = false,
    val password: String = "",
    val pageCount: Int = 0,
    val selectedPages: Set<Int> = emptySet(),
    /** Small page renders keyed by page index, filled lazily as grid cells appear. */
    val thumbnails: Map<Int, Bitmap> = emptyMap(),
    val options: PdfImageOptions = PdfImageOptions(),
    /** Measured size of one encoded page per resolution, for the current format. */
    val estimates: Map<PdfImageResolution, Long> = emptyMap(),
    val isExporting: Boolean = false,
    val exportedCount: Int = 0,
    val exportTotal: Int = 0,
    /** Pages being saved, in order; [exportedCount] indexes the one in progress. */
    val exportPages: List<Int> = emptyList(),
    val result: PdfImageExport? = null,
    /** Page indexes picked on the Done step for a partial share; empty shares everything. */
    val shareSelection: Set<Int> = emptySet(),
) {
    val isReady: Boolean get() = pageCount > 0 && !needsPassword

    val sortedSelection: List<Int> get() = selectedPages.sorted()

    val estimateEach: Long? get() = estimates[options.resolution]

    val shareTargets: List<PdfPageImage>
        get() {
            val images = result?.images.orEmpty()
            return if (shareSelection.isEmpty()) images else images.filter { it.pageIndex in shareSelection }
        }
}

sealed interface PdfToImagesEvent {
    data class ShowMessage(val message: String) : PdfToImagesEvent
}

/**
 * PDF to JPG: choose a PDF, pick pages + format + resolution, then save each page to the
 * gallery. The source is opened once into a decrypted working copy that thumbnails, size
 * estimates and the export all read in place.
 */
@HiltViewModel
class PdfToImagesViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val openPdfWorkingCopyUseCase: OpenPdfWorkingCopyUseCase,
    private val closePdfWorkingCopyUseCase: ClosePdfWorkingCopyUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    private val estimatePdfPageImageSizeUseCase: EstimatePdfPageImageSizeUseCase,
    private val exportPdfPagesAsImagesUseCase: ExportPdfPagesAsImagesUseCase,
    private val recordPositiveMomentUseCase: RecordPositiveMomentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfToImagesUiState())
    val uiState: StateFlow<PdfToImagesUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PdfToImagesEvent>()
    val events: SharedFlow<PdfToImagesEvent> = _events.asSharedFlow()

    private var workingCopy: PdfWorkingCopy? = null
    private var openJob: Job? = null
    private var estimateJob: Job? = null

    /** Bumped whenever the working copy changes so late thumbnail renders are dropped. */
    private var generation = 0L
    private val thumbnailsInFlight = mutableSetOf<Int>()
    private val thumbnailPermits = Semaphore(ThumbnailParallelism)

    init {
        viewModelScope.launch {
            observeDocumentsUseCase().collect { docs ->
                _uiState.update { it.copy(libraryDocuments = docs) }
            }
        }
    }

    fun onSourceChosen(source: PdfToolSource) {
        releaseWorkingCopy()
        _uiState.update {
            it.copy(
                source = source,
                needsPassword = false,
                password = "",
                pageCount = 0,
                selectedPages = emptySet(),
                thumbnails = emptyMap(),
                estimates = emptyMap(),
                result = null,
                shareSelection = emptySet(),
            )
        }
        open(source, password = null)
    }

    fun setPassword(value: String) {
        _uiState.update { it.copy(password = value) }
    }

    fun unlock() {
        val state = _uiState.value
        val source = state.source ?: return
        if (state.password.isBlank()) return
        open(source, state.password)
    }

    private fun open(source: PdfToolSource, password: String?) {
        openJob?.cancel()
        val requestGeneration = ++generation
        openJob = viewModelScope.launch {
            _uiState.update { it.copy(isOpening = true) }
            val result = openPdfWorkingCopyUseCase(source, password)
            if (requestGeneration != generation) {
                if (result is ScanlyResult.Success) closeQuietly(result.value)
                return@launch
            }
            when (result) {
                is ScanlyResult.Success -> {
                    workingCopy = result.value
                    _uiState.update {
                        it.copy(
                            isOpening = false,
                            needsPassword = false,
                            pageCount = result.value.pageCount,
                            selectedPages = (0 until result.value.pageCount).toSet(),
                        )
                    }
                    refreshEstimates()
                }
                is ScanlyResult.Failure -> {
                    val message = result.error.message
                    val passwordProblem = message.contains("password", ignoreCase = true)
                    _uiState.update { it.copy(isOpening = false, needsPassword = passwordProblem) }
                    // A wrong password is worth saying; the first "it's locked" is shown inline.
                    if (!passwordProblem || password != null) emitMessage(message)
                }
            }
        }
    }

    /** Renders page [index] for the grid if it isn't cached or already on its way. */
    fun requestThumbnail(index: Int) {
        val copy = workingCopy ?: return
        if (index in _uiState.value.thumbnails || !thumbnailsInFlight.add(index)) return
        val requestGeneration = generation
        viewModelScope.launch {
            val result = thumbnailPermits.withPermit {
                if (requestGeneration != generation) return@withPermit null
                renderPdfPageUseCase(copy.file, index, maxWidth = ThumbnailLongEdgePx)
            }
            thumbnailsInFlight.remove(index)
            if (result !is ScanlyResult.Success) return@launch
            if (requestGeneration != generation) {
                result.value.recycle()
                return@launch
            }
            _uiState.update { state ->
                // Insertion order doubles as age: drop the oldest renders past the cap. They are
                // not recycled because a visible cell may still be drawing them; GC frees them.
                val next = LinkedHashMap(state.thumbnails)
                next[index] = result.value
                while (next.size > MaxCachedThumbnails) next.remove(next.keys.first())
                state.copy(thumbnails = next)
            }
        }
    }

    fun togglePage(index: Int) {
        _uiState.update { it.copy(selectedPages = it.selectedPages.toggled(index)) }
    }

    /** Drag-select hands over the whole new set; indexes outside the PDF are dropped. */
    fun setSelectedPages(pages: Set<Int>) {
        _uiState.update { state ->
            state.copy(selectedPages = pages.filterTo(mutableSetOf()) { it in 0 until state.pageCount })
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedPages = (0 until it.pageCount).toSet()) }
    }

    fun selectNone() {
        _uiState.update { it.copy(selectedPages = emptySet()) }
    }

    fun setFormat(format: PdfImageFormat) {
        if (_uiState.value.options.format == format) return
        _uiState.update { it.copy(options = it.options.copy(format = format), estimates = emptyMap()) }
        refreshEstimates()
    }

    fun setResolution(resolution: PdfImageResolution) {
        _uiState.update { it.copy(options = it.options.copy(resolution = resolution)) }
    }

    /** Measures page one at both resolutions so each row and the button can show a size. */
    private fun refreshEstimates() {
        val copy = workingCopy ?: return
        val format = _uiState.value.options.format
        estimateJob?.cancel()
        estimateJob = viewModelScope.launch {
            // Selected resolution first so the button's total appears as soon as possible.
            val selected = _uiState.value.options.resolution
            val order = listOf(selected) + PdfImageResolution.entries.filter { it != selected }
            for (resolution in order) {
                val result = estimatePdfPageImageSizeUseCase(copy, 0, PdfImageOptions(format, resolution))
                if (result !is ScanlyResult.Success) continue
                if (workingCopy !== copy || _uiState.value.options.format != format) return@launch
                _uiState.update { it.copy(estimates = it.estimates + (resolution to result.value)) }
            }
        }
    }

    fun export() {
        val copy = workingCopy ?: return
        val state = _uiState.value
        val pages = state.sortedSelection
        if (pages.isEmpty()) {
            emitMessage("Choose at least one page.")
            return
        }
        val baseName = sanitizePdfBaseName(state.source?.label().orEmpty()).ifBlank { "Scanly" }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isExporting = true,
                    exportedCount = 0,
                    exportTotal = pages.size,
                    exportPages = pages,
                    result = null,
                )
            }
            // The Saving screen shows the page in progress: render it (and each next one) as a
            // thumbnail while the full-size image encodes. Cached pages return immediately.
            requestThumbnail(pages.first())
            val result = exportPdfPagesAsImagesUseCase(
                copy = copy,
                pageIndexes = pages,
                options = state.options,
                baseName = baseName,
                onProgress = { done, _ ->
                    _uiState.update { it.copy(exportedCount = done) }
                    pages.getOrNull(done)?.let { next ->
                        withContext(Dispatchers.Main.immediate) { requestThumbnail(next) }
                    }
                },
            )
            when (result) {
                is ScanlyResult.Success -> {
                    _uiState.update {
                        it.copy(isExporting = false, result = result.value, shareSelection = emptySet())
                    }
                    recordPositiveMomentUseCase()
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isExporting = false) }
                    emitMessage(result.error.message)
                }
            }
        }
    }

    fun toggleShare(pageIndex: Int) {
        _uiState.update { it.copy(shareSelection = it.shareSelection.toggled(pageIndex)) }
    }

    fun setShareSelection(pageIndexes: Set<Int>) {
        _uiState.update { it.copy(shareSelection = pageIndexes) }
    }

    /** "Export again": back to the pages step with the same file, pages and options. */
    fun exportAgain() {
        _uiState.update { it.copy(result = null, shareSelection = emptySet()) }
    }

    fun showMessage(message: String) = emitMessage(message)

    private fun emitMessage(message: String) {
        viewModelScope.launch { _events.emit(PdfToImagesEvent.ShowMessage(message)) }
    }

    private fun releaseWorkingCopy() {
        openJob?.cancel()
        estimateJob?.cancel()
        generation += 1
        thumbnailsInFlight.clear()
        workingCopy?.let(::closeQuietly)
        workingCopy = null
    }

    private fun closeQuietly(copy: PdfWorkingCopy) {
        CoroutineScope(Dispatchers.IO + NonCancellable).launch { closePdfWorkingCopyUseCase(copy) }
    }

    override fun onCleared() {
        releaseWorkingCopy()
        super.onCleared()
    }

    private companion object {
        const val ThumbnailLongEdgePx = 480
        const val ThumbnailParallelism = 2
        const val MaxCachedThumbnails = 48
    }
}
