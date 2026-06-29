package `in`.c1ph3rj.scanly.domain.repository

import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateAvailability
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateInstallState
import `in`.c1ph3rj.scanly.domain.model.PlayInAppUpdateType
import kotlinx.coroutines.flow.StateFlow

interface PlayInAppUpdateCoordinator {
    val installState: StateFlow<PlayInAppUpdateInstallState?>

    suspend fun refreshAvailability(): ScanlyResult<PlayInAppUpdateAvailability>

    suspend fun startUpdate(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
        updateType: PlayInAppUpdateType,
    ): Boolean

    suspend fun resumeStalledUpdate(
        activity: ComponentActivity,
        launcher: ActivityResultLauncher<IntentSenderRequest>,
    ): Boolean

    suspend fun completeFlexibleUpdate()
}