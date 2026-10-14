package `in`.c1ph3rj.scanly.feature.tools.pdf

import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveExportArtifactUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.MergePdfsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import kotlinx.coroutines.flow.update
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class PdfMergeViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    private val mergePdfsUseCase: MergePdfsUseCase,
    saveExportArtifactUseCase: SaveExportArtifactUseCase,
    renderPdfPageUseCase: RenderPdfPageUseCase,
) : BasePdfToolViewModel(observeDocumentsUseCase, saveExportArtifactUseCase, renderPdfPageUseCase) {

    /** Picker tap: appends the library document to the merge order, or removes it if already picked. */
    fun toggleLibraryDocument(document: ScanDocument) {
        _uiState.update { state ->
            val index = state.sources.indexOfFirst {
                it is PdfToolSource.LibraryDocument && it.documentId == document.id
            }
            val next = if (index >= 0) {
                state.sources.filterIndexed { i, _ -> i != index }
            } else {
                state.sources + PdfToolSource.LibraryDocument(document.id, document.title)
            }
            state.copy(sources = next, result = null)
        }
    }

    /** Appends files picked from phone storage, skipping any that are already in the order. */
    fun addDeviceSources(sources: List<PdfToolSource.DeviceUri>) {
        _uiState.update { state ->
            val known = state.sources
                .filterIsInstance<PdfToolSource.DeviceUri>()
                .map { it.uriString }
                .toSet()
            val fresh = sources
                .filter { it.uriString !in known }
                .distinctBy { it.uriString }
            state.copy(sources = state.sources + fresh, result = null)
        }
    }

    fun removeSource(index: Int) {
        _uiState.update {
            val next = it.sources.toMutableList()
            if (index in next.indices) next.removeAt(index)
            it.copy(sources = next, result = null)
        }
    }

    /** Moves the file at index [from] so that it ends up at index [to]. */
    fun moveSource(from: Int, to: Int) {
        _uiState.update { state ->
            val list = state.sources
            if (from !in list.indices || to !in list.indices || from == to) return@update state
            val next = list.toMutableList().apply { add(to, removeAt(from)) }
            state.copy(sources = next, result = null)
        }
    }

    /** Seeds the editable output name the first time the user reaches the Order step. */
    fun prepareOrder() {
        _uiState.update { state ->
            if (state.outputName.isBlank()) state.copy(outputName = defaultMergeName()) else state
        }
    }

    fun runMerge() {
        val state = _uiState.value
        val sources = state.sources
        if (sources.size < 2) {
            emitMessage("Add at least two PDFs to merge.")
            return
        }
        val name = sanitizePdfBaseName(state.outputName).ifBlank { defaultMergeName() }
        runProcess("Merging…", outputName = name) { mergePdfsUseCase(sources) }
    }

    private fun defaultMergeName(): String =
        "Merged PDF – " + SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date())
}
