package `in`.c1ph3rj.scanly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.model.SettingsContent
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.domain.model.BackupEstimate
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkState
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkPhase
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import `in`.c1ph3rj.scanly.domain.usecase.ClearAllAppDataUseCase
import `in`.c1ph3rj.scanly.domain.usecase.GetAppStorageUsageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.LoadSettingsContentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveThemeModeUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetThemeModeUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveExportDestinationUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetExportDestinationUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ResetExportDestinationUseCase
import `in`.c1ph3rj.scanly.domain.usecase.EstimateLibraryBackupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.StartLibraryBackupUseCase
import `in`.c1ph3rj.scanly.domain.usecase.StartLibraryRestoreUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveLibraryArchiveWorkUseCase
import `in`.c1ph3rj.scanly.domain.usecase.CancelLibraryArchiveWorkUseCase
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val content: SettingsContent? = null,
    val isLoading: Boolean = true,
    val storageUsage: AppStorageUsage? = null,
    val isLoadingStorage: Boolean = true,
    val isClearingData: Boolean = false,
    val exportDestination: ExportDestination = ExportDestination.DefaultDownloadsScanly,
    val backupEstimate: BackupEstimate? = null,
    val isLoadingBackupEstimate: Boolean = true,
    val archiveWork: ArchiveWorkState = ArchiveWorkState(),
)

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    private val loadSettingsContentUseCase: LoadSettingsContentUseCase,
    private val getAppStorageUsageUseCase: GetAppStorageUsageUseCase,
    private val clearAllAppDataUseCase: ClearAllAppDataUseCase,
    observeExportDestinationUseCase: ObserveExportDestinationUseCase,
    private val setExportDestinationUseCase: SetExportDestinationUseCase,
    private val resetExportDestinationUseCase: ResetExportDestinationUseCase,
    private val estimateLibraryBackupUseCase: EstimateLibraryBackupUseCase,
    private val startLibraryBackupUseCase: StartLibraryBackupUseCase,
    private val startLibraryRestoreUseCase: StartLibraryRestoreUseCase,
    observeLibraryArchiveWorkUseCase: ObserveLibraryArchiveWorkUseCase,
    private val cancelLibraryArchiveWorkUseCase: CancelLibraryArchiveWorkUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<SettingsEvent>()
    val events: SharedFlow<SettingsEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            observeThemeModeUseCase().collectLatest { themeMode ->
                _uiState.update { current -> current.copy(themeMode = themeMode) }
            }
        }
        viewModelScope.launch {
            observeExportDestinationUseCase().collectLatest { destination ->
                _uiState.update { current -> current.copy(exportDestination = destination) }
                loadBackupEstimate()
            }
        }
        viewModelScope.launch {
            var previousPhase = ArchiveWorkPhase.IDLE
            observeLibraryArchiveWorkUseCase().collectLatest { work ->
                _uiState.update { current -> current.copy(archiveWork = work) }
                if (work.phase != previousPhase && work.phase in setOf(
                        ArchiveWorkPhase.SUCCEEDED,
                        ArchiveWorkPhase.FAILED,
                        ArchiveWorkPhase.CANCELLED,
                    )
                ) {
                    work.message?.let { _events.emit(SettingsEvent.ShowMessage(it)) }
                    loadStorageUsage()
                    loadBackupEstimate()
                }
                previousPhase = work.phase
            }
        }
        refresh()
        loadStorageUsage()
        loadBackupEstimate()
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
                    loadBackupEstimate()
                }

                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isClearingData = false) }
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

    fun loadBackupEstimate() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBackupEstimate = true) }
            when (val result = estimateLibraryBackupUseCase()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    _uiState.update {
                        it.copy(backupEstimate = result.value, isLoadingBackupEstimate = false)
                    }
                }
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _uiState.update { it.copy(backupEstimate = null, isLoadingBackupEstimate = false) }
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun startBackup() {
        if (_uiState.value.archiveWork.isRunning || _uiState.value.backupEstimate?.canBackup != true) return
        viewModelScope.launch {
            when (val result = startLibraryBackupUseCase()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> Unit
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                    loadBackupEstimate()
                }
            }
        }
    }

    fun startRestore(uriString: String, mode: RestoreMode) {
        if (_uiState.value.archiveWork.isRunning) return
        viewModelScope.launch {
            when (val result = startLibraryRestoreUseCase(uriString, mode)) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> Unit
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }

    fun cancelArchiveWork() {
        viewModelScope.launch { cancelLibraryArchiveWorkUseCase() }
    }

}
