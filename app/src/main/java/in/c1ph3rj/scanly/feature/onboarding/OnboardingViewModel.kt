package `in`.c1ph3rj.scanly.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.usecase.CompleteOnboardingUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveOnboardingCompletedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class OnboardingStatus {
    LOADING,
    REQUIRED,
    COMPLETE,
}

data class OnboardingUiState(
    val status: OnboardingStatus = OnboardingStatus.LOADING,
    val isCompleting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    observeOnboardingCompletedUseCase: ObserveOnboardingCompletedUseCase,
    private val completeOnboardingUseCase: CompleteOnboardingUseCase,
) : ViewModel() {
    private val isCompleting = MutableStateFlow(false)
    private val completedInSession = MutableStateFlow(false)
    private val errorMessage = MutableStateFlow<String?>(null)

    private val persistedStatus = observeOnboardingCompletedUseCase()
        .map { completed ->
            if (completed) OnboardingStatus.COMPLETE else OnboardingStatus.REQUIRED
        }

    val uiState: StateFlow<OnboardingUiState> = combine(
        persistedStatus,
        completedInSession,
        isCompleting,
        errorMessage,
    ) { status, sessionCompleted, completing, error ->
        OnboardingUiState(
            status = if (sessionCompleted) OnboardingStatus.COMPLETE else status,
            isCompleting = completing,
            errorMessage = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(stopTimeoutMillis = 5_000),
        initialValue = OnboardingUiState(),
    )

    fun completeOnboarding() {
        if (isCompleting.value || completedInSession.value) return

        viewModelScope.launch {
            isCompleting.value = true
            errorMessage.value = null
            when (val result = completeOnboardingUseCase()) {
                is ScanlyResult.Success -> completedInSession.value = true
                is ScanlyResult.Failure -> errorMessage.value = result.error.message
            }
            isCompleting.value = false
        }
    }

    fun dismissError() {
        errorMessage.value = null
    }
}
