package `in`.c1ph3rj.scanly

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.usecase.DeleteLegacyArchiveLeftoversUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObservePureBlackEnabledUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecordFirstSeenUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveThemeModeUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
    observePureBlackEnabledUseCase: ObservePureBlackEnabledUseCase,
    private val deleteLegacyArchiveLeftoversUseCase: DeleteLegacyArchiveLeftoversUseCase,
    private val recordFirstSeenUseCase: RecordFirstSeenUseCase,
) : ViewModel() {

    init {
        // Upgrade cleanup for the removed library backup feature. Fire and forget.
        viewModelScope.launch { deleteLegacyArchiveLeftoversUseCase() }
        // Anchors the rating prompt's "not too early" window. Only the first launch counts.
        viewModelScope.launch { recordFirstSeenUseCase() }
    }

    val themeMode: StateFlow<ThemeMode> = observeThemeModeUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ThemeMode.SYSTEM,
    )

    val pureBlackEnabled: StateFlow<Boolean> = observePureBlackEnabledUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = false,
    )
}
