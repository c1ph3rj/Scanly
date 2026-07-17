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
import `in`.c1ph3rj.scanly.domain.usecase.ImportImagesUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SuggestDocumentTitleUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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

@HiltViewModel
class LaunchActionViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    private val createDocumentUseCase: CreateDocumentUseCase,
    private val suggestDocumentTitleUseCase: SuggestDocumentTitleUseCase,
    private val importImagesUseCase: ImportImagesUseCase,
) : ViewModel() {

    private val _importProgress = MutableStateFlow(LaunchImportProgress())
    val importProgress: StateFlow<LaunchImportProgress> = _importProgress.asStateFlow()

    private val _events = MutableSharedFlow<LaunchActionEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<LaunchActionEvent> = _events.asSharedFlow()

    private var appReady: Boolean = false
    private var pendingAction: ScanlyLaunchAction? =
        savedStateHandle.get<String>(KEY_PENDING_ACTION)?.let { name ->
            runCatching { ScanlyLaunchAction.valueOf(name) }.getOrNull()
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
    }

    fun importImagesAsDocument(imageUris: List<Uri>) {
        if (imageUris.isEmpty() || _importProgress.value.active) return

        val cappedSelection = ImageImportSupport.capSelection(imageUris)
        val total = cappedSelection.items.size

        viewModelScope.launch {
            _importProgress.value = LaunchImportProgress(
                active = true,
                current = 0,
                total = total,
                stageLabel = "Starting import",
            )
            try {
                when (val createResult = createDocumentUseCase.createImported()) {
                    is ScanlyResult.Success -> {
                        when (
                            val importResult = importImagesUseCase(
                                documentId = createResult.value,
                                imageUris = cappedSelection.items,
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
                                _events.emit(LaunchActionEvent.OpenDocument(createResult.value))
                                _events.emit(
                                    LaunchActionEvent.ShowMessage(
                                        ImageImportSupport.importResultMessage(
                                            importedCount = cappedSelection.items.size,
                                            truncated = cappedSelection.truncated,
                                        ),
                                    ),
                                )
                            }

                            is ScanlyResult.Failure -> {
                                _events.emit(LaunchActionEvent.ShowMessage(importResult.error.message))
                            }
                        }
                    }

                    is ScanlyResult.Failure -> {
                        _events.emit(LaunchActionEvent.ShowMessage(createResult.error.message))
                    }
                }
            } finally {
                _importProgress.update { LaunchImportProgress() }
            }
        }
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
        val title = suggestDocumentTitleUseCase()
        when (val result = createDocumentUseCase(title)) {
            is ScanlyResult.Success ->
                _events.emit(LaunchActionEvent.OpenScanSession(result.value))
            is ScanlyResult.Failure ->
                _events.emit(LaunchActionEvent.ShowMessage(result.error.message))
        }
    }

    private companion object {
        const val KEY_COLD_START_HANDLED = "launch_cold_start_handled"
        const val KEY_PENDING_ACTION = "launch_pending_action"
    }
}
