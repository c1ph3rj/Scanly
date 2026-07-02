package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkState
import `in`.c1ph3rj.scanly.domain.model.BackupEstimate
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import kotlinx.coroutines.flow.Flow

interface LibraryArchiveRepository {
    fun observeWorkState(): Flow<ArchiveWorkState>

    suspend fun estimateBackup(): ScanlyResult<BackupEstimate>

    suspend fun startBackup(): ScanlyResult<Unit>

    suspend fun startRestore(uriString: String, mode: RestoreMode): ScanlyResult<Unit>

    suspend fun cancelActiveWork()
}
