package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.PdfWorkingCopy
import `in`.c1ph3rj.scanly.domain.usecase.RecordPositiveMomentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.ClosePdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.ImportPdfToLibraryUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.OpenPdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.PdfImportLook
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.PdfLibraryImport
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

data class PdfImportUiState(
    val source: PdfToolSource? = null,
    val isOpening: Boolean = false,
    val needsPassword: Boolean = false,
    val password: String = "",
    val pageCount: Int = 0,
    val selectedPages: Set<Int> = emptySet(),
    /** Small page renders keyed by page index, filled lazily as grid cells appear. */
    val thumbnails: Map<Int, Bitmap> = emptyMap(),
    val title: String = "",
    val look: PdfImportLook = PdfImportLook.ORIGINAL,
    val isImporting: Boolean = false,
    val importedCount: Int = 0,
    /** Pages being added, in order; [importedCount] indexes the one in progress. */
    val importPages: List<Int> = emptyList(),
    val result: PdfLibraryImport? = null,
) {
    val isReady: Boolean get() = pageCount > 0 && !needsPassword

    val sortedSelection: List<Int> get() = selectedPages.sorted()

    val canImport: Boolean get() = isReady && selectedPages.isNotEmpty() && title.isNotBlank() && !isImporting
}

sealed interface PdfImportEvent {
    data class ShowMessage(val message: String) : PdfImportEvent
}

/**
 * Add to library: choose a PDF from the phone, pick pages, name it and choose a look, then each
 * page becomes a page of a new Scanly document. The source is opened once into a decrypted
 * working copy that thumbnails and the import read in place.
 */
@HiltViewModel
class PdfImportViewModel @Inject constructor(
    private val openPdfWorkingCopyUseCase: OpenPdfWorkingCopyUseCase,
    private val closePdfWorkingCopyUseCase: ClosePdfWorkingCopyUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    private val importPdfToLibraryUseCase: ImportPdfToLibraryUseCase,
    private val recordPositiveMomentUseCase: RecordPositiveMomentUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PdfImportUiState())
    val uiState: StateFlow<PdfImportUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PdfImportEvent>()
    val events: SharedFlow<PdfImportEvent> = _events.asSharedFlow()

    private var workingCopy: PdfWorkingCopy? = null
    private var openJob: Job? = null

    /** Bumped whenever the working copy changes so late thumbnail renders are dropped. */
    private var generation = 0L
    private val thumbnailsInFlight = mutableSetOf<Int>()
    private val thumbnailPermits = Semaphore(ThumbnailParallelism)

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
                title = sanitizePdfBaseName(source.label()).ifBlank { "Imported PDF" },
                result = null,
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
                // Oldest renders past the cap are dropped, not recycled: a visible cell may still draw them.
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

    fun setTitle(value: String) {
        _uiState.update { it.copy(title = value.take(MaxTitleLength)) }
    }

    fun setLook(look: PdfImportLook) {
        _uiState.update { it.copy(look = look) }
    }

    fun import() {
        val copy = workingCopy ?: return
        val state = _uiState.value
        if (!state.canImport) {
            if (state.selectedPages.isEmpty()) emitMessage("Choose at least one page.")
            else if (state.title.isBlank()) emitMessage("Give the document a name.")
            return
        }
        val pages = state.sortedSelection
        viewModelScope.launch {
            _uiState.update {
                it.copy(isImporting = true, importedCount = 0, importPages = pages, result = null)
            }
            requestThumbnail(pages.first())
            val result = importPdfToLibraryUseCase(
                copy = copy,
                pageIndexes = pages,
                title = state.title.trim(),
                look = state.look,
                onProgress = { done, _ ->
                    _uiState.update { it.copy(importedCount = done) }
                    pages.getOrNull(done)?.let { next ->
                        withContext(Dispatchers.Main.immediate) { requestThumbnail(next) }
                    }
                },
            )
            when (result) {
                is ScanlyResult.Success -> {
                    _uiState.update { it.copy(isImporting = false, result = result.value) }
                    recordPositiveMomentUseCase()
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isImporting = false) }
                    emitMessage(result.error.message)
                }
            }
        }
    }

    /** "Import another": back to the file picker with a clean slate. */
    fun reset() {
        releaseWorkingCopy()
        _uiState.value = PdfImportUiState()
    }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _events.emit(PdfImportEvent.ShowMessage(message)) }
    }

    private fun releaseWorkingCopy() {
        openJob?.cancel()
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
        const val MaxTitleLength = 120
    }
}
