package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import `in`.c1ph3rj.scanly.domain.repository.LibraryArchiveRepository
import javax.inject.Inject

class StartLibraryRestoreUseCase @Inject constructor(
    private val repository: LibraryArchiveRepository,
) {
    suspend operator fun invoke(uriString: String, mode: RestoreMode): ScanlyResult<Unit> =
        repository.startRestore(uriString, mode)
}
