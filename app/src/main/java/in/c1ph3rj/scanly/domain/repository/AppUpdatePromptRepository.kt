package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult

interface AppUpdatePromptRepository {
    suspend fun getLastUpdateDialogShownAtMillis(): Long?

    suspend fun setLastUpdateDialogShownAtMillis(timestampMillis: Long): ScanlyResult<Unit>
}
