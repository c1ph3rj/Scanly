package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppUpdateCheckResult

interface AppUpdateRepository {
    suspend fun checkForUpdate(): ScanlyResult<AppUpdateCheckResult>
}
