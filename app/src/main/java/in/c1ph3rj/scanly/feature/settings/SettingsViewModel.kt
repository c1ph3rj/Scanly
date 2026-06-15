package `in`.c1ph3rj.scanly.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.model.SettingsContent
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.usecase.ClearAllAppDataUseCase
import `in`.c1ph3rj.scanly.domain.usecase.GetAppStorageUsageUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveShowDetectionStatsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.LoadSettingsContentUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveThemeModeUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetShowDetectionStatsUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetThemeModeUseCase
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
    val showDetectionStats: Boolean = true,
    val content: SettingsContent? = null,
    val isLoading: Boolean = true,
    val storageUsage: AppStorageUsage? = null,
    val isLoadingStorage: Boolean = true,
    val isClearingData: Boolean = false,
)

sealed interface SettingsEvent {
    data class ShowMessage(val message: String) : SettingsEvent
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    observeShowDetectionStatsUseCase: ObserveShowDetectionStatsUseCase,
    private val setThemeModeUseCase: SetThemeModeUseCase,
    private val setShowDetectionStatsUseCase: SetShowDetectionStatsUseCase,
    private val loadSettingsContentUseCase: LoadSettingsContentUseCase,
    private val getAppStorageUsageUseCase: GetAppStorageUsageUseCase,
    private val clearAllAppDataUseCase: ClearAllAppDataUseCase,
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
            observeShowDetectionStatsUseCase().collectLatest { showDetectionStats ->
                _uiState.update { current ->
                    current.copy(showDetectionStats = showDetectionStats)
                }
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

    fun setShowDetectionStats(enabled: Boolean) {
        viewModelScope.launch {
            when (val result = setShowDetectionStatsUseCase(enabled)) {
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Success -> Unit
                is `in`.c1ph3rj.scanly.core.common.ScanlyResult.Failure -> {
                    _events.emit(SettingsEvent.ShowMessage(result.error.message))
                }
            }
        }
    }
}
