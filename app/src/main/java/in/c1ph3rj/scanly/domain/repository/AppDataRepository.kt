package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage

interface AppDataRepository {
    suspend fun getStorageUsage(): ScanlyResult<AppStorageUsage>

    suspend fun clearAllLibraryData(): ScanlyResult<Unit>

    suspend fun clearTemporaryCache(): ScanlyResult<Unit>
}
