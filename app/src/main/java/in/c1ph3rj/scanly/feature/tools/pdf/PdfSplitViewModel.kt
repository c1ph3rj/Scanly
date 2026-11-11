package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.appPluralString
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.PdfWorkingCopy
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecordPositiveMomentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveExportArtifactUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.ClosePdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.OpenPdfWorkingCopyUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.SplitPdfPagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.ZipPdfToolFilesUseCase
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
import java.io.File
import javax.inject.Inject

/** One PDF the split wrote, with the name the user can still edit on the Done step. */
data class PdfSplitOutput(
    val artifact: ExportArtifact,
    val pageIndexes: List<Int>,
    val name: String,
    val sizeBytes: Long?,
) {
    /** The file renamed to [name] (sanitized), as saved, zipped and shared. */
    fun named(): ExportArtifact {
        val base = sanitizePdfBaseName(name)
        return if (base.isBlank()) artifact else artifact.copy(fileName = "$base.pdf")
    }
}

data class PdfSplitUiState(
    val libraryDocuments: List<ScanDocument> = emptyList(),
    val source: PdfToolSource? = null,
    val isOpening: Boolean = false,
    val needsPassword: Boolean = false,
    val password: String = "",
    /** True once the file was unlocked with a password: the parts are written without one. */
    val wasLocked: Boolean = false,
    val pageCount: Int = 0,
    /** Small page renders keyed by page index, filled lazily. */
    val thumbnails: Map<Int, Bitmap> = emptyMap(),
    val mode: PdfSplitMode = PdfSplitMode.Custom,
    val customRanges: List<PdfPageRange> = emptyList(),
    /** What the user typed in the ranges field; null shows the cards' ranges instead. */
    val customText: String? = null,
    val customTextError: Boolean = false,
    val mergeRanges: Boolean = false,
    val pagesPerPart: Int = 1,
    val selectedPages: Set<Int> = emptySet(),
    /** What the user typed in the Extract pages field; null shows the grid selection. */
    val extractText: String? = null,
    val extractTextError: Boolean = false,
    val extractSeparately: Boolean = false,
    val keepRest: Boolean = false,
    val isSplitting: Boolean = false,
    val splitDone: Int = 0,
    val splitTotal: Int = 0,
    val outputs: List<PdfSplitOutput> = emptyList(),
    val saveAsZip: Boolean = false,
    val isSaving: Boolean = false,
    val savedDestinationLabel: String? = null,
) {
    val isReady: Boolean get() = pageCount > 0 && !needsPassword

    val choices: PdfSplitChoices
        get() = PdfSplitChoices(
            pageCount = pageCount,
            mode = mode,
            customRanges = customRanges,
            mergeRanges = mergeRanges,
            pagesPerPart = pagesPerPart,
            selectedPages = selectedPages,
            extractSeparately = extractSeparately,
            keepRest = keepRest,
        )

    /** The PDFs a split would write now; empty when it can't run. */
    val plan: List<PdfSplitOutputPlan> get() = planSplitOutputs(choices)

    val hasResult: Boolean get() = outputs.isNotEmpty()
}

sealed interface PdfSplitEvent {
    data class ShowMessage(val message: String) : PdfSplitEvent
    data class Share(val artifact: ShareArtifact) : PdfSplitEvent
}

/**
 * Split PDF: choose a PDF, decide where to cut (custom ranges, every N pages, or extract
 * picked pages), write the parts, then rename, save or share them. The source is opened once
 * into a decrypted working copy that thumbnails and the split read in place.
 */
