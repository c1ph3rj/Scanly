package `in`.c1ph3rj.scanly.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.update.AppReleaseApkInstaller
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
    data class InstallApk(val intent: android.content.Intent) : AppUpdateEvent
    data object RequestInstallPermission : AppUpdateEvent
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val checkForAppUpdateUseCase: CheckForAppUpdateUseCase,
    private val updatePromptRepository: AppUpdatePromptRepository,
    private val apkInstaller: AppReleaseApkInstaller,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<AppUpdateEvent>()
    val events: SharedFlow<AppUpdateEvent> = _events.asSharedFlow()

    private var pendingInstallApkPath: String? = null

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

    fun retryPendingInstall() {
        val apkPath = pendingInstallApkPath ?: return
        if (!apkInstaller.canRequestPackageInstalls()) return

        viewModelScope.launch {
            val apkFile = java.io.File(apkPath)
            if (!apkFile.exists()) {
                pendingInstallApkPath = null
                return@launch
            }

            when (val installIntent = apkInstaller.createInstallIntent(apkFile)) {
                is ScanlyResult.Success -> {
                    pendingInstallApkPath = null
                    _events.emit(AppUpdateEvent.InstallApk(installIntent.value))
                }

                is ScanlyResult.Failure -> {
                    _events.emit(
                        AppUpdateEvent.ShowMessage(
                            installIntent.error.message.ifBlank {
                                "Could not open the update installer."
                            },
                        ),
                    )
                }
            }
        }
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
                val result = apkInstaller.enqueueBackgroundDownload(
                    downloadUrl = apkAsset.downloadUrl,
                    fileName = apkAsset.name,
                    releaseTag = release.tagName,
                )
            ) {
                is ScanlyResult.Success -> {
                    dismissUpdateDialog()
                    _events.emit(
                        AppUpdateEvent.ShowMessage(
                            "Downloading ${apkAsset.name}…",
                        ),
                    )

                    when (
                        val downloadResult = apkInstaller.waitForDownloadComplete(
                            downloadId = result.value,
                            fileName = apkAsset.name,
                        )
                    ) {
                        is ScanlyResult.Success -> {
                            if (!apkInstaller.canRequestPackageInstalls()) {
                                pendingInstallApkPath = downloadResult.value.absolutePath
                                _events.emit(AppUpdateEvent.RequestInstallPermission)
                                return@launch
                            }
                            when (val installIntent = apkInstaller.createInstallIntent(downloadResult.value)) {
                                is ScanlyResult.Success -> {
                                    _events.emit(AppUpdateEvent.InstallApk(installIntent.value))
                                }

                                is ScanlyResult.Failure -> {
                                    _events.emit(
                                        AppUpdateEvent.ShowMessage(
                                            installIntent.error.message.ifBlank {
                                                "Could not open the update installer."
                                            },
                                        ),
                                    )
                                }
                            }
                        }

                        is ScanlyResult.Failure -> {
                            _events.emit(
                                AppUpdateEvent.ShowMessage(
                                    downloadResult.error.message.ifBlank {
                                        "The update download did not finish."
                                    },
                                ),
                            )
                        }
                    }
                }

                is ScanlyResult.Failure -> {
                    _events.emit(
                        AppUpdateEvent.ShowMessage(
                            result.error.message.ifBlank {
                                "Could not start the update download."
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
