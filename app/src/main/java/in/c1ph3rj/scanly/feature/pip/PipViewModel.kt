package `in`.c1ph3rj.scanly.feature.pip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.domain.model.PipPreference
import `in`.c1ph3rj.scanly.domain.usecase.DismissPipTipUseCase
import `in`.c1ph3rj.scanly.domain.usecase.SetPipPreferenceUseCase
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

/**
 * Writes Pip's preferences. Reading happens through [LocalPipPreferences], which the app root
 * keeps current, so screens never hold a second copy.
 */
@HiltViewModel
class PipViewModel @Inject constructor(
    private val setPipPreferenceUseCase: SetPipPreferenceUseCase,
    private val dismissPipTipUseCase: DismissPipTipUseCase,
) : ViewModel() {

    fun setPreference(preference: PipPreference, enabled: Boolean) {
        viewModelScope.launch { setPipPreferenceUseCase(preference, enabled) }
    }

    fun dismissTodaysTip() {
        viewModelScope.launch { dismissPipTipUseCase(LocalDate.now().toEpochDay()) }
    }
}
