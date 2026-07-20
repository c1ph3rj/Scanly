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
