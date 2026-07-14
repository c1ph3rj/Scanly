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

data class PdfToolUiState(
    val sources: List<PdfToolSource> = emptyList(),
    val libraryDocuments: List<ScanDocument> = emptyList(),
    val isProcessing: Boolean = false,
    val progressMessage: String = "",
    val result: ExportArtifact? = null,
    val info: PdfDocumentInfo? = null,
    val password: String = "",
    val confirmPassword: String = "",
    val currentPassword: String = "",
    val passwordMode: PdfPasswordMode = PdfPasswordMode.Protect,
    val compressQuality: PdfCompressQuality = PdfCompressQuality.MEDIUM,
    val watermarkText: String = "CONFIDENTIAL",
    val watermarkOpacity: Float = 0.24f,
    val watermarkSize: WatermarkSize = WatermarkSize.MEDIUM,
    val watermarkLayout: WatermarkLayout = WatermarkLayout.REPEATED,
    val watermarkPageRange: WatermarkPageRange = WatermarkPageRange.ALL_PAGES,
    val watermarkOrientation: WatermarkOrientation = WatermarkOrientation.DIAGONAL,
    val watermarkPreview: Bitmap? = null,
    val isWatermarkPreviewLoading: Boolean = false,
    /** First-page bitmap for compress (and similar tools) source preview. */
    val sourcePagePreview: Bitmap? = null,
    val isSourcePagePreviewLoading: Boolean = false,
    val originalSizeBytes: Long? = null,
    val compressedSizeBytes: Long? = null,
    val needsPassword: Boolean = false,
)

sealed interface PdfToolEvent {
    data class ShowMessage(val message: String) : PdfToolEvent
}

@HiltViewModel
class PdfMergeViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val mergePdfsUseCase: MergePdfsUseCase,
    private val saveExportArtifactUseCase: SaveExportArtifactUseCase,
) : BasePdfToolViewModel(observeDocumentsUseCase, saveExportArtifactUseCase) {
    fun removeSource(index: Int) {
        _uiState.update {
            val next = it.sources.toMutableList()
            if (index in next.indices) next.removeAt(index)
            it.copy(sources = next, result = null)
        }
    }

    fun runMerge() {
        val sources = _uiState.value.sources
        if (sources.size < 2) {
            emitMessage("Add at least two PDFs to merge.")
            return
        }
        runProcess("Merging PDFs…") { mergePdfsUseCase(sources) }
    }
}

@HiltViewModel
class PdfCompressViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val compressPdfUseCase: CompressPdfUseCase,
    private val inspectPdfUseCase: InspectPdfUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    saveExportArtifactUseCase: SaveExportArtifactUseCase,
) : BasePdfToolViewModel(observeDocumentsUseCase, saveExportArtifactUseCase) {
    private var previewJob: Job? = null
    private var previewRequestId = 0L

    fun setQuality(quality: PdfCompressQuality) {
        _uiState.update {
            it.copy(
                compressQuality = quality,
                result = null,
                compressedSizeBytes = null,
            )
        }
    }

    fun setCurrentPassword(value: String) {
        _uiState.update { it.copy(currentPassword = value) }
    }

    fun onSourcesChosen(sources: List<PdfToolSource>) {
        recycleSourcePreview()
        setSources(sources)
        _uiState.update {
            it.copy(
                result = null,
                compressedSizeBytes = null,
                originalSizeBytes = null,
                info = null,
                needsPassword = false,
            )
        }
        sources.firstOrNull()?.let {
            inspect(it)
            renderSourcePreview(it)
        }
    }

    fun unlockOrRefresh() {
        val source = _uiState.value.sources.firstOrNull() ?: return
        inspect(source)
        renderSourcePreview(source)
    }

    fun runCompress() {
        val source = _uiState.value.sources.firstOrNull() ?: run {
            emitMessage("Choose a PDF first.")
            return
        }
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = "Compressing PDF…",
                    result = null,
                    compressedSizeBytes = null,
                )
            }
            when (
                val result = compressPdfUseCase(
                    source = source,
                    quality = state.compressQuality,
                    password = state.currentPassword.ifBlank { null },
                )
            ) {
                is ScanlyResult.Success -> {
                    val after = File(result.value.filePath).length().takeIf { it > 0L }
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            progressMessage = "",
                            result = result.value,
                            compressedSizeBytes = after,
                        )
                    }
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                    emitMessage(result.error.message)
                }
            }
        }
    }

    private fun inspect(source: PdfToolSource) {
        viewModelScope.launch {
            when (
                val result = inspectPdfUseCase(
                    source,
                    _uiState.value.currentPassword.ifBlank { null },
                )
            ) {
                is ScanlyResult.Success -> _uiState.update {
                    it.copy(
                        info = result.value,
                        originalSizeBytes = result.value.fileSizeBytes,
                        needsPassword = false,
                    )
                }
                is ScanlyResult.Failure -> {
                    val needsPw = result.error.message.contains("password", ignoreCase = true)
                    _uiState.update {
                        it.copy(needsPassword = needsPw, info = null, originalSizeBytes = null)
                    }
                    if (!needsPw) emitMessage(result.error.message)
                }
            }
        }
    }

    private fun renderSourcePreview(source: PdfToolSource) {
        previewJob?.cancel()
        val requestId = ++previewRequestId
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(isSourcePagePreviewLoading = true) }
            val result = renderPdfPageUseCase(
                source = source,
                pageIndex = 0,
                password = _uiState.value.currentPassword.ifBlank { null },
                maxWidth = COMPRESS_PREVIEW_WIDTH,
            )
            if (requestId != previewRequestId) {
                if (result is ScanlyResult.Success) result.value.recycle()
                return@launch
            }
            when (result) {
                is ScanlyResult.Success -> {
                    val previous = _uiState.value.sourcePagePreview
                    _uiState.update {
                        it.copy(
                            sourcePagePreview = result.value,
                            isSourcePagePreviewLoading = false,
                        )
                    }
                    if (previous !== result.value) previous?.recycle()
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isSourcePagePreviewLoading = false) }
                    // Password-locked previews stay empty until unlock; other failures stay quiet.
                }
            }
        }
    }

    private fun recycleSourcePreview() {
        previewJob?.cancel()
        previewRequestId += 1
        _uiState.value.sourcePagePreview?.recycle()
        _uiState.update {
            it.copy(sourcePagePreview = null, isSourcePagePreviewLoading = false)
        }
    }

    override fun onCleared() {
        recycleSourcePreview()
        super.onCleared()
    }

    private companion object {
        const val COMPRESS_PREVIEW_WIDTH = 720
    }
}

