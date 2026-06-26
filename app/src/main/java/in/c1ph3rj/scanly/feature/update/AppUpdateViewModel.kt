package `in`.c1ph3rj.scanly.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.update.AppReleaseApkInstaller
import `in`.c1ph3rj.scanly.domain.model.AppRelease
import `in`.c1ph3rj.scanly.domain.model.AppUpdateCheckResult
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
    val isDownloadingApk: Boolean = false,
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
    private val apkInstaller: AppReleaseApkInstaller,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppUpdateEvent>()
    val events: SharedFlow<AppUpdateEvent> = _events.asSharedFlow()

    private var dismissedReleaseTag: String? = null

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
        val releaseTag = _uiState.value.dialogCheckResult?.latestRelease?.tagName
        if (releaseTag != null) {
            dismissedReleaseTag = releaseTag
        }
        _uiState.update { current -> current.copy(dialogCheckResult = null) }
    }

    fun downloadRelease(release: AppRelease) {
        if (_uiState.value.isDownloadingApk) return

        val apkAsset = release.apkAsset
        if (apkAsset == null) {
            viewModelScope.launch {
                _events.emit(AppUpdateEvent.OpenUri(release.htmlUrl))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { current -> current.copy(isDownloadingApk = true) }

            when (
                val result = apkInstaller.downloadAndPromptInstall(
                    downloadUrl = apkAsset.downloadUrl,
                    fileName = apkAsset.name,
                )
            ) {
                is ScanlyResult.Success -> {
                    dismissUpdateDialog()
                }

                is ScanlyResult.Failure -> {
                    _events.emit(
                        AppUpdateEvent.ShowMessage(
                            result.error.message.ifBlank {
                                "Could not download the update."
                            },
                        ),
                    )
                }
            }

            _uiState.update { current -> current.copy(isDownloadingApk = false) }
        }
    }

    private suspend fun handleSuccessfulCheck(
        checkResult: AppUpdateCheckResult,
        trigger: AppUpdateCheckTrigger,
    ) {
        val releaseTag = checkResult.latestRelease.tagName
        val shouldShowDialog = checkResult.updateAvailable &&
            (trigger == AppUpdateCheckTrigger.Manual || dismissedReleaseTag != releaseTag)

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