@HiltViewModel
class PdfSplitViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val openPdfWorkingCopyUseCase: OpenPdfWorkingCopyUseCase,
    private val closePdfWorkingCopyUseCase: ClosePdfWorkingCopyUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    private val splitPdfPagesUseCase: SplitPdfPagesUseCase,
    private val zipPdfToolFilesUseCase: ZipPdfToolFilesUseCase,
    private val saveExportArtifactUseCase: SaveExportArtifactUseCase,
    private val recordPositiveMomentUseCase: RecordPositiveMomentUseCase,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        PdfSplitUiState(
            mode = savedStateHandle.get<String>(KeyMode)
                ?.let { name -> PdfSplitMode.entries.firstOrNull { it.name == name } }
                ?: PdfSplitMode.Custom,
        ),
    )
    val uiState: StateFlow<PdfSplitUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PdfSplitEvent>()
    val events: SharedFlow<PdfSplitEvent> = _events.asSharedFlow()

    private var workingCopy: PdfWorkingCopy? = null
    private var openJob: Job? = null

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
            PdfSplitUiState(
                libraryDocuments = it.libraryDocuments,
                source = source,
                mode = it.mode,
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
                    val count = result.value.pageCount
                    _uiState.update {
                        it.copy(
                            isOpening = false,
                            needsPassword = false,
                            wasLocked = password != null,
                            pageCount = count,
                            customRanges = restoredRanges(count) ?: defaultCustomRanges(count),
                            customText = null,
                            pagesPerPart = savedStateHandle.get<Int>(KeyPagesPerPart)
                                ?.coerceIn(1, maxPagesPerPart(count))
                                ?: defaultPagesPerPart(count),
                            selectedPages = emptySet(),
                        )
                    }
                    persistChoices()
                }
                is ScanlyResult.Failure -> {
                    val message = result.error.message
                    val passwordProblem = message.contains("password", ignoreCase = true)
                    _uiState.update { it.copy(isOpening = false, needsPassword = passwordProblem) }
                    if (!passwordProblem || password != null) emitMessage(message)
                }
            }
        }
    }

    /** Renders page [index] for the grid and range cards if it isn't cached or on its way. */
    fun requestThumbnail(index: Int) {
        val copy = workingCopy ?: return
        if (index !in 0 until _uiState.value.pageCount) return
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
                val next = LinkedHashMap(state.thumbnails)
                next[index] = result.value
                while (next.size > MaxCachedThumbnails) next.remove(next.keys.first())
                state.copy(thumbnails = next)
            }
        }
    }

    // ── Mode ───────────────────────────────────────────────────────────────────────────────

    fun setMode(mode: PdfSplitMode) {
        _uiState.update { it.copy(mode = mode) }
        persistChoices()
    }

    // ── Custom ranges ──────────────────────────────────────────────────────────────────────

    /** Typed ranges replace the cards once they parse; until then the cards keep their value. */
    fun setCustomText(text: String) {
        _uiState.update { state ->
            val parsed = parsePageRanges(text, state.pageCount)
            if (parsed != null) {
                state.copy(customText = text, customRanges = parsed, customTextError = false)
            } else {
                // Trailing "-" or "," is mid-typing, not a mistake.
                val unfinished = text.isBlank() || text.trimEnd().lastOrNull() in UnfinishedEndings
                state.copy(customText = text, customTextError = !unfinished)
            }
        }
        persistChoices()
    }

    fun setRangeFrom(index: Int, from: Int) = editRange(index) { it.copy(from = from) }

    fun setRangeTo(index: Int, to: Int) = editRange(index) { it.copy(to = to) }

    private fun editRange(index: Int, edit: (PdfPageRange) -> PdfPageRange) {
        _uiState.update { state ->
            if (index !in state.customRanges.indices) return@update state
            val next = state.customRanges.toMutableList().apply { this[index] = edit(this[index]) }
            state.copy(customRanges = next, customText = null, customTextError = false)
        }
        persistChoices()
    }

    fun addRange() {
        _uiState.update { state ->
            state.copy(
                customRanges = state.customRanges + nextRange(state.customRanges, state.pageCount),
                customText = null,
                customTextError = false,
            )
        }
        persistChoices()
    }

    fun removeRange(index: Int) {
        _uiState.update { state ->
            if (state.customRanges.size <= 1 || index !in state.customRanges.indices) return@update state
            state.copy(
                customRanges = state.customRanges.filterIndexed { i, _ -> i != index },
                customText = null,
                customTextError = false,
            )
        }
        persistChoices()
    }

    fun setMergeRanges(merge: Boolean) {
        _uiState.update { it.copy(mergeRanges = merge) }
    }

    // ── Every N pages ──────────────────────────────────────────────────────────────────────

    fun setPagesPerPart(value: Int) {
        _uiState.update { it.copy(pagesPerPart = value.coerceIn(1, maxPagesPerPart(it.pageCount))) }
        persistChoices()
    }

    // ── Extract ────────────────────────────────────────────────────────────────────────────

    fun togglePage(index: Int) {
        _uiState.update { it.copy(selectedPages = it.selectedPages.toggled(index), extractText = null, extractTextError = false) }
    }

    fun setSelectedPages(pages: Set<Int>) {
        _uiState.update { state ->
            state.copy(
                selectedPages = pages.filterTo(mutableSetOf()) { it in 0 until state.pageCount },
                extractText = null,
                extractTextError = false,
            )
        }
    }

    fun selectAll() {
        _uiState.update { it.copy(selectedPages = (0 until it.pageCount).toSet(), extractText = null, extractTextError = false) }
    }

    fun selectNone() {
        _uiState.update { it.copy(selectedPages = emptySet(), extractText = null, extractTextError = false) }
    }

    /** Typed pages ("2-3, 6") pick those pages in the grid once they parse. */
    fun setExtractText(text: String) {
        _uiState.update { state ->
            if (text.isBlank()) return@update state.copy(extractText = text, selectedPages = emptySet(), extractTextError = false)
            val parsed = parsePageRanges(text, state.pageCount)
            if (parsed != null) {
                val pages = parsed.flatMap(PdfPageRange::pageIndexes).toSet()
                state.copy(extractText = text, selectedPages = pages, extractTextError = false)
            } else {
                val unfinished = text.trimEnd().lastOrNull() in UnfinishedEndings
                state.copy(extractText = text, extractTextError = !unfinished)
            }
        }
    }

    fun setExtractSeparately(separately: Boolean) {
        _uiState.update { it.copy(extractSeparately = separately) }
    }

    fun setKeepRest(keep: Boolean) {
        _uiState.update { it.copy(keepRest = keep) }
    }

    // ── Split ──────────────────────────────────────────────────────────────────────────────

    fun split() {
        val copy = workingCopy ?: return
        val state = _uiState.value
        if (state.isSplitting) return
        val plan = state.plan
        if (plan.isEmpty()) return
        val baseName = sanitizePdfBaseName(state.source?.label().orEmpty())
            .ifBlank { appString(R.string.pdf_tool_shared_scanly_pdf) }
        _uiState.update {
            it.copy(isSplitting = true, splitDone = 0, splitTotal = plan.size, outputs = emptyList(), savedDestinationLabel = null)
        }
        viewModelScope.launch {
            val result = splitPdfPagesUseCase(
                copy = copy,
                outputs = plan.map(PdfSplitOutputPlan::pageIndexes),
                onProgress = { done, _ -> _uiState.update { it.copy(splitDone = done) } },
            )
            when (result) {
                is ScanlyResult.Success -> {
                    val outputs = withContext(Dispatchers.IO) {
                        result.value.mapIndexed { index, artifact ->
                            val pages = plan[index].pageIndexes
                            PdfSplitOutput(
                                artifact = artifact,
                                pageIndexes = pages,
                                name = suggestSplitOutputName(baseName, plan[index]),
                                sizeBytes = File(artifact.filePath).length().takeIf { it > 0L },
                            )
                        }
                    }
                    _uiState.update { it.copy(isSplitting = false, outputs = outputs, saveAsZip = false) }
                    recordPositiveMomentUseCase()
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isSplitting = false) }
                    emitMessage(result.error.message)
                }
            }
        }
    }

    // ── Done ───────────────────────────────────────────────────────────────────────────────

    fun renameOutput(index: Int, name: String) {
        _uiState.update { state ->
            if (index !in state.outputs.indices) return@update state
            val next = state.outputs.toMutableList()
            next[index] = next[index].copy(name = name.replace('\n', ' ').take(96))
            state.copy(outputs = next, savedDestinationLabel = null)
        }
    }

    fun setSaveAsZip(zip: Boolean) {
        _uiState.update { it.copy(saveAsZip = zip, savedDestinationLabel = null) }
    }

    /** Back from Done to Where to split, keeping the file and choices. */
    fun splitAgain() {
        _uiState.update { it.copy(outputs = emptyList(), savedDestinationLabel = null) }
    }

    fun saveAll() {
        val state = _uiState.value
        if (state.isSaving || state.outputs.isEmpty()) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            val files = state.outputs.map(PdfSplitOutput::named)
            if (state.saveAsZip && files.size > 1) {
                when (val zip = zipPdfToolFilesUseCase(files, zipBaseName(state))) {
                    is ScanlyResult.Success -> when (val saved = saveExportArtifactUseCase(zip.value)) {
                        is ScanlyResult.Success -> {
                            _uiState.update { it.copy(savedDestinationLabel = saved.value.destinationLabel) }
                            emitMessage(appString(R.string.document_detail_saved_to, saved.value.fileName, saved.value.destinationLabel))
                        }
                        is ScanlyResult.Failure -> emitMessage(saved.error.message)
                    }
                    is ScanlyResult.Failure -> emitMessage(zip.error.message)
                }
            } else {
                var savedCount = 0
                var destination: String? = null
                for (file in files) {
                    when (val saved = saveExportArtifactUseCase(file)) {
                        is ScanlyResult.Success -> {
                            savedCount++
                            destination = saved.value.destinationLabel
                        }
                        is ScanlyResult.Failure -> {
                            emitMessage(saved.error.message)
                            break
                        }
                    }
                }
                if (savedCount == files.size && destination != null) {
                    _uiState.update { it.copy(savedDestinationLabel = destination) }
                    emitMessage(appPluralString(R.plurals.pdf_split_saved, savedCount, savedCount, destination))
                }
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    /** Copies each part (or the ZIP) under its readable name, then asks the screen to share. */
    fun shareAll() {
        val state = _uiState.value
        if (state.outputs.isEmpty()) return
        viewModelScope.launch {
            val files = state.outputs.map(PdfSplitOutput::named)
            val share = if (state.saveAsZip && files.size > 1) {
                when (val zip = zipPdfToolFilesUseCase(files, zipBaseName(state))) {
                    is ScanlyResult.Success -> ShareArtifact(zip.value.mimeType, zip.value.fileName, listOf(zip.value.filePath))
                    is ScanlyResult.Failure -> {
                        emitMessage(zip.error.message)
                        return@launch
                    }
                }
            } else {
                val named = withContext(Dispatchers.IO) { files.map(::namedCopy) }
                ShareArtifact(
                    mimeType = PdfMimeType,
                    title = appPluralString(R.plurals.pdf_split_pdf_count, named.size, named.size),
                    filePaths = named.map(ExportArtifact::filePath),
                )
            }
            _events.emit(PdfSplitEvent.Share(share))
        }
    }

    /** One part renamed, for opening in the reader. */
    fun outputForPreview(index: Int): ExportArtifact? = _uiState.value.outputs.getOrNull(index)?.named()

    private fun zipBaseName(state: PdfSplitUiState): String =
        sanitizePdfBaseName(state.source?.label().orEmpty()).ifBlank { appString(R.string.pdf_tool_shared_scanly_pdf) }

    /** Share sheets show the file name, so each part is copied next to itself under its name. */
    private fun namedCopy(artifact: ExportArtifact): ExportArtifact {
        val source = File(artifact.filePath)
        if (source.name == artifact.fileName) return artifact
        return runCatching {
            val dir = File(source.parentFile, "named_${source.nameWithoutExtension}").apply { mkdirs() }
            val target = File(dir, artifact.fileName)
            source.copyTo(target, overwrite = true)
            artifact.copy(filePath = target.absolutePath)
        }.getOrElse { artifact }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────────────────

    private fun persistChoices() {
        val state = _uiState.value
        savedStateHandle[KeyMode] = state.mode.name
        savedStateHandle[KeyPagesPerPart] = state.pagesPerPart
        savedStateHandle[KeyRanges] = formatPageRanges(state.customRanges)
    }

    private fun restoredRanges(pageCount: Int): List<PdfPageRange>? =
        savedStateHandle.get<String>(KeyRanges)?.let { parsePageRanges(it, pageCount) }

    private fun emitMessage(message: String) {
        viewModelScope.launch { _events.emit(PdfSplitEvent.ShowMessage(message)) }
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
        const val PdfMimeType = "application/pdf"
        const val KeyMode = "split_mode"
        const val KeyPagesPerPart = "split_pages_per_part"
        const val KeyRanges = "split_ranges"
        const val ThumbnailLongEdgePx = 360
        const val ThumbnailParallelism = 2
        const val MaxCachedThumbnails = 60
        val UnfinishedEndings = setOf('-', '–', '—', ',', ';', ' ')
    }
}

/** "Lease – pages 1–3, 6" or "Lease – page 4": names say which pages each PDF holds. */
fun suggestSplitOutputName(baseName: String, plan: PdfSplitOutputPlan): String {
    val runs = plan.runs
    val name = if (runs.size == 1 && runs[0].from == runs[0].to) {
        appString(R.string.pdf_split_output_page, baseName, runs[0].from)
    } else {
        appString(R.string.pdf_split_output_pages, baseName, formatPageRangesForDisplay(runs))
    }
    return sanitizePdfBaseName(name)
}