@HiltViewModel
class PdfPasswordViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val setPdfPasswordUseCase: SetPdfPasswordUseCase,
    private val removePdfPasswordUseCase: RemovePdfPasswordUseCase,
    private val inspectPdfUseCase: InspectPdfUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    saveExportArtifactUseCase: SaveExportArtifactUseCase,
) : BasePdfToolViewModel(observeDocumentsUseCase, saveExportArtifactUseCase) {
    private var previewJob: Job? = null
    private var previewRequestId = 0L

    fun setMode(mode: PdfPasswordMode) {
        _uiState.update { it.copy(passwordMode = mode, result = null) }
    }

    fun setPassword(value: String) {
        _uiState.update { it.copy(password = value, result = null) }
    }

    fun setConfirmPassword(value: String) {
        _uiState.update { it.copy(confirmPassword = value, result = null) }
    }

    fun setCurrentPassword(value: String) {
        _uiState.update { it.copy(currentPassword = value, result = null) }
    }

    fun onSourcesChosen(sources: List<PdfToolSource>) {
        recycleSourcePreview()
        setSources(sources)
        _uiState.update {
            it.copy(
                result = null,
                info = null,
                needsPassword = false,
                password = "",
                confirmPassword = "",
                currentPassword = "",
            )
        }
        sources.firstOrNull()?.let {
            inspect(it)
            renderSourcePreview(it)
        }
    }

    fun unlockPreview() {
        val source = _uiState.value.sources.firstOrNull() ?: return
        inspect(source)
        renderSourcePreview(source)
    }

    fun run() {
        val source = _uiState.value.sources.firstOrNull() ?: run {
            emitMessage("Choose a PDF first.")
            return
        }
        val state = _uiState.value
        val alreadyProtected = state.isPdfAlreadyProtected()
        when (state.passwordMode) {
            PdfPasswordMode.Protect -> {
                if (state.password.isBlank()) {
                    emitMessage("Enter a new password.")
                    return
                }
                if (state.password != state.confirmPassword) {
                    emitMessage("Passwords do not match.")
                    return
                }
                if (alreadyProtected && state.currentPassword.isBlank()) {
                    emitMessage("Enter the current password for this protected PDF.")
                    return
                }
                runProcess("Protecting PDF…") {
                    setPdfPasswordUseCase(
                        source = source,
                        newPassword = state.password,
                        // Only pass a current password when the source is already locked.
                        currentPassword = if (alreadyProtected) {
                            state.currentPassword.ifBlank { null }
                        } else {
                            null
                        },
                    )
                }
            }
            PdfPasswordMode.Remove -> {
                if (!alreadyProtected) {
                    emitMessage("This PDF is not password protected.")
                    return
                }
                if (state.currentPassword.isBlank()) {
                    emitMessage("Enter the current password.")
                    return
                }
                runProcess("Removing password…") {
                    removePdfPasswordUseCase(source, state.currentPassword)
                }
            }
        }
    }

    private fun inspect(source: PdfToolSource) {
        viewModelScope.launch {
            when (
                val result = inspectPdfUseCase(
                    source,
                    _uiState.value.currentPassword.ifBlank { null },
                )
            ) {
                is ScanlyResult.Success -> _uiState.update {
                    it.copy(
                        info = result.value,
                        needsPassword = false,
                    )
                }
                is ScanlyResult.Failure -> {
                    val needsPw = result.error.message.contains("password", ignoreCase = true)
                    _uiState.update {
                        it.copy(needsPassword = needsPw, info = null)
                    }
                    if (!needsPw) emitMessage(result.error.message)
                }
            }
        }
    }

    private fun renderSourcePreview(source: PdfToolSource) {
        previewJob?.cancel()
        val requestId = ++previewRequestId
        previewJob = viewModelScope.launch {
            _uiState.update { it.copy(isSourcePagePreviewLoading = true) }
            val result = renderPdfPageUseCase(
                source = source,
                pageIndex = 0,
                password = _uiState.value.currentPassword.ifBlank { null },
                maxWidth = PASSWORD_PREVIEW_WIDTH,
            )
            if (requestId != previewRequestId) {
                if (result is ScanlyResult.Success) result.value.recycle()
                return@launch
            }
            when (result) {
                is ScanlyResult.Success -> {
                    val previous = _uiState.value.sourcePagePreview
                    _uiState.update {
                        it.copy(
                            sourcePagePreview = result.value,
                            isSourcePagePreviewLoading = false,
                        )
                    }
                    if (previous !== result.value) previous?.recycle()
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isSourcePagePreviewLoading = false) }
                }
            }
        }
    }

    private fun recycleSourcePreview() {
        previewJob?.cancel()
        previewRequestId += 1
        _uiState.value.sourcePagePreview?.recycle()
        _uiState.update {
            it.copy(sourcePagePreview = null, isSourcePagePreviewLoading = false)
        }
    }

    override fun onCleared() {
        recycleSourcePreview()
        super.onCleared()
    }

    private companion object {
        const val PASSWORD_PREVIEW_WIDTH = 720
    }
}

