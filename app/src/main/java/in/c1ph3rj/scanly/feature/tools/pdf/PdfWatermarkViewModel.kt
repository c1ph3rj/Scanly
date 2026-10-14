package `in`.c1ph3rj.scanly.feature.tools.pdf

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.WatermarkColor
import `in`.c1ph3rj.scanly.domain.model.WatermarkLayout
import `in`.c1ph3rj.scanly.domain.model.WatermarkOptions
import `in`.c1ph3rj.scanly.domain.model.WatermarkOrientation
import `in`.c1ph3rj.scanly.domain.model.WatermarkPageRange
import `in`.c1ph3rj.scanly.domain.model.WatermarkSize
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveExportArtifactUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.InspectPdfUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderWatermarkPreviewUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.WatermarkPdfUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

/** Common stamps offered as one-tap presets in the editor. */
val WatermarkPresets: List<String> = listOf("CONFIDENTIAL", "DRAFT", "COPY", "For KYC use only")

/** "14 Oct 2026" — appended to the stamp when "Add date" is on. */
fun formatWatermarkDate(millis: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("d MMM yyyy", Locale.ENGLISH).format(Date(millis))

/** The exact text that will be stamped: user text plus the optional date. */
fun PdfToolUiState.watermarkLabel(dateText: String = formatWatermarkDate()): String {
    val text = watermarkText.trim()
    return when {
        !watermarkAddDate -> text
        text.isEmpty() -> dateText
        else -> "$text · $dateText"
    }
}

/** "Tiled · diagonal · 24%" — the style summary on the Done screen. */
fun PdfToolUiState.watermarkStyleSummary(): String {
    val layout = if (watermarkLayout == WatermarkLayout.REPEATED) "Tiled" else "Single"
    val angle = if (watermarkOrientation == WatermarkOrientation.DIAGONAL) "diagonal" else "horizontal"
    val percent = (watermarkOpacity * 100f).toInt()
    return "$layout · $angle · $percent% · ${watermarkColor.label.lowercase()}"
}

@HiltViewModel
class PdfWatermarkViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val watermarkPdfUseCase: WatermarkPdfUseCase,
    private val inspectPdfUseCase: InspectPdfUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    private val renderWatermarkPreviewUseCase: RenderWatermarkPreviewUseCase,
    saveExportArtifactUseCase: SaveExportArtifactUseCase,
) : BasePdfToolViewModel(observeDocumentsUseCase, saveExportArtifactUseCase, renderPdfPageUseCase) {
    private var previewJob: Job? = null
    private var previewRequestId = 0L
    private var inspectJob: Job? = null

    override fun setSources(sources: List<PdfToolSource>) {
        recyclePreview()
        recycleResultPreview()
        super.setSources(sources)
        _uiState.update {
            it.copy(
                info = null,
                needsPassword = false,
                currentPassword = "",
                watermarkPreviewError = null,
                outputName = "",
            )
        }
        val source = sources.firstOrNull() ?: return
        inspect(source)
        renderPreview(source, debounce = false)
    }

    fun setWatermarkText(value: String) {
        // PDF text has no line breaks; keep the field single-line and bounded like the design.
        _uiState.update { it.copy(watermarkText = value.replace('\n', ' ').take(MaxTextLength)) }
        renderPreview()
    }

    fun toggleDate() {
        _uiState.update { it.copy(watermarkAddDate = !it.watermarkAddDate) }
        renderPreview()
    }

    fun setOpacity(value: Float) {
        _uiState.update { it.copy(watermarkOpacity = value.coerceIn(MinOpacity, MaxOpacity)) }
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

    fun setOrientation(value: WatermarkOrientation) {
        _uiState.update { it.copy(watermarkOrientation = value) }
        renderPreview()
    }

    fun setColor(value: WatermarkColor) {
        _uiState.update { it.copy(watermarkColor = value) }
        renderPreview()
    }

    fun setPageRange(value: WatermarkPageRange) {
        _uiState.update { it.copy(watermarkPageRange = value) }
    }

    fun setCurrentPassword(value: String) {
        _uiState.update { it.copy(currentPassword = value) }
    }

    /** Retries opening a locked PDF with the typed password. */
    fun unlock() {
        val source = _uiState.value.sources.firstOrNull() ?: return
        inspect(source)
        renderPreview(source, debounce = false)
    }

    /** Leaves the Done screen to tweak the stamp; the source and settings are kept. */
    fun editAgain() {
        clearResult()
        renderPreview(debounce = false)
    }

    fun run() {
        val state = _uiState.value
        val source = state.sources.firstOrNull() ?: run {
            emitMessage("Choose a PDF first.")
            return
        }
        val options = state.watermarkOptions()
        if (options.text.isBlank()) {
            emitMessage("Enter watermark text.")
            return
        }
        runProcess(
            message = "Adding watermark…",
            outputName = suggestPdfOutputName(source, outputSuffix(state.watermarkText)),
        ) {
            watermarkPdfUseCase(
                source = source,
                options = options,
                password = state.currentPassword.ifBlank { null },
            )
        }
    }

    private fun inspect(source: PdfToolSource) {
        inspectJob?.cancel()
        inspectJob = viewModelScope.launch {
            when (val result = inspectPdfUseCase(source, _uiState.value.currentPassword.ifBlank { null })) {
                is ScanlyResult.Success -> _uiState.update {
                    it.copy(info = result.value, needsPassword = false)
                }
                is ScanlyResult.Failure -> {
                    val needsPassword = result.error.message.contains("password", ignoreCase = true)
                    _uiState.update { it.copy(info = null, needsPassword = needsPassword) }
                    if (!needsPassword) emitMessage(result.error.message)
                }
            }
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
            if (debounce) delay(PreviewDebounceMs)
            val state = _uiState.value
            if (state.result != null) return@launch
            val options = state.watermarkOptions()
            _uiState.update { it.copy(isWatermarkPreviewLoading = true) }
            val password = state.currentPassword.ifBlank { null }
            val stamped = if (options.text.isBlank()) {
                null
            } else {
                renderWatermarkPreviewUseCase(source, options, password, PreviewWidth)
            }
            val stampError = (stamped as? ScanlyResult.Failure)?.error?.message
                ?.takeUnless { it.contains("password", ignoreCase = true) }
            // Blank or undrawable text: still show the clean page so the editor never goes empty.
            val result = if (stamped is ScanlyResult.Success) {
                stamped
            } else {
                renderPdfPageUseCase(source = source, pageIndex = 0, password = password, maxWidth = PreviewWidth)
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
                            watermarkPreviewError = stampError,
                        )
                    }
                    if (previous !== result.value) previous?.recycle()
                }
                is ScanlyResult.Failure -> _uiState.update {
                    it.copy(isWatermarkPreviewLoading = false, watermarkPreviewError = stampError)
                }
            }
        }
    }

    private fun recyclePreview() {
        previewJob?.cancel()
        previewRequestId += 1
        val previous = _uiState.value.watermarkPreview
        _uiState.update {
            it.copy(watermarkPreview = null, isWatermarkPreviewLoading = false, watermarkPreviewError = null)
        }
        previous?.recycle()
    }

    override fun onCleared() {
        previewJob?.cancel()
        _uiState.value.watermarkPreview?.recycle()
        super.onCleared()
    }

    private fun PdfToolUiState.watermarkOptions() = WatermarkOptions(
        text = watermarkLabel(),
        opacity = watermarkOpacity,
        angleDegrees = watermarkOrientation.angleDegrees,
        size = watermarkSize,
        layout = watermarkLayout,
        pageRange = watermarkPageRange,
        color = watermarkColor,
    )

    private fun outputSuffix(text: String): String = when {
        text.contains("kyc", ignoreCase = true) -> "KYC copy"
        text.equals("draft", ignoreCase = true) -> "draft"
        text.equals("copy", ignoreCase = true) -> "copy"
        else -> "watermarked"
    }

    private companion object {
        const val PreviewWidth = 900
        const val PreviewDebounceMs = 220L
        const val MaxTextLength = 40
        const val MinOpacity = 0.10f
        const val MaxOpacity = 0.60f
    }
}
