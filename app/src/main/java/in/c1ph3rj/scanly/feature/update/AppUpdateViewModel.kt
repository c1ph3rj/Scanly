package `in`.c1ph3rj.scanly.feature.update

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.BuildConfig
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.update.PlayInAppUpdatePolicy
import `in`.c1ph3rj.scanly.domain.model.AppRelease
import `in`.c1ph3rj.scanly.domain.model.AppUpdateChannel
import `in`.c1ph3rj.scanly.domain.model.AppUpdateCheckResult
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateType
import `in`.c1ph3rj.scanly.domain.model.PlayInstallStatus
import `in`.c1ph3rj.scanly.domain.repository.AppUpdatePromptRepository
import `in`.c1ph3rj.scanly.domain.repository.PlayInAppUpdateCoordinator
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
    val channel: AppUpdateChannel,
    val isChecking: Boolean = false,
    val lastCheckResult: AppUpdateCheckResult? = null,
    val dialogCheckResult: AppUpdateCheckResult? = null,
    val flexibleUpdateDownloaded: Boolean = false,
    val flexibleUpdateProgress: Float? = null,
    val flexibleUpdatePromptToken: Long = 0L,
)

enum class AppUpdateCheckTrigger {
    Automatic,
    Manual,
}

sealed interface AppUpdateEvent {
    data class ShowMessage(val message: String) : AppUpdateEvent
    data class OpenUri(val uri: String) : AppUpdateEvent
    data class LaunchPlayUpdate(val updateType: PlayInAppUpdateType) : AppUpdateEvent
    data object ResumePlayUpdate : AppUpdateEvent
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checkForAppUpdateUseCase: CheckForAppUpdateUseCase,
    private val updatePromptRepository: AppUpdatePromptRepository,
    private val playInAppUpdateCoordinator: PlayInAppUpdateCoordinator,
) : ViewModel() {

    private val updateChannel = AppUpdateChannel.fromBuildConfig(BuildConfig.UPDATE_CHANNEL)
    private val _uiState = MutableStateFlow(AppUpdateUiState(channel = updateChannel))
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppUpdateEvent>()
    val events: SharedFlow<AppUpdateEvent> = _events.asSharedFlow()

    init {
        if (updateChannel == AppUpdateChannel.PLAY_STORE) {
            viewModelScope.launch {
                playInAppUpdateCoordinator.installState.collect { installState ->
                    _uiState.update { current ->
                        val downloaded = installState?.status == PlayInstallStatus.DOWNLOADED
                        current.copy(
                            flexibleUpdateDownloaded = downloaded,
                            flexibleUpdateProgress = installState?.downloadProgress,
                            flexibleUpdatePromptToken = if (
                                downloaded && !current.flexibleUpdateDownloaded
                            ) {
                                current.flexibleUpdatePromptToken + 1L
                            } else {
                                current.flexibleUpdatePromptToken
                            },
                        )
                    }
                }
            }
        }
    }

    fun checkForUpdates(trigger: AppUpdateCheckTrigger) {
        val currentState = _uiState.value
        if (currentState.isChecking) {
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
                                    "Could not check ${updateChannel.sourceLabel} for updates."
                                },
                            ),
                        )
                    }
                }
            }
        }
    }

    fun resumePlayUpdateIfNeeded() {
        if (updateChannel != AppUpdateChannel.PLAY_STORE) return

        viewModelScope.launch {
            _events.emit(AppUpdateEvent.ResumePlayUpdate)
        }
    }

    fun dismissUpdateDialog() {
        _uiState.update { current -> current.copy(dialogCheckResult = null) }
    }

    fun startUpdate(release: AppRelease) {
        val checkResult = _uiState.value.dialogCheckResult
            ?: _uiState.value.lastCheckResult
            ?: return

        viewModelScope.launch {
            dismissUpdateDialog()
            when (checkResult.channel) {
                AppUpdateChannel.GITHUB -> _events.emit(AppUpdateEvent.OpenUri(release.htmlUrl))
                AppUpdateChannel.PLAY_STORE -> checkResult.playUpdateType?.let { updateType ->
                    _events.emit(AppUpdateEvent.LaunchPlayUpdate(updateType))
                }
            }
        }
    }

    fun completeFlexibleUpdate() {
        viewModelScope.launch {
            playInAppUpdateCoordinator.completeFlexibleUpdate()
            _uiState.update { current ->
                current.copy(
                    flexibleUpdateDownloaded = false,
                    flexibleUpdateProgress = null,
                )
            }
        }
    }

    fun launchPlayUpdate(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        updateType: PlayInAppUpdateType,
    ) {
        viewModelScope.launch {
            playInAppUpdateCoordinator.startUpdate(
                activity = activity,
                launcher = launcher,
                updateType = updateType,
            )
        }
    }

    fun resumePlayUpdate(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ) {
        viewModelScope.launch {
            playInAppUpdateCoordinator.resumeStalledUpdate(
                activity = activity,
                launcher = launcher,
            )
            when (val result = playInAppUpdateCoordinator.refreshAvailability()) {
                is ScanlyResult.Success -> {
                    val downloaded = result.value.installStatus == PlayInstallStatus.DOWNLOADED
                    _uiState.update { current ->
                        current.copy(
                            flexibleUpdateDownloaded = downloaded,
                            flexibleUpdatePromptToken = if (downloaded) {
                                current.flexibleUpdatePromptToken + 1L
                            } else {
                                current.flexibleUpdatePromptToken
                            },
                        )
                    }
                }

                is ScanlyResult.Failure -> Unit
            }
        }
    }

    fun onPlayUpdateFlowResult(resultCode: Int) {
        if (resultCode == android.app.Activity.RESULT_OK) {
            _uiState.update { current ->
                current.copy(
                    flexibleUpdateDownloaded = false,
                    flexibleUpdateProgress = null,
                )
            }
        }
    }

    private suspend fun handleSuccessfulCheck(
        checkResult: AppUpdateCheckResult,
        trigger: AppUpdateCheckTrigger,
    ) {
        val nowMillis = System.currentTimeMillis()
        val lastShownAtMillis = updatePromptRepository.getLastUpdateDialogShownAtMillis()
        val shouldAutoStartImmediate = PlayInAppUpdatePolicy.shouldAutoStartImmediate(
            updateType = checkResult.playUpdateType,
            triggerAutomatic = trigger == AppUpdateCheckTrigger.Automatic,
        )
        val shouldShowDialog = checkResult.updateAvailable &&
            !shouldAutoStartImmediate &&
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

        when {
            shouldAutoStartImmediate && checkResult.playUpdateType != null -> {
                _events.emit(AppUpdateEvent.LaunchPlayUpdate(checkResult.playUpdateType))
            }

            trigger == AppUpdateCheckTrigger.Manual && !checkResult.updateAvailable -> {
                _events.emit(
                    AppUpdateEvent.ShowMessage(
                        "Scanly is up to date. You are on ${versionLabel(checkResult.installedVersionName)}.",
                    ),
                )
            }
        }
    }

    private fun versionLabel(versionName: String): String =
        if (versionName.startsWith("v", ignoreCase = true)) {
            versionName
        } else {
            "v$versionName"
        }
}
