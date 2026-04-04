package `in`.c1ph3rj.scanly

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.ThemeMode
import `in`.c1ph3rj.scanly.domain.usecase.ObserveThemeModeUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    observeThemeModeUseCase: ObserveThemeModeUseCase,
) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = observeThemeModeUseCase().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = ThemeMode.SYSTEM,
    )
}
