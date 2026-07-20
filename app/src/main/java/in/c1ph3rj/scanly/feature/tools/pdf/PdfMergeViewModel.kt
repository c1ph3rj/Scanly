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
