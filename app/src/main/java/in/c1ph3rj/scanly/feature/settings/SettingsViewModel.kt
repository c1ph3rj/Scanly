package `in`.c1ph3rj.scanly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.model.SettingsContent
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.usecase.ClearAllAppDataUseCase
import `in`.c1ph3rj.scanly.domain.usecase.GetAppStorageUsageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.LoadSettingsContentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveThemeModeUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetThemeModeUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ClearTemporaryCacheUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RebuildLibraryIndexUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ConnectLibraryUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveLibraryAccessUseCase
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
    val isMaintainingLibrary: Boolean = false,
    val libraryDisplayName: String? = null,
)

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
    data object RequestLibraryFolder : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    private val loadSettingsContentUseCase: LoadSettingsContentUseCase,
    private val getAppStorageUsageUseCase: GetAppStorageUsageUseCase,
    private val clearAllAppDataUseCase: ClearAllAppDataUseCase,
    private val clearTemporaryCacheUseCase: ClearTemporaryCacheUseCase,
    private val rebuildLibraryIndexUseCase: RebuildLibraryIndexUseCase,
    private val connectLibraryUseCase: ConnectLibraryUseCase,
    observeLibraryAccessUseCase: ObserveLibraryAccessUseCase,
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
            observeLibraryAccessUseCase().collectLatest { access ->
                _uiState.update { current -> current.copy(libraryDisplayName = access.displayName) }
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

    fun clearTemporaryCache() = runMaintenance(
        successMessage = "Temporary files cleared.",
        action = { clearTemporaryCacheUseCase() },
    )

    fun rebuildLibraryIndex() = runMaintenance(
        successMessage = "Library index rebuilt.",
        action = { rebuildLibraryIndexUseCase() },
    )

    fun requestLibraryFolder() {
        viewModelScope.launch { _events.emit(SettingsEvent.RequestLibraryFolder) }
    }

    fun connectLibrary(treeUri: String) = runMaintenance(
        successMessage = "Scanly library connected.",
        action = { connectLibraryUseCase(treeUri) },
    )

    private fun runMaintenance(
        successMessage: String,
        action: suspend () -> `in`.c1ph3rj.scanly.core.common.ScanlyResult<Unit>,
    ) {
        if (_uiState.value.isMaintainingLibrary) return
        viewModelScope.launch {
            _uiState.update { it.copy(isMaintainingLibrary = true) }
            when (val result = action()) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> {
                    _events.emit(SettingsEvent.ShowMessage(successMessage))
                    loadStorageUsage()
                }
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure ->
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
            }
            _uiState.update { it.copy(isMaintainingLibrary = false) }
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

}
