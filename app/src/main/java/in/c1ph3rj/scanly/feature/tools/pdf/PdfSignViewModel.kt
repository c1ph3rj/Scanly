package `in`.c1ph3rj.scanly.feature.tools.pdf

import android.graphics.Bitmap
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PdfSignSession
import `in`.c1ph3rj.scanly.domain.model.PdfToolSource
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveSignaturesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecordPositiveMomentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SaveExportArtifactUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.ClosePdfSignSessionUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.OpenPdfSignSessionUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.RenderPdfPageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.pdftools.SignPdfUseCase
import `in`.c1ph3rj.scanly.feature.sign.editor.SignEditorAction
import `in`.c1ph3rj.scanly.feature.sign.editor.SignEditorController
import `in`.c1ph3rj.scanly.feature.sign.editor.SignEditorPage
import `in`.c1ph3rj.scanly.feature.sign.editor.SignEditorUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import kotlin.math.abs

/**
 * State and actions for the Sign PDF tool.
 *
 * The chosen PDF is opened once as a [PdfSignSession]. Pages are rendered lazily for the shared
 * sign editor, and [run] writes the signed copy from the editor's marks. The editor itself is
 * owned here through [editor]; the screen only forwards its actions.
 */
@HiltViewModel
class PdfSignViewModel @Inject constructor(
    observeDocumentsUseCase: ObserveDocumentsUseCase,
    saveExportArtifactUseCase: SaveExportArtifactUseCase,
    private val renderPdfPageUseCase: RenderPdfPageUseCase,
    recordPositiveMomentUseCase: RecordPositiveMomentUseCase,
    observeSignaturesUseCase: ObserveSignaturesUseCase,
    private val openPdfSignSessionUseCase: OpenPdfSignSessionUseCase,
    private val signPdfUseCase: SignPdfUseCase,
    private val closePdfSignSessionUseCase: ClosePdfSignSessionUseCase,
) : BasePdfToolViewModel(
    observeDocumentsUseCase,
    saveExportArtifactUseCase,
    renderPdfPageUseCase,
    recordPositiveMomentUseCase,
) {
    /** Owns the pages, marks and selection shown by the shared sign editor. */
    val editor = SignEditorController()

    /** Editor state the shared sign editor draws. */
    val editorState: StateFlow<SignEditorUiState> = editor.state

    private val _pageBitmaps = MutableStateFlow<Map<Int, Bitmap>>(emptyMap())

    /** Rendered page bitmaps keyed by page index. Only pages near the current page are kept. */
    val pageBitmaps: StateFlow<Map<Int, Bitmap>> = _pageBitmaps.asStateFlow()

    private val _session = MutableStateFlow<PdfSignSession?>(null)

    /** True once the chosen PDF is open and its pages are known to the editor. */
    val isSessionReady: StateFlow<Boolean> = _session
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _isOpening = MutableStateFlow(false)

    /** True while the chosen PDF is being opened, including after an unlock attempt. */
    val isOpening: StateFlow<Boolean> = _isOpening.asStateFlow()

    private val pagesInFlight = mutableSetOf<Int>()
    private var sessionGeneration = 0
    private var openJob: Job? = null

    init {
        viewModelScope.launch {
            observeSignaturesUseCase().collect { editor.setSignatures(it) }
        }
    }

    /**
     * Replaces the chosen PDF. Any previous session is closed and its bitmaps are recycled
     * before the new one is opened.
     */
    override fun setSources(sources: List<PdfToolSource>) {
        releaseSession()
        recycleResultPreview()
        super.setSources(sources)
        _uiState.update {
            it.copy(needsPassword = false, currentPassword = "", outputName = "")
        }
        val source = sources.firstOrNull() ?: return
        openSession(source)
    }

    /** Updates the password typed for a locked PDF. */
    fun setCurrentPassword(value: String) {
        _uiState.update { it.copy(currentPassword = value) }
    }

    /** Retries opening a locked PDF with the typed password. */
    fun unlock() {
        val source = _uiState.value.sources.firstOrNull() ?: return
        openSession(source)
    }

    /**
     * Ensures page [index] is rendered for the editor. Pages already rendered or being rendered
     * are skipped, and pages far from the current page are evicted after the new page arrives.
     */
    fun requestPage(index: Int) {
        val session = _session.value ?: return
        if (index !in session.pageSizes.indices) return
        if (_pageBitmaps.value.containsKey(index) || !pagesInFlight.add(index)) return
        val generation = sessionGeneration
        viewModelScope.launch {
            val rendered = renderPdfPageUseCase(
                source = session.file,
                pageIndex = index,
                password = null,
                maxWidth = PageRenderWidth,
            )
            if (generation != sessionGeneration) {
                // The session was replaced while rendering; this bitmap no longer has a home.
                if (rendered is ScanlyResult.Success) rendered.value.recycle()
                return@launch
            }
            pagesInFlight.remove(index)
            when (rendered) {
                is ScanlyResult.Success -> storePage(index, rendered.value)
                is ScanlyResult.Failure -> emitMessage(rendered.error.message)
            }
        }
    }

    /** Forwards a user intent from the sign editor. */
    fun onEditorAction(action: SignEditorAction) {
        editor.onAction(action)
    }

    /**
     * Writes the signed copy using the editor's current marks. Does nothing useful without a
     * mark, so the user is asked to place one first.
     */
    fun run() {
        val session = _session.value
        if (session == null) {
            emitMessage("Open a PDF first.")
            return
        }
        val marks = editor.marks()
        if (marks.isEmpty()) {
            emitMessage("Place a signature first.")
            return
        }
        val source = _uiState.value.sources.firstOrNull()
        runProcess(
            message = "Signing…",
            outputName = suggestPdfOutputName(source, "signed"),
        ) {
            signPdfUseCase(session, marks)
        }
    }

    /**
     * Leaves the Done screen to adjust signatures. Marks are kept so the user can change them.
     */
    fun editAgain() {
        clearResult()
    }

    /** Opens [source] as a sign session, or asks for a password when the PDF is locked. */
    private fun openSession(source: PdfToolSource) {
        openJob?.cancel()
        openJob = viewModelScope.launch {
            _isOpening.value = true
            val password = _uiState.value.currentPassword.ifBlank { null }
            val result = openPdfSignSessionUseCase(source, password)
            if (!isActive) {
                if (result is ScanlyResult.Success) closeSession(result.value)
                return@launch
            }
            when (result) {
                is ScanlyResult.Success -> {
                    _session.value = result.value
                    editor.setPages(
                        result.value.pageSizes.mapIndexed { index, size ->
                            SignEditorPage(index = index, aspectRatio = size.aspectRatio)
                        },
                    )
                    editor.setMarks(emptyList())
                    _uiState.update { it.copy(needsPassword = false) }
                }
                is ScanlyResult.Failure -> {
                    val needsPassword = result.error.message.contains("password", ignoreCase = true)
                    if (needsPassword) {
                        _uiState.update { it.copy(needsPassword = true) }
                    } else {
                        // Return to the picker so the message is not hidden behind a spinner.
                        _uiState.update { it.copy(sources = emptyList(), needsPassword = false) }
                        emitMessage(result.error.message)
                    }
                }
            }
            _isOpening.value = false
        }
    }

    /** Closes the current session, invalidates in-flight renders and drops every page bitmap. */
    private fun releaseSession() {
        openJob?.cancel()
        sessionGeneration += 1
        pagesInFlight.clear()
        _session.value?.let { closeSession(it) }
        _session.value = null
        recycleAllPages()
    }

    /** Stores [bitmap] for [index] and evicts pages that are too far from the current page. */
    private fun storePage(index: Int, bitmap: Bitmap) {
        val current = editor.state.value.currentPage
        val pages = _pageBitmaps.value.toMutableMap()
        pages[index] = bitmap
        // Far pages are dropped, not recycled: the UI may still draw them for a frame.
        pages.keys
            .filter { it != index && abs(it - current) > KeptPageRadius }
            .forEach { pages.remove(it) }
        _pageBitmaps.value = pages
    }

    private fun recycleAllPages() {
        _pageBitmaps.value = emptyMap()
    }

    /** Closes the session off the main thread, even if the ViewModel is being cleared. */
    private fun closeSession(session: PdfSignSession) {
        CoroutineScope(Dispatchers.IO + NonCancellable).launch {
            closePdfSignSessionUseCase(session)
        }
    }

    override fun onCleared() {
        openJob?.cancel()
        sessionGeneration += 1
        pagesInFlight.clear()
        _session.value?.let { closeSession(it) }
        _session.value = null
        recycleAllPages()
        super.onCleared()
    }

    private companion object {
        /** Width in pixels that pages are rendered at for the editor. */
        const val PageRenderWidth = 1400

        /** Pages further than this from the current page are dropped from memory. */
        const val KeptPageRadius = 2
    }
}
