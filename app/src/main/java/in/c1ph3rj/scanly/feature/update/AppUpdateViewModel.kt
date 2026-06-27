package `in`.c1ph3rj.scanly.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppRelease
import `in`.c1ph3rj.scanly.domain.model.AppUpdateCheckResult
import `in`.c1ph3rj.scanly.domain.repository.AppUpdatePromptRepository
import `in`.c1ph3rj.scanly.domain.usecase.CheckForAppUpdateUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AppUpdateUiState(
    val isChecking: Boolean = false,
    val lastCheckResult: AppUpdateCheckResult? = null,
    val dialogCheckResult: AppUpdateCheckResult? = null,
)

enum class AppUpdateCheckTrigger {
    Automatic,
    Manual,
}

sealed interface AppUpdateEvent {
    data class ShowMessage(val message: String) : AppUpdateEvent
    data class OpenUri(val uri: String) : AppUpdateEvent
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checkForAppUpdateUseCase: CheckForAppUpdateUseCase,
    private val updatePromptRepository: AppUpdatePromptRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppUpdateEvent>()
    val events: SharedFlow<AppUpdateEvent> = _events.asSharedFlow()

    fun checkForUpdates(trigger: AppUpdateCheckTrigger) {
        val currentState = _uiState.value
        if (currentState.isChecking || currentState.dialogCheckResult != null) {
            return
        }

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isChecking = true) }

            when (val result = checkForAppUpdateUseCase()) {
                is ScanlyResult.Success -> handleSuccessfulCheck(
                    checkResult = result.value,
                    trigger = trigger,
                )

                is ScanlyResult.Failure -> {
                    _uiState.update { current -> current.copy(isChecking = false) }
                    if (trigger == AppUpdateCheckTrigger.Manual) {
                        _events.emit(
                            AppUpdateEvent.ShowMessage(
                                result.error.message.ifBlank {
                                    "Could not check for updates."
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { current -> current.copy(dialogCheckResult = null) }
    }

    fun downloadRelease(release: AppRelease) {
        viewModelScope.launch {
            dismissUpdateDialog()
            _events.emit(AppUpdateEvent.OpenUri(release.htmlUrl))
        }
    }

    private suspend fun handleSuccessfulCheck(
        checkResult: AppUpdateCheckResult,
        trigger: AppUpdateCheckTrigger,
    ) {
        val nowMillis = System.currentTimeMillis()
        val lastShownAtMillis = updatePromptRepository.getLastUpdateDialogShownAtMillis()
        val shouldShowDialog = checkResult.updateAvailable &&
            (
                trigger == AppUpdateCheckTrigger.Manual ||
                    AppUpdateDialogCooldown.canShowAgain(lastShownAtMillis, nowMillis)
                )

        if (shouldShowDialog) {
            updatePromptRepository.setLastUpdateDialogShownAtMillis(nowMillis)
        }

        _uiState.update { current ->
            current.copy(
                isChecking = false,
                lastCheckResult = checkResult,
                dialogCheckResult = if (shouldShowDialog) checkResult else current.dialogCheckResult,
            )
        }

        if (trigger == AppUpdateCheckTrigger.Manual && !checkResult.updateAvailable) {
            _events.emit(
                AppUpdateEvent.ShowMessage(
                    "Scanly is up to date. Latest release is ${checkResult.latestRelease.tagName}.",
                ),
            )
        }
    }
}
