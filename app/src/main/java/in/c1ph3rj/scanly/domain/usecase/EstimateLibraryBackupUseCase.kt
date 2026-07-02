package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.BackupEstimate
import `in`.c1ph3rj.scanly.domain.repository.LibraryArchiveRepository
import javax.inject.Inject

class EstimateLibraryBackupUseCase @Inject constructor(
    private val repository: LibraryArchiveRepository,
) {
    suspend operator fun invoke(): ScanlyResult<BackupEstimate> = repository.estimateBackup()
}
