package `in`.c1ph3rj.scanly.feature.tools.qr

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.QrHistoryEntry
import `in`.c1ph3rj.scanly.domain.model.QrOrigin
import `in`.c1ph3rj.scanly.domain.usecase.SaveExportArtifactUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.AddQrHistoryEntryUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.ClearQrHistoryUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.DeleteQrHistoryEntryUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.GenerateQrBitmapUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.ObserveQrHistoryUseCase
import `in`.c1ph3rj.scanly.domain.usecase.qr.SaveQrPngUseCase
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
import javax.inject.Inject

/** Nav seed for the route: `scan` opens the scanner, `generate` opens Create. */
enum class QrToolMode {
    Scan,
    Generate,
}

/** One screen of the QR tool. Scan, Create and History are tabs; Result sits above them. */
sealed interface QrScreen {
    data object Scan : QrScreen
    data object Create : QrScreen
    data object History : QrScreen

    /** [returnTo] is the screen Back goes to: Scan or History. */
    data class Result(val item: QrResultItem, val returnTo: QrScreen) : QrScreen
}

/** A code shown on the result screen, whether it was just scanned or came from history. */
data class QrResultItem(
    val id: String?,
    val raw: String,
    val origin: QrOrigin,
    val createdAtMillis: Long,
    val capture: QrCapturePreview? = null,
)

data class QrCapturePreview(val bitmap: Bitmap, val corners: List<Offset>)

data class QrToolUiState(
    val screen: QrScreen = QrScreen.Scan,
    /** Code found by the camera or a photo; shown as the card over the viewfinder. */
    val found: QrResultItem? = null,
    val isReadingPhoto: Boolean = false,
    val draft: QrCreateDraft = QrCreateDraft(),
    val previewBitmap: Bitmap? = null,
    val isSaving: Boolean = false,
    val savedArtifact: ExportArtifact? = null,
    /** Inputs [savedArtifact] was rendered from; a changed draft renders a new file. */
    val savedArtifactKey: String? = null,
    val historyQuery: String = "",
    val historyFilter: QrHistoryFilter = QrHistoryFilter.All,
)

sealed interface QrToolEvent {
    data class ShowMessage(val message: String) : QrToolEvent
}