/** True when inspect failed for a password reason, or the opened PDF reports encryption. */
internal fun PdfToolUiState.isPdfAlreadyProtected(): Boolean =
    needsPassword || info?.isEncrypted == true

@HiltViewModel
class PdfWatermarkViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val watermarkPdfUseCase: WatermarkPdfUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    private val renderWatermarkPreviewUseCase: RenderWatermarkPreviewUseCase,
    saveExportArtifactUseCase: SaveExportArtifactUseCase,
) : BasePdfToolViewModel(observeDocumentsUseCase, saveExportArtifactUseCase) {
    private var previewJob: Job? = null
    private var previewRequestId = 0L

    override fun setSources(sources: List<PdfToolSource>) {
        recyclePreview()
        super.setSources(sources)
        renderPreview(sources.firstOrNull(), debounce = false)
    }

    fun setWatermarkText(value: String) {
        _uiState.update { it.copy(watermarkText = value) }
        renderPreview()
    }

    fun setOpacity(value: Float) {
        _uiState.update { it.copy(watermarkOpacity = value) }
        renderPreview()
    }

    fun setSize(value: WatermarkSize) {
        _uiState.update { it.copy(watermarkSize = value) }
        renderPreview()
    }

    fun setLayout(value: WatermarkLayout) {
        _uiState.update { it.copy(watermarkLayout = value) }
        renderPreview()
    }

    fun setPageRange(value: WatermarkPageRange) {
        _uiState.update { it.copy(watermarkPageRange = value) }
    }

    fun setOrientation(value: WatermarkOrientation) {
        _uiState.update { it.copy(watermarkOrientation = value) }
        renderPreview()
    }

    fun setCurrentPassword(value: String) {
        _uiState.update { it.copy(currentPassword = value) }
        renderPreview()
    }

    fun run() {
        val source = _uiState.value.sources.firstOrNull() ?: run {
            emitMessage("Choose a PDF first.")
            return
        }
        val state = _uiState.value
        runProcess("Adding watermark…") {
            watermarkPdfUseCase(
                source = source,
                options = state.watermarkOptions(),
                password = state.currentPassword.ifBlank { null },
            )
        }
    }

    private fun renderPreview(
        source: PdfToolSource? = _uiState.value.sources.firstOrNull(),
        debounce: Boolean = true,
    ) {
        previewJob?.cancel()
        val requestId = ++previewRequestId
        if (source == null) return
        previewJob = viewModelScope.launch {
            if (debounce) delay(WATERMARK_PREVIEW_DEBOUNCE_MS)
            val state = _uiState.value
            val options = state.watermarkOptions()
            _uiState.update { it.copy(isWatermarkPreviewLoading = true) }
            val result = if (options.text.isBlank()) {
                renderPdfPageUseCase(
                    source = source,
                    pageIndex = 0,
                    password = state.currentPassword.ifBlank { null },
                    maxWidth = WATERMARK_PREVIEW_WIDTH,
                )
            } else {
                renderWatermarkPreviewUseCase(
                    source = source,
                    options = options,
                    password = state.currentPassword.ifBlank { null },
                    maxWidth = WATERMARK_PREVIEW_WIDTH,
                )
            }
            if (requestId != previewRequestId) {
                if (result is ScanlyResult.Success) result.value.recycle()
                return@launch
            }
            when (result) {
                is ScanlyResult.Success -> {
                    val previous = _uiState.value.watermarkPreview
                    _uiState.update {
                        it.copy(
                            watermarkPreview = result.value,
                            isWatermarkPreviewLoading = false,
                        )
                    }
                    if (previous !== result.value) previous?.recycle()
                }
                is ScanlyResult.Failure -> _uiState.update {
                    it.copy(isWatermarkPreviewLoading = false)
                }
            }
        }
    }

    private fun recyclePreview() {
        previewJob?.cancel()
        previewRequestId += 1
        _uiState.value.watermarkPreview?.recycle()
        _uiState.update { it.copy(watermarkPreview = null, isWatermarkPreviewLoading = false) }
    }

    override fun onCleared() {
        recyclePreview()
        super.onCleared()
    }

    private fun PdfToolUiState.watermarkOptions() = WatermarkOptions(
        text = watermarkText.trim(),
        opacity = watermarkOpacity,
        angleDegrees = watermarkOrientation.angleDegrees,
        size = watermarkSize,
        layout = watermarkLayout,
        pageRange = watermarkPageRange,
    )

    private companion object {
        const val WATERMARK_PREVIEW_WIDTH = 720
        const val WATERMARK_PREVIEW_DEBOUNCE_MS = 180L
    }
}

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

abstract class BasePdfToolViewModel(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val saveExportArtifactUseCase: SaveExportArtifactUseCase,
) : ViewModel() {
    protected val _uiState = MutableStateFlow(PdfToolUiState())
    val uiState: StateFlow<PdfToolUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<PdfToolEvent>()
    val events: SharedFlow<PdfToolEvent> = _events.asSharedFlow()

    val documents: StateFlow<List<ScanDocument>> = observeDocumentsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            documents.collect { docs ->
                _uiState.update { it.copy(libraryDocuments = docs) }
            }
        }
    }

    open fun setSources(sources: List<PdfToolSource>) {
        _uiState.update { it.copy(sources = sources, result = null) }
    }

    fun appendDeviceUris(uris: List<Uri>, displayNames: List<String>) {
        val added = uris.mapIndexed { index, uri ->
            PdfToolSource.DeviceUri(
                uriString = uri.toString(),
                displayName = displayNames.getOrElse(index) { "Document.pdf" },
            )
        }
        _uiState.update { it.copy(sources = it.sources + added, result = null) }
    }

    fun appendLibrary(sources: List<PdfToolSource.LibraryDocument>) {
        _uiState.update { it.copy(sources = it.sources + sources, result = null) }
    }

    fun clearResult() {
        _uiState.update { it.copy(result = null, compressedSizeBytes = null) }
    }

    fun clearAll() {
        _uiState.update {
            it.copy(
                sources = emptyList(),
                result = null,
                info = null,
                originalSizeBytes = null,
                compressedSizeBytes = null,
                needsPassword = false,
            )
        }
    }

    fun saveResult() {
        val artifact = _uiState.value.result ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Saving…") }
            when (val result = saveExportArtifactUseCase(artifact)) {
                is ScanlyResult.Success ->
                    emitMessage("Saved ${result.value.fileName} to ${result.value.destinationLabel}")
                is ScanlyResult.Failure -> emitMessage(result.error.message)
            }
            _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
        }
    }

    protected fun runProcess(
        message: String,
        block: suspend () -> ScanlyResult<ExportArtifact>,
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = message, result = null) }
            when (val result = block()) {
                is ScanlyResult.Success ->
                    _uiState.update {
                        it.copy(isProcessing = false, progressMessage = "", result = result.value)
                    }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                    emitMessage(result.error.message)
                }
            }
        }
    }

    protected fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(PdfToolEvent.ShowMessage(message))
        }
    }
}
