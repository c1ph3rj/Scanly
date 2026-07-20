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