@HiltViewModel
class QrToolViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val generateQrBitmapUseCase: GenerateQrBitmapUseCase,
    private val saveQrPngUseCase: SaveQrPngUseCase,
    private val saveExportArtifactUseCase: SaveExportArtifactUseCase,
    observeQrHistoryUseCase: ObserveQrHistoryUseCase,
    private val addQrHistoryEntryUseCase: AddQrHistoryEntryUseCase,
    private val deleteQrHistoryEntryUseCase: DeleteQrHistoryEntryUseCase,
    private val clearQrHistoryUseCase: ClearQrHistoryUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(QrToolUiState())
    val uiState: StateFlow<QrToolUiState> = _uiState.asStateFlow()

    /** Newest first. Shared by the History screen and kept warm only while it is collected. */
    val history: StateFlow<List<QrHistoryEntry>> = observeQrHistoryUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _events = MutableSharedFlow<QrToolEvent>()
    val events: SharedFlow<QrToolEvent> = _events.asSharedFlow()

    private var previewJob: Job? = null
    private var photoJob: Job? = null

    private fun cancelPhoto() {
        photoJob?.cancel()
        _uiState.update { it.copy(isReadingPhoto = false) }
    }

    /**
     * Nav route seed. Applied once per ViewModel so a rotation does not undo a tab the user
     * already switched to.
     */
    private var initialModeApplied = false

    fun applyInitialMode(mode: QrToolMode) {
        if (initialModeApplied) return
        initialModeApplied = true
        if (mode == QrToolMode.Generate) {
            _uiState.update { it.copy(screen = QrScreen.Create) }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Navigation
    // -----------------------------------------------------------------------------------------

    fun showScanner() {
        _uiState.update { it.copy(screen = QrScreen.Scan) }
    }

    fun showCreate() {
        cancelPhoto()
        _uiState.update { it.copy(screen = QrScreen.Create, found = null) }
    }

    fun showHistory() {
        cancelPhoto()
        _uiState.update { it.copy(screen = QrScreen.History, found = null) }
    }

    fun openFoundResult() {
        _uiState.update { state ->
            val found = state.found ?: return@update state
            state.copy(screen = QrScreen.Result(found, returnTo = QrScreen.Scan))
        }
    }

    fun openHistoryEntry(entry: QrHistoryEntry) {
        val item = QrResultItem(
            id = entry.id,
            raw = entry.raw,
            origin = entry.origin,
            createdAtMillis = entry.createdAtMillis,
        )
        _uiState.update { it.copy(screen = QrScreen.Result(item, returnTo = QrScreen.History)) }
    }

    /** Back from Result returns to where it was opened from; back from History goes to Scan. */
    fun navigateBack() {
        _uiState.update { state ->
            when (val screen = state.screen) {
                is QrScreen.Result -> state.copy(screen = screen.returnTo)
                QrScreen.History -> state.copy(screen = QrScreen.Scan)
                else -> state
            }
        }
    }

    /** Removes the entry shown on the result screen, then returns to the previous screen. */
    fun deleteResultEntry() {
        val result = _uiState.value.screen as? QrScreen.Result ?: return
        val id = result.item.id ?: return
        viewModelScope.launch {
            deleteQrHistoryEntryUseCase(id)
            _uiState.update { state ->
                state.copy(
                    screen = result.returnTo,
                    found = if (result.returnTo == QrScreen.Scan) null else state.found,
                )
            }
        }
    }

    // -----------------------------------------------------------------------------------------
    // Scan
    // -----------------------------------------------------------------------------------------

    /**
     * Records a decoded code once in history and shows it over the camera. The camera stops
     * detecting until [dismissFound], so this is not called again for the same code.
     */
    fun onCodeFound(raw: String, capture: QrCapturePreview? = null) {
        if (raw.isBlank() || _uiState.value.screen != QrScreen.Scan || _uiState.value.found != null) return
        val pending = QrResultItem(null, raw, QrOrigin.Scanned, System.currentTimeMillis(), capture)
        _uiState.update { it.copy(found = pending) }
        viewModelScope.launch {
            val entry = runCatching { addQrHistoryEntryUseCase(raw, QrOrigin.Scanned) }.getOrNull()
            val item = pending.copy(
                id = entry?.id,
                raw = raw,
                origin = QrOrigin.Scanned,
                createdAtMillis = entry?.createdAtMillis ?: System.currentTimeMillis(),
            )
            _uiState.update { state ->
                val result = state.screen as? QrScreen.Result
                state.copy(
                    found = if (state.found === pending) item else state.found,
                    screen = if (result?.item === pending) result.copy(item = item) else state.screen,
                )
            }
        }
    }

    fun onPhotoPicked(uri: Uri) {
        cancelPhoto()
        _uiState.update { it.copy(found = null, isReadingPhoto = true) }
        photoJob = viewModelScope.launch {
            val raw = decodeQrFromPhoto(appContext, uri)
            _uiState.update { it.copy(isReadingPhoto = false) }
            if (raw == null) {
                emitMessage("No QR code found in that photo")
            } else {
                onCodeFound(raw)
            }
        }
    }

    fun dismissFound() {
        cancelPhoto()
        _uiState.update { it.copy(found = null) }
    }

    // -----------------------------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------------------------

    /** Applies [transform] to the draft and redraws the preview after a short pause. */
    fun updateDraft(transform: (QrCreateDraft) -> QrCreateDraft) {
        _uiState.update { state -> state.copy(draft = transform(state.draft)) }
        schedulePreview()
    }

    private fun schedulePreview() {
        previewJob?.cancel()
        val draft = _uiState.value.draft
        val payload = qrCreatePayload(draft)
        previewJob = viewModelScope.launch {
            delay(PREVIEW_DEBOUNCE_MS)
            if (payload == null) {
                replacePreview(null)
                return@launch
            }
            when (val result = generateQrBitmapUseCase(payload, inkArgb = draft.ink.argb)) {
                is ScanlyResult.Success -> replacePreview(result.value)
                // The old code no longer matches the draft, so it must not stay on screen to export.
                is ScanlyResult.Failure -> replacePreview(null)
            }
        }
    }

    /**
     * Swaps in [bitmap]. The old one is left to the GC, not recycled: Compose may still draw the
     * previous state for a frame after the swap, and drawing a recycled bitmap crashes.
     */
    private fun replacePreview(bitmap: Bitmap?) {
        _uiState.update { it.copy(previewBitmap = bitmap) }
    }

    fun saveCreatedCode() {
        val draft = _uiState.value.draft
        val payload = qrCreatePayload(draft) ?: run {
            emitMessage("Add the details first")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val rendered = renderCreatedCode(draft, payload)) {
                is ScanlyResult.Success -> {
                    runCatching { addQrHistoryEntryUseCase(payload, QrOrigin.Created) }
                    when (val exported = saveExportArtifactUseCase(rendered.value)) {
                        is ScanlyResult.Success -> emitMessage(
                            "Saved ${exported.value.fileName} to ${exported.value.destinationLabel}",
                        )
                        is ScanlyResult.Failure -> emitMessage(exported.error.message)
                    }
                }
                is ScanlyResult.Failure -> emitMessage(rendered.error.message)
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    /** Renders the PNG and hands it to [onReady] (the caller opens the share sheet). */
    fun shareCreatedCode(onReady: (ExportArtifact) -> Unit) {
        val draft = _uiState.value.draft
        val payload = qrCreatePayload(draft) ?: run {
            emitMessage("Add the details first")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            when (val rendered = renderCreatedCode(draft, payload)) {
                is ScanlyResult.Success -> {
                    runCatching { addQrHistoryEntryUseCase(payload, QrOrigin.Created) }
                    onReady(rendered.value)
                }
                is ScanlyResult.Failure -> emitMessage(rendered.error.message)
            }
            _uiState.update { it.copy(isSaving = false) }
        }
    }

    /** Reuses the last PNG when nothing that changes the image has changed. */
    private suspend fun renderCreatedCode(
        draft: QrCreateDraft,
        payload: String,
    ): ScanlyResult<ExportArtifact> {
        val caption = draft.caption.trim().ifEmpty { null }
        val key = listOf(payload, draft.ink.argb.toString(), caption.orEmpty()).joinToString("|")
        val state = _uiState.value
        state.savedArtifact.takeIf { state.savedArtifactKey == key }?.let { cached ->
            return ScanlyResult.Success(cached)
        }

        val result = saveQrPngUseCase(
            content = payload,
            sizePx = PNG_SIZE_PX,
            inkArgb = draft.ink.argb,
            caption = caption,
        )
        if (result is ScanlyResult.Success) {
            _uiState.update { it.copy(savedArtifact = result.value, savedArtifactKey = key) }
        }
        return result
    }

    // -----------------------------------------------------------------------------------------
    // History
    // -----------------------------------------------------------------------------------------

    fun setHistoryQuery(query: String) {
        _uiState.update { it.copy(historyQuery = query) }
    }

    fun setHistoryFilter(filter: QrHistoryFilter) {
        _uiState.update { it.copy(historyFilter = filter) }
    }

    fun clearHistory() {
        viewModelScope.launch {
            clearQrHistoryUseCase()
            emitMessage("QR history cleared")
        }
    }

    // -----------------------------------------------------------------------------------------
    // Messages
    // -----------------------------------------------------------------------------------------

    fun emitMessage(message: String) {
        viewModelScope.launch {
            _events.emit(QrToolEvent.ShowMessage(message))
        }
    }

    private companion object {
        const val PREVIEW_DEBOUNCE_MS = 250L
        const val PNG_SIZE_PX = 1024
    }
}
