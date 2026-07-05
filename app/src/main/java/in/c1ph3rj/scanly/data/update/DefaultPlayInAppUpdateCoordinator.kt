package `in`.c1ph3rj.scanly.data.update

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.google.android.play.core.appupdate.AppUpdateInfo
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.appupdate.AppUpdateOptions
import com.google.android.play.core.install.InstallException
import com.google.android.play.core.install.InstallStateUpdatedListener
import com.google.android.play.core.install.model.AppUpdateType
import com.google.android.play.core.install.model.InstallStatus
import com.google.android.play.core.install.model.UpdateAvailability
import com.google.android.play.core.ktx.isFlexibleUpdateAllowed
import com.google.android.play.core.ktx.isImmediateUpdateAllowed
import com.google.android.play.core.ktx.requestAppUpdateInfo
import com.google.android.play.core.ktx.requestCompleteUpdate
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.update.PlayInAppUpdatePolicy
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateAvailability
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateInstallState
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateType
import `in`.c1ph3rj.scanly.domain.model.PlayInstallStatus
import `in`.c1ph3rj.scanly.domain.repository.PlayInAppUpdateCoordinator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class DefaultPlayInAppUpdateCoordinator @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : PlayInAppUpdateCoordinator {

    private val appUpdateManager = AppUpdateManagerFactory.create(context)
    private var cachedAppUpdateInfo: AppUpdateInfo? = null

    private val _installState = MutableStateFlow<PlayInAppUpdateInstallState?>(null)
    override val installState: StateFlow<PlayInAppUpdateInstallState?> = _installState.asStateFlow()

    private val installStateListener = InstallStateUpdatedListener { state ->
        _installState.value = PlayInAppUpdateInstallState(
            status = state.installStatus().toPlayInstallStatus(),
            bytesDownloaded = state.bytesDownloaded(),
            totalBytesToDownload = state.totalBytesToDownload(),
        )
    }

    override suspend fun refreshAvailability(): ScanlyResult<PlayInAppUpdateAvailability> =
        withContext(dispatchers.io) {
            runCatching {
                val appUpdateInfo = appUpdateManager.requestAppUpdateInfo()
                cachedAppUpdateInfo = appUpdateInfo
                _installState.value = PlayInAppUpdateInstallState(
                    status = appUpdateInfo.installStatus().toPlayInstallStatus(),
                    bytesDownloaded = appUpdateInfo.bytesDownloaded(),
                    totalBytesToDownload = appUpdateInfo.totalBytesToDownload(),
                )
                ScanlyResult.Success(appUpdateInfo.toAvailability())
            }.getOrElse { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.toPlayUpdateMessage(),
                        cause = throwable,
                    ),
                )
            }
        }

    override suspend fun startUpdate(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        updateType: PlayInAppUpdateType,
    ): Boolean = withContext(dispatchers.main) {
        val appUpdateInfo = cachedAppUpdateInfo ?: return@withContext false
        if (!appUpdateInfo.canStart(updateType)) {
            return@withContext false
        }

        appUpdateManager.registerListener(installStateListener)
        appUpdateManager.startUpdateFlowForResult(
            appUpdateInfo,
            launcher,
            AppUpdateOptions.newBuilder(updateType.toAppUpdateType()).build(),
        )
    }

    override suspend fun resumeStalledUpdate(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean = withContext(dispatchers.main) {
        val appUpdateInfo = runCatching {
            appUpdateManager.requestAppUpdateInfo()
        }.getOrNull() ?: return@withContext false

        cachedAppUpdateInfo = appUpdateInfo

        when {
            appUpdateInfo.installStatus() == InstallStatus.DOWNLOADED -> {
                _installState.value = PlayInAppUpdateInstallState(
                    status = PlayInstallStatus.DOWNLOADED,
                )
                true
            }

            appUpdateInfo.updateAvailability() ==
                UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS &&
                appUpdateInfo.isImmediateUpdateAllowed -> {
                appUpdateManager.registerListener(installStateListener)
                appUpdateManager.startUpdateFlowForResult(
                    appUpdateInfo,
                    launcher,
                    AppUpdateOptions.newBuilder(AppUpdateType.IMMEDIATE).build(),
                )
            }

            else -> false
        }
    }

    override suspend fun completeFlexibleUpdate() {
        withContext(dispatchers.io) {
            runCatching {
                appUpdateManager.requestCompleteUpdate()
            }
        }
    }

    private fun AppUpdateInfo.toAvailability(): PlayInAppUpdateAvailability {
        val updateAvailable = updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE
        val recommendedUpdateType = if (updateAvailable) {
            PlayInAppUpdatePolicy.resolveUpdateType(
                updatePriority = updatePriority(),
                stalenessDays = clientVersionStalenessDays(),
                flexibleAllowed = isFlexibleUpdateAllowed,
                immediateAllowed = isImmediateUpdateAllowed,
            )
        } else {
            null
        }

        return PlayInAppUpdateAvailability(
            updateAvailable = updateAvailable,
            availableVersionCode = availableVersionCode().takeIf { updateAvailable },
            updatePriority = updatePriority(),
            stalenessDays = clientVersionStalenessDays(),
            recommendedUpdateType = recommendedUpdateType,
            installStatus = installStatus().toPlayInstallStatus(),
            developerTriggeredUpdateInProgress =
                updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS,
        )
    }

    private fun AppUpdateInfo.canStart(updateType: PlayInAppUpdateType): Boolean {
        val availabilityAllowsUpdate = updateAvailability() == UpdateAvailability.UPDATE_AVAILABLE ||
            updateAvailability() == UpdateAvailability.DEVELOPER_TRIGGERED_UPDATE_IN_PROGRESS
        if (!availabilityAllowsUpdate) {
            return false
        }

        return when (updateType) {
            PlayInAppUpdateType.FLEXIBLE -> isFlexibleUpdateAllowed
            PlayInAppUpdateType.IMMEDIATE -> isImmediateUpdateAllowed
        }
    }

    private fun PlayInAppUpdateType.toAppUpdateType(): Int = when (this) {
        PlayInAppUpdateType.FLEXIBLE -> AppUpdateType.FLEXIBLE
        PlayInAppUpdateType.IMMEDIATE -> AppUpdateType.IMMEDIATE
    }

    private fun Int.toPlayInstallStatus(): PlayInstallStatus = when (this) {
        InstallStatus.PENDING -> PlayInstallStatus.PENDING
        InstallStatus.DOWNLOADING -> PlayInstallStatus.DOWNLOADING
        InstallStatus.DOWNLOADED -> PlayInstallStatus.DOWNLOADED
        InstallStatus.INSTALLING -> PlayInstallStatus.INSTALLING
        InstallStatus.INSTALLED -> PlayInstallStatus.INSTALLED
        InstallStatus.FAILED -> PlayInstallStatus.FAILED
        InstallStatus.CANCELED -> PlayInstallStatus.CANCELED
        else -> PlayInstallStatus.UNKNOWN
    }

    private fun Throwable.toPlayUpdateMessage(): String = when (this) {
        is InstallException -> message ?: "Google Play could not check for updates."
        else -> message ?: "Could not check Google Play for updates."
    }
}