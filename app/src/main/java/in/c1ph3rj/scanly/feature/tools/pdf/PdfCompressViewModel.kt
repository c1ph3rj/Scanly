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
