package `in`.c1ph3rj.scanly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.StorageFormatter
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.model.SettingsContent
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel
import `in`.c1ph3rj.scanly.core.ml.AutomaticDocumentModelSelector
import `in`.c1ph3rj.scanly.domain.usecase.ObserveLiveDetectionModelUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetLiveDetectionModelUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObservePostProcessingModelUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetPostProcessingModelUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveAutomaticModelSelectionUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetAutomaticModelSelectionUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveDocumentGateEnabledUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetDocumentGateEnabledUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveOcrEngineUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetOcrEngineUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveIndraSuspendedUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObservePagesNotReadWithUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveTextIndexProgressUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ResetIndraUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RereadPagesNotReadWithUseCase
import `in`.c1ph3rj.scanly.domain.model.OcrEngine
import `in`.c1ph3rj.scanly.domain.usecase.ClearAllAppDataUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ClearExportCacheUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeleteEmptyDocumentsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DeleteEmptyGroupsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.GetAppStorageUsageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.LoadSettingsContentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveThemeModeUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObservePureBlackEnabledUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetThemeModeUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetPureBlackEnabledUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveExportDestinationUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetExportDestinationUseCase
import `in`.c1ph3rj.scanly.domain.usecase.MarkAppRatedUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ResetExportDestinationUseCase
import `in`.c1ph3rj.scanly.domain.usecase.DismissSettingsRatingCardUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveRatingPromptStateUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlackEnabled: Boolean = false,
    val content: SettingsContent? = null,
    val isLoading: Boolean = true,
    val storageUsage: AppStorageUsage? = null,
    val isLoadingStorage: Boolean = true,
    val isClearingData: Boolean = false,
    val isClearingExportCache: Boolean = false,
    val isCleaningEmptyDocuments: Boolean = false,
    val isCleaningEmptyFolders: Boolean = false,
    val exportDestination: ExportDestination = ExportDestination.DefaultDownloadsScanly,
    val liveDetectionModel: DocumentCornerModel = DocumentCornerModel.ACCURATE,
    val postProcessingModel: DocumentCornerModel = DocumentCornerModel.ACCURATE,
    val automaticModelSelectionEnabled: Boolean = true,
    val documentGateEnabled: Boolean = true,
    val ocrEngine: OcrEngine = OcrEngine.ML_KIT,
    /** Pages whose text was not produced by [ocrEngine] (read by the other engine, or empty). */
    val pagesNotReadWithSelectedEngine: Int = 0,
    /** Pages still waiting for the background indexer, including ones queued for a re-read. */
    val indexPendingPages: Int = 0,
    val indraSuspended: Boolean = false,
    val showRereadPrompt: Boolean = false,
    val isCalibratingModels: Boolean = false,
    val automaticLiveModel: DocumentCornerModel? = null,
    val automaticPostProcessingModel: DocumentCornerModel? = null,
    val hasRated: Boolean = false,
    val settingsCardDismissed: Boolean = false,
    val ratingPromptStateLoaded: Boolean = false,
) {
    /** The friendly rating card under the Settings header shows until rated or dismissed. */
    val showRatingCard: Boolean
        get() = ratingPromptStateLoaded && !hasRated && !settingsCardDismissed
}

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    observePureBlackEnabledUseCase: ObservePureBlackEnabledUseCase,
    private val setPureBlackEnabledUseCase: SetPureBlackEnabledUseCase,
    private val loadSettingsContentUseCase: LoadSettingsContentUseCase,
    private val getAppStorageUsageUseCase: GetAppStorageUsageUseCase,
    private val clearAllAppDataUseCase: ClearAllAppDataUseCase,
    private val clearExportCacheUseCase: ClearExportCacheUseCase,
    private val deleteEmptyDocumentsUseCase: DeleteEmptyDocumentsUseCase,
    private val deleteEmptyGroupsUseCase: DeleteEmptyGroupsUseCase,
    observeExportDestinationUseCase: ObserveExportDestinationUseCase,
    private val setExportDestinationUseCase: SetExportDestinationUseCase,
    private val resetExportDestinationUseCase: ResetExportDestinationUseCase,
    observeLiveDetectionModelUseCase: ObserveLiveDetectionModelUseCase,
    private val setLiveDetectionModelUseCase: SetLiveDetectionModelUseCase,
    observePostProcessingModelUseCase: ObservePostProcessingModelUseCase,
    private val setPostProcessingModelUseCase: SetPostProcessingModelUseCase,
    observeAutomaticModelSelectionUseCase: ObserveAutomaticModelSelectionUseCase,
    private val setAutomaticModelSelectionUseCase: SetAutomaticModelSelectionUseCase,
    observeDocumentGateEnabledUseCase: ObserveDocumentGateEnabledUseCase,
    private val setDocumentGateEnabledUseCase: SetDocumentGateEnabledUseCase,
    observeOcrEngineUseCase: ObserveOcrEngineUseCase,
    private val setOcrEngineUseCase: SetOcrEngineUseCase,
    observePagesNotReadWithUseCase: ObservePagesNotReadWithUseCase,
    private val rereadPagesNotReadWithUseCase: RereadPagesNotReadWithUseCase,
    observeTextIndexProgressUseCase: ObserveTextIndexProgressUseCase,
    observeIndraSuspendedUseCase: ObserveIndraSuspendedUseCase,
    private val resetIndraUseCase: ResetIndraUseCase,
    private val automaticDocumentModelSelector: AutomaticDocumentModelSelector,
    private val markAppRatedUseCase: MarkAppRatedUseCase,
    observeRatingPromptStateUseCase: ObserveRatingPromptStateUseCase,
    private val dismissSettingsRatingCardUseCase: DismissSettingsRatingCardUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    /** Set by an actual user switch, so the re-read prompt appears then and not on screen open. */
    private var promptAfterSwitchTo: OcrEngine? = null

    init {
        viewModelScope.launch {
            observeLiveDetectionModelUseCase().collectLatest { model ->
                _uiState.update { it.copy(liveDetectionModel = model) }
            }
        }
        viewModelScope.launch {
            observePostProcessingModelUseCase().collectLatest { model ->
                _uiState.update { it.copy(postProcessingModel = model) }
            }
        }
        viewModelScope.launch {
            observeAutomaticModelSelectionUseCase().collectLatest { enabled ->
                _uiState.update {
                    it.copy(
                        automaticModelSelectionEnabled = enabled,
                        isCalibratingModels = enabled,
                        automaticLiveModel = if (enabled) it.automaticLiveModel else null,
                        automaticPostProcessingModel = if (enabled) it.automaticPostProcessingModel else null,
                    )
                }
                if (enabled) {
                    runCatching { automaticDocumentModelSelector.selection() }
                        .onSuccess { selection ->
                            _uiState.update {
                                it.copy(
                                    isCalibratingModels = false,
                                    automaticLiveModel = selection.liveModel,
                                    automaticPostProcessingModel = selection.postProcessingModel,
                                )
                            }
                        }
                        .onFailure {
                            _uiState.update { it.copy(isCalibratingModels = false) }
                            _events.emit(SettingsEvent.ShowMessage("Could not calibrate document models on this device."))
                        }
                }
            }
        }
        viewModelScope.launch {
            observeDocumentGateEnabledUseCase().collectLatest { enabled ->
                _uiState.update { it.copy(documentGateEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            observeOcrEngineUseCase().collectLatest { engine ->
                _uiState.update { it.copy(ocrEngine = engine) }
            }
        }
        viewModelScope.launch {
            observeOcrEngineUseCase()
                .flatMapLatest { engine -> observePagesNotReadWithUseCase(engine).map { engine to it } }
                .collectLatest { (engine, count) ->
                    val prompt = promptAfterSwitchTo == engine
                    if (prompt) promptAfterSwitchTo = null
                    _uiState.update {
                        it.copy(
                            pagesNotReadWithSelectedEngine = count,
                            showRereadPrompt = if (prompt) count > 0 else it.showRereadPrompt && count > 0,
                        )
                    }
                }
        }
        viewModelScope.launch {
            observeTextIndexProgressUseCase().collectLatest { progress ->
                _uiState.update { it.copy(indexPendingPages = progress.pendingPages) }
            }
        }
        viewModelScope.launch {
            observeIndraSuspendedUseCase().collectLatest { suspended ->
                _uiState.update { it.copy(indraSuspended = suspended) }
            }
        }
        viewModelScope.launch {
            observeThemeModeUseCase().collectLatest { themeMode ->
                _uiState.update { current -> current.copy(themeMode = themeMode) }
            }
        }
        viewModelScope.launch {
            observePureBlackEnabledUseCase().collectLatest { enabled ->
                _uiState.update { current -> current.copy(pureBlackEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            observeRatingPromptStateUseCase().collectLatest { state ->
                _uiState.update { current ->
                    current.copy(
                        hasRated = state.hasRated,
                        settingsCardDismissed = state.settingsCardDismissed,
                        ratingPromptStateLoaded = true,
                    )
                }
            }
        }
        viewModelScope.launch {
            observeExportDestinationUseCase().collectLatest { destination ->
                _uiState.update { current -> current.copy(exportDestination = destination) }
            }
        }
        refresh()
        loadStorageUsage()
    }

    fun loadStorageUsage() {
        viewModelScope.launch {
            _uiState.update { current -> current.copy(isLoadingStorage = true) }
            when (val result = getAppStorageUsageUseCase()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            storageUsage = result.value,
                            isLoadingStorage = false,
                        )
                    }
                }

                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isLoadingStorage = false) }
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun clearAllData() {
        if (_uiState.value.isClearingData) {
            return
        }

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isClearingData = true) }
            when (val result = clearAllAppDataUseCase()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    _uiState.update { current -> current.copy(isClearingData = false) }
                    _events.emit(SettingsEvent.ShowMessage("All data cleared."))
                    loadStorageUsage()
                }

                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isClearingData = false) }
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun clearExportCache() {
        if (_uiState.value.isClearingExportCache) return
        val freedBytes = _uiState.value.storageUsage?.exportCacheBytes ?: 0L
        viewModelScope.launch {
            _uiState.update { it.copy(isClearingExportCache = true) }
            when (val result = clearExportCacheUseCase()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    _uiState.update { it.copy(isClearingExportCache = false) }
                    _events.emit(
                        SettingsEvent.ShowMessage("Freed ${StorageFormatter.formatBytes(freedBytes)}"),
                    )
                    loadStorageUsage()
                }

                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isClearingExportCache = false) }
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun deleteEmptyDocuments() {
        if (_uiState.value.isCleaningEmptyDocuments) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaningEmptyDocuments = true) }
            when (val result = deleteEmptyDocumentsUseCase()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    val count = result.value
                    _uiState.update { it.copy(isCleaningEmptyDocuments = false) }
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            when (count) {
                                0 -> "No empty documents found."
                                1 -> "Removed 1 empty document."
                                else -> "Removed $count empty documents."
                            },
                        ),
                    )
                    loadStorageUsage()
                }

                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isCleaningEmptyDocuments = false) }
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun deleteEmptyFolders() {
        if (_uiState.value.isCleaningEmptyFolders) return
        viewModelScope.launch {
            _uiState.update { it.copy(isCleaningEmptyFolders = true) }
            when (val result = deleteEmptyGroupsUseCase()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    val count = result.value
                    _uiState.update { it.copy(isCleaningEmptyFolders = false) }
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            when (count) {
                                0 -> "No empty folders found."
                                1 -> "Removed 1 empty folder."
                                else -> "Removed $count empty folders."
                            },
                        ),
                    )
                    loadStorageUsage()
                }

                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _uiState.update { it.copy(isCleaningEmptyFolders = false) }
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { current -> current.copy(isLoading = true) }
            when (val result = loadSettingsContentUseCase()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    _uiState.update { current ->
                        current.copy(
                            content = result.value,
                            isLoading = false,
                        )
                    }
                }

                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isLoading = false) }
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun setThemeMode(themeMode: ThemeMode) {
        viewModelScope.launch {
            when (val result = setThemeModeUseCase(themeMode)) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> Unit
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun setPureBlackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (setPureBlackEnabledUseCase(enabled) is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure) {
                _events.emit(SettingsEvent.ShowMessage("Could not update pure black theme."))
            }
        }
    }

    fun setLiveDetectionModel(model: DocumentCornerModel) {
        if (_uiState.value.automaticModelSelectionEnabled) return
        viewModelScope.launch {
            if (setLiveDetectionModelUseCase(model) is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure) {
                _events.emit(SettingsEvent.ShowMessage("Could not update the live preview model."))
            }
        }
    }

    fun setPostProcessingModel(model: DocumentCornerModel) {
        if (_uiState.value.automaticModelSelectionEnabled) return
        viewModelScope.launch {
            if (setPostProcessingModelUseCase(model) is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure) {
                _events.emit(SettingsEvent.ShowMessage("Could not update the post-processing model."))
            }
        }
    }

    fun setAutomaticModelSelection(enabled: Boolean) {
        viewModelScope.launch {
            if (setAutomaticModelSelectionUseCase(enabled) is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure) {
                _events.emit(SettingsEvent.ShowMessage("Could not update automatic model selection."))
            }
        }
    }

    fun setDocumentGateEnabled(enabled: Boolean) {
        viewModelScope.launch {
            if (setDocumentGateEnabledUseCase(enabled) is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure) {
                _events.emit(SettingsEvent.ShowMessage("Could not update the physical-document gate."))
            }
        }
    }

    fun setOcrEngine(engine: OcrEngine) {
        val switching = _uiState.value.ocrEngine != engine
        if (!switching && engine != OcrEngine.INDRA) return
        viewModelScope.launch {
            // Choosing Indra, even when it is already chosen, is the way to retry after a pause.
            if (engine == OcrEngine.INDRA) resetIndraUseCase()
            if (!switching) return@launch
            promptAfterSwitchTo = engine
            if (setOcrEngineUseCase(engine) is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure) {
                promptAfterSwitchTo = null
                _events.emit(SettingsEvent.ShowMessage("Could not update the text recognition engine."))
            }
        }
    }

    fun requestReread() {
        if (_uiState.value.pagesNotReadWithSelectedEngine > 0) {
            _uiState.update { it.copy(showRereadPrompt = true) }
        }
    }

    fun dismissRereadPrompt() {
        _uiState.update { it.copy(showRereadPrompt = false) }
    }

    fun confirmReread() {
        val engine = _uiState.value.ocrEngine
        _uiState.update { it.copy(showRereadPrompt = false) }
        viewModelScope.launch {
            when (val result = rereadPagesNotReadWithUseCase(engine)) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    val count = result.value
                    _events.emit(
                        SettingsEvent.ShowMessage(
                            when (count) {
                                0 -> "No pages to read again."
                                1 -> "Reading 1 page again in the background."
                                else -> "Reading $count pages again in the background."
                            },
                        ),
                    )
                }

                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun setExportDestination(uriString: String, displayName: String) {
        viewModelScope.launch {
            when (val result = setExportDestinationUseCase(
                ExportDestination.CustomTree(uriString, displayName),
            )) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    _events.emit(SettingsEvent.ShowMessage("Save location updated."))
                }
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    /**
     * Records that the user chose to rate Scanly from Settings. The store flow itself is launched
     * by the screen, which owns the Activity.
     */
    fun onRateApp() {
        viewModelScope.launch { markAppRatedUseCase() }
    }

    fun dismissRatingCard() {
        viewModelScope.launch { dismissSettingsRatingCardUseCase() }
    }

    fun resetExportDestination() {
        viewModelScope.launch {
            when (val result = resetExportDestinationUseCase()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    _events.emit(SettingsEvent.ShowMessage("Save location reset to Downloads/Scanly."))
                }
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }
}
