package `in`.c1ph3rj.scanly.data.update

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateAvailability
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateInstallState
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateType
import `in`.c1ph3rj.scanly.domain.repository.PlayInAppUpdateCoordinator
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

@Singleton
class NoOpPlayInAppUpdateCoordinator @Inject constructor() : PlayInAppUpdateCoordinator {
    override val installState: StateFlow<PlayInAppUpdateInstallState?> = MutableStateFlow(null)

    override suspend fun refreshAvailability(): ScanlyResult<PlayInAppUpdateAvailability> =
        ScanlyResult.Success(
            PlayInAppUpdateAvailability(
                updateAvailable = false,
                availableVersionCode = null,
                updatePriority = 0,
                stalenessDays = null,
                recommendedUpdateType = null,
                installStatus = null,
                developerTriggeredUpdateInProgress = false,
            ),
        )

    override suspend fun startUpdate(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        updateType: PlayInAppUpdateType,
    ): Boolean = false

    override suspend fun resumeStalledUpdate(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean = false

    override suspend fun completeFlexibleUpdate() = Unit
}
