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
import `in`.c1ph3rj.scanly.domain.model.WatermarkColor
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions
import `in`.c1ph3rj.scanly.domain.model.WatermarkOrientation
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.domain.model.WatermarkSize
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecordPositiveMomentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveExportArtifactUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.CompressPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.InspectPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.MergePdfsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RemovePdfPasswordUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderWatermarkPreviewUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.SetPdfPasswordUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.WatermarkPdfUseCase
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.withContext
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
    /**
     * Base name (no extension) the result is saved / shared under. Tools seed it with a
     * readable suggestion when a result is produced; the Done screen lets the user edit it.
     */
    val outputName: String = "",
    /** Destination label after a successful "Save to phone" (e.g. "Downloads/Scanly"). */
    val savedDestinationLabel: String? = null,
    val watermarkColor: WatermarkColor = WatermarkColor.GREY,
    val watermarkAddDate: Boolean = false,
    /** Why the live watermark preview could not be drawn (e.g. unsupported characters). */
    val watermarkPreviewError: String? = null,
    /** First page of the produced file, for the Done screen's paper preview. */
    val resultPreview: Bitmap? = null,
    val resultSizeBytes: Long? = null,
)

/** Result renamed to the user's chosen [PdfToolUiState.outputName] (save uses fileName). */
fun PdfToolUiState.namedResult(): ExportArtifact? {
    val artifact = result ?: return null
    val base = sanitizePdfBaseName(outputName)
    return if (base.isBlank()) artifact else artifact.copy(fileName = "$base.pdf")
}

/** Strips characters that are illegal in file names on Android/FAT/SAF providers. */
fun sanitizePdfBaseName(raw: String): String =
    raw.trim()
        .removeSuffix(".pdf").removeSuffix(".PDF")
        .replace(Regex("[\\\\/:*?\"<>|\u0000-\u001F]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(96)

/** "Lease agreement.pdf" → "Lease agreement – watermarked". */
fun suggestPdfOutputName(source: PdfToolSource?, suffix: String): String {
    val base = source?.label()?.let(::sanitizePdfBaseName).orEmpty().ifBlank { "Scanly PDF" }
    return if (suffix.isBlank()) base else "$base – $suffix"
}

sealed interface PdfToolEvent {
    data class ShowMessage(val message: String) : PdfToolEvent
}


abstract class BasePdfToolViewModel(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val saveExportArtifactUseCase: SaveExportArtifactUseCase,
    private val resultPageRenderer: RenderPdfPageUseCase,
    private val recordPositiveMomentUseCase: RecordPositiveMomentUseCase,
) : ViewModel() {
    private var resultPreviewJob: Job? = null
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
        recycleResultPreview()
        _uiState.update { it.copy(result = null, compressedSizeBytes = null, savedDestinationLabel = null) }
    }

    /** Password needed to open the produced file (only the Protect flow sets one). */
    protected open fun resultPreviewPassword(): String? = null

    /** Called after a successful run; renders page 1 of the output for the Done screen. */
    protected open fun onResultReady(artifact: ExportArtifact) {
        resultPreviewJob?.cancel()
        resultPreviewJob = viewModelScope.launch {
            val size = withContext(Dispatchers.IO) { File(artifact.filePath).length().takeIf { it > 0L } }
            _uiState.update { it.copy(resultSizeBytes = size) }
            val rendered = resultPageRenderer(
                source = PdfToolSource.AppFile(artifact.filePath, artifact.fileName),
                pageIndex = 0,
                password = resultPreviewPassword(),
                maxWidth = RESULT_PREVIEW_WIDTH,
            )
            if (rendered is ScanlyResult.Success) {
                if (_uiState.value.result?.filePath != artifact.filePath) {
                    rendered.value.recycle()
                    return@launch
                }
                _uiState.update { it.copy(resultPreview = rendered.value) }
            }
        }
    }

    protected fun recycleResultPreview() {
        resultPreviewJob?.cancel()
        _uiState.update { it.copy(resultPreview = null, resultSizeBytes = null) }
    }

    override fun onCleared() {
        resultPreviewJob?.cancel()
        super.onCleared()
    }

    fun setOutputName(value: String) {
        _uiState.update { it.copy(outputName = value.take(96), savedDestinationLabel = null) }
    }

    /**
     * Copies the result to a file named after [PdfToolUiState.outputName] so the share sheet
     * and receiving apps see a readable name, then hands it to [onReady] on the main thread.
     */
    fun prepareNamedShare(onReady: (ExportArtifact) -> Unit) {
        val state = _uiState.value
        val artifact = state.namedResult() ?: return
        viewModelScope.launch {
            val shareable = runCatching {
                withContext(Dispatchers.IO) {
                    val source = File(artifact.filePath)
                    if (source.name == artifact.fileName) return@withContext artifact
                    val dir = File(source.parentFile, "named").apply { mkdirs() }
                    val target = File(dir, artifact.fileName)
                    source.copyTo(target, overwrite = true)
                    artifact.copy(filePath = target.absolutePath)
                }
            }.getOrElse { artifact }
            onReady(shareable)
        }
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
        val artifact = _uiState.value.namedResult() ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isProcessing = true, progressMessage = "Saving…") }
            when (val result = saveExportArtifactUseCase(artifact)) {
                is ScanlyResult.Success -> {
                    _uiState.update { it.copy(savedDestinationLabel = result.value.destinationLabel) }
                    emitMessage("Saved ${result.value.fileName} to ${result.value.destinationLabel}")
                }
                is ScanlyResult.Failure -> emitMessage(result.error.message)
            }
            _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
        }
    }

    /**
     * Runs a tool operation. [outputName] seeds the editable result name shown on the Done
     * screen (see [suggestPdfOutputName]).
     */
    protected fun runProcess(
        message: String,
        outputName: String? = null,
        block: suspend () -> ScanlyResult<ExportArtifact>,
    ) {
        recycleResultPreview()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    progressMessage = message,
                    result = null,
                    savedDestinationLabel = null,
                )
            }
            when (val result = block()) {
                is ScanlyResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isProcessing = false,
                            progressMessage = "",
                            result = result.value,
                            outputName = outputName ?: it.outputName.ifBlank {
                                sanitizePdfBaseName(result.value.fileName)
                            },
                        )
                    }
                    recordPositiveMomentUseCase()
                    onResultReady(result.value)
                }
                is ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isProcessing = false, progressMessage = "") }
                    emitMessage(result.error.message)
                }
            }
        }
    }

    private companion object {
        const val RESULT_PREVIEW_WIDTH = 720
    }

    protected fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(PdfToolEvent.ShowMessage(message))
        }
    }
}
