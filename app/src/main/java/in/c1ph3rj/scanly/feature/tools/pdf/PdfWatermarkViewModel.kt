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
