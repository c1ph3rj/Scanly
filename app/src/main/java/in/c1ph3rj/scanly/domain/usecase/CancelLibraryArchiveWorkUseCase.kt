package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.LibraryArchiveRepository
import javax.inject.Inject

class CancelLibraryArchiveWorkUseCase @Inject constructor(
    private val repository: LibraryArchiveRepository,
) {
    suspend operator fun invoke() = repository.cancelActiveWork()
}
