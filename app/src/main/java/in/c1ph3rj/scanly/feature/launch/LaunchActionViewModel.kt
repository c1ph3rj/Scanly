package `in`.c1ph3rj.scanly.feature.launch

import android.content.Intent
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.ui.ImageImportSupport
import `in`.c1ph3rj.scanly.domain.usecase.CreateDocumentUseCase
import `in`.c1ph3rj.scanly.domain.model.ScanDocument
import `in`.c1ph3rj.scanly.domain.usecase.DeleteDocumentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DiscardSharedImagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ImportImagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.StageSharedImagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.StagedImageShare
import `in`.c1ph3rj.scanly.domain.usecase.SuggestDocumentTitleUseCase
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString

data class LaunchImportProgress(
    val active: Boolean = false,
    val current: Int = 0,
    val total: Int = 0,
    val stageLabel: String = "",
)

sealed interface LaunchActionEvent {
    data class OpenScanSession(val documentId: String) : LaunchActionEvent
    data class OpenDocument(val documentId: String) : LaunchActionEvent
    data object OpenLibrary : LaunchActionEvent
    data object OpenQr : LaunchActionEvent
    data object RequestImportPicker : LaunchActionEvent
    data class ShowMessage(val message: String) : LaunchActionEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LaunchActionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val suggestDocumentTitleUseCase: SuggestDocumentTitleUseCase,
    private val importImagesUseCase: ImportImagesUseCase,
    private val deleteDocumentUseCase: DeleteDocumentUseCase,
    private val stageSharedImagesUseCase: StageSharedImagesUseCase,
    private val discardSharedImagesUseCase: DiscardSharedImagesUseCase,
    observeDocumentsUseCase: ObserveDocumentsUseCase,
) : ViewModel() {

    private val _importProgress = MutableStateFlow(LaunchImportProgress())
    val importProgress: StateFlow<LaunchImportProgress> = _importProgress.asStateFlow()

    private val _events = MutableSharedFlow<LaunchActionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LaunchActionEvent> = _events.asSharedFlow()

    private val _sharedImages = MutableStateFlow(restoreSharedImages())

    /** Images shared from another app, waiting for the user to pick New document or Add pages. */
    val sharedImages: StateFlow<StagedImageShare?> = _sharedImages.asStateFlow()

    /** Documents that can take the shared images as extra pages, most recently changed first. */
    val shareTargets: StateFlow<List<ScanDocument>> = _sharedImages
        .map { it != null }
        .distinctUntilChanged()
        .flatMapLatest { waiting -> if (waiting) observeDocumentsUseCase() else flowOf(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Serializes staging and cleanup so a cleanup never deletes a batch being copied. */
    private val shareMutex = Mutex()
    private var stagingShares = 0

    /** Messages raised before the app is ready to show them (e.g. during onboarding). */
    private val pendingMessages = mutableListOf<String>()

    private var appReady: Boolean = false
    private var pendingAction: ScanlyLaunchAction? =
        savedStateHandle.get<String>(KEY_PENDING_ACTION)?.let { name ->
            runCatching { ScanlyLaunchAction.valueOf(name) }.getOrNull()
        }

    init {
        viewModelScope.launch {
            shareMutex.withLock { discardSharedImagesUseCase.clearAllExcept(_sharedImages.value?.batchId) }
        }
    }

    fun onColdStartIntent(intent: Intent?) {
        if (savedStateHandle.get<Boolean>(KEY_COLD_START_HANDLED) == true) return
        savedStateHandle[KEY_COLD_START_HANDLED] = true
        enqueue(ScanlyLaunchAction.fromIntent(intent) ?: return)
    }

    fun onNewIntent(intent: Intent?) {
        enqueue(ScanlyLaunchAction.fromIntent(intent) ?: return)
    }

    fun onAppReady() {
        if (appReady) {
            flushPending()
            return
        }
        appReady = true
        flushPending()
        flushMessages()
    }

    /** True while shared images are being copied or wait for a destination. */
    fun hasIncomingShare(): Boolean = stagingShares > 0 || _sharedImages.value != null

    /**
     * Images shared from another app. They are copied into the cache now (the sender's grant
     * doesn't outlive our task) and the destination sheet appears once the app is ready.
     */
    fun onSharedImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val cappedSelection = ImageImportSupport.capSelection(uris)
        stagingShares++
        viewModelScope.launch {
            try {
                val staged = shareMutex.withLock {
                    // A newer share replaces one that is still waiting for a destination.
                    _sharedImages.value?.let { previous ->
                        setSharedImages(null)
                        discardSharedImagesUseCase(previous.batchId)
                    }
                    stageSharedImagesUseCase(cappedSelection.items)
                }
                if (staged == null) {
                    showMessage(
                        if (cappedSelection.items.size == 1) {
                            appString(R.string.import_images_use_case_the_selected_file_couldn_t)
                        } else {
                            appString(R.string.import_images_use_case_none_of_the_selected_files)
                        },
                    )
                } else {
                    setSharedImages(staged.copy(truncated = cappedSelection.truncated))
                }
            } finally {
                stagingShares--
            }
        }
    }

    /**
     * Imports the shared images into [targetDocumentId], or into a new document when it is null,
     * through the same pipeline as the in-app picker, then opens the document.
     */
    fun importSharedImages(targetDocumentId: String?) {
        if (_importProgress.value.active) return
        val share = _sharedImages.value ?: return
        setSharedImages(null)
        runImport(
            targetDocumentId = targetDocumentId,
            imageUris = share.imagePaths.map { Uri.fromFile(File(it)) },
            truncated = share.truncated,
            extraSkipped = share.unreadableCount,
            onFinished = {
                shareMutex.withLock { discardSharedImagesUseCase(share.batchId) }
            },
        )
    }

    /** The user closed the destination sheet: drop the copies. */
    fun dismissSharedImages() {
        val share = _sharedImages.value ?: return
        setSharedImages(null)
        viewModelScope.launch {
            shareMutex.withLock { discardSharedImagesUseCase(share.batchId) }
        }
    }

    fun importImagesAsDocument(imageUris: List<Uri>) {
        if (imageUris.isEmpty() || _importProgress.value.active) return
        val cappedSelection = ImageImportSupport.capSelection(imageUris)
        runImport(
            targetDocumentId = null,
            imageUris = cappedSelection.items,
            truncated = cappedSelection.truncated,
        )
    }

    /**
     * Runs [ImportImagesUseCase] with the launch progress overlay. With no [targetDocumentId] a new
     * "Imported" document is created first (and removed again if no page could be saved).
     *
     * @param extraSkipped Items already dropped before the import, counted in the result message.
     */
    private fun runImport(
        targetDocumentId: String?,
        imageUris: List<Uri>,
        truncated: Boolean,
        extraSkipped: Int = 0,
        onFinished: suspend () -> Unit = {},
    ) {
        if (imageUris.isEmpty()) {
            viewModelScope.launch { onFinished() }
            return
        }
        val total = imageUris.size
        // Marked active before the coroutine starts so a second tap can't start another import.
        _importProgress.value = LaunchImportProgress(
            active = true,
            current = 0,
            total = total,
            stageLabel = appString(R.string.document_detail_starting_import),
        )

        viewModelScope.launch {
            try {
                val documentId = targetDocumentId ?: when (val createResult = createDocumentUseCase.createImported()) {
                    is ScanlyResult.Success -> createResult.value
                    is ScanlyResult.Failure -> {
                        _events.emit(LaunchActionEvent.ShowMessage(createResult.error.message))
                        return@launch
                    }
                }
                when (
                    val importResult = importImagesUseCase(
                        documentId = documentId,
                        imageUris = imageUris,
                        onProgress = { progress ->
                            _importProgress.value = LaunchImportProgress(
                                active = true,
                                current = progress.currentIndex,
                                total = progress.totalCount,
                                stageLabel = progress.stageLabel,
                            )
                        },
                    )
                ) {
                    is ScanlyResult.Success -> {
                        _events.emit(LaunchActionEvent.OpenDocument(documentId))
                        _events.emit(
                            LaunchActionEvent.ShowMessage(
                                ImageImportSupport.importResultMessage(
                                    importedCount = importResult.value.importedCount,
                                    truncated = truncated,
                                    skippedCount = importResult.value.skippedCount + extraSkipped,
                                ),
                            ),
                        )
                    }

                    is ScanlyResult.Failure -> {
                        // No page was saved, so drop the empty "Imported" document we just made.
                        if (targetDocumentId == null) deleteDocumentUseCase(documentId)
                        _events.emit(LaunchActionEvent.ShowMessage(importResult.error.message))
                    }
                }
            } finally {
                _importProgress.update { LaunchImportProgress() }
                onFinished()
            }
        }
    }

    /** In-app Scan (bottom bar): same as the launcher shortcut — new document, straight to camera. */
    fun requestScan() {
        enqueue(ScanlyLaunchAction.Scan)
    }

    /** First-run CTA must not replace a shortcut already waiting behind onboarding. */
    fun requestOnboardingScan() {
        if (pendingAction == null && !hasIncomingShare()) enqueue(ScanlyLaunchAction.Scan)
    }

    /** In-app Import (bottom bar): same as the launcher shortcut — opens the photo picker. */
    fun requestImport() {
        enqueue(ScanlyLaunchAction.Import)
    }

    private fun enqueue(action: ScanlyLaunchAction) {
        if (!appReady) {
            pendingAction = action
            savedStateHandle[KEY_PENDING_ACTION] = action.name
            return
        }
        execute(action)
    }

    private fun flushPending() {
        val action = pendingAction ?: return
        pendingAction = null
        savedStateHandle.remove<String>(KEY_PENDING_ACTION)
        execute(action)
    }

    private suspend fun showMessage(message: String) {
        if (appReady) {
            _events.emit(LaunchActionEvent.ShowMessage(message))
        } else {
            pendingMessages += message
        }
    }

    private fun flushMessages() {
        if (pendingMessages.isEmpty()) return
        val messages = pendingMessages.toList()
        pendingMessages.clear()
        viewModelScope.launch { messages.forEach { _events.emit(LaunchActionEvent.ShowMessage(it)) } }
    }

    private fun setSharedImages(share: StagedImageShare?) {
        _sharedImages.value = share
        if (share == null) {
            savedStateHandle.remove<String>(KEY_SHARE_BATCH)
            savedStateHandle.remove<ArrayList<String>>(KEY_SHARE_PATHS)
            savedStateHandle.remove<Boolean>(KEY_SHARE_TRUNCATED)
            savedStateHandle.remove<Int>(KEY_SHARE_UNREADABLE)
        } else {
            savedStateHandle[KEY_SHARE_BATCH] = share.batchId
            savedStateHandle[KEY_SHARE_PATHS] = ArrayList(share.imagePaths)
            savedStateHandle[KEY_SHARE_TRUNCATED] = share.truncated
            savedStateHandle[KEY_SHARE_UNREADABLE] = share.unreadableCount
        }
    }

    private fun restoreSharedImages(): StagedImageShare? {
        val batchId = savedStateHandle.get<String>(KEY_SHARE_BATCH) ?: return null
        val paths = savedStateHandle.get<ArrayList<String>>(KEY_SHARE_PATHS)
            ?.filter { File(it).isFile }
            .orEmpty()
        if (paths.isEmpty()) return null
        return StagedImageShare(
            batchId = batchId,
            imagePaths = paths,
            truncated = savedStateHandle.get<Boolean>(KEY_SHARE_TRUNCATED) ?: false,
            unreadableCount = savedStateHandle.get<Int>(KEY_SHARE_UNREADABLE) ?: 0,
        )
    }

    private fun execute(action: ScanlyLaunchAction) {
        viewModelScope.launch {
            when (action) {
                ScanlyLaunchAction.Scan -> startScan()
                ScanlyLaunchAction.Import -> _events.emit(LaunchActionEvent.RequestImportPicker)
                ScanlyLaunchAction.Qr -> _events.emit(LaunchActionEvent.OpenQr)
                ScanlyLaunchAction.Library -> _events.emit(LaunchActionEvent.OpenLibrary)
            }
        }
    }

    private suspend fun startScan() {
        // A fast double tap would otherwise create two documents and stack two camera sessions.
        if (isStartingScan) return
        isStartingScan = true
        try {
            val title = suggestDocumentTitleUseCase()
            when (val result = createDocumentUseCase(title)) {
                is ScanlyResult.Success ->
                    _events.emit(LaunchActionEvent.OpenScanSession(result.value))
                is ScanlyResult.Failure ->
                    _events.emit(LaunchActionEvent.ShowMessage(result.error.message))
            }
        } finally {
            isStartingScan = false
        }
    }

    private var isStartingScan = false

    private companion object {
        const val KEY_COLD_START_HANDLED = "launch_cold_start_handled"
        const val KEY_PENDING_ACTION = "launch_pending_action"
        const val KEY_SHARE_BATCH = "launch_share_batch"
        const val KEY_SHARE_PATHS = "launch_share_paths"
        const val KEY_SHARE_TRUNCATED = "launch_share_truncated"
        const val KEY_SHARE_UNREADABLE = "launch_share_unreadable"
    }
}
