package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkState
import `in`.c1ph3rj.scanly.domain.repository.LibraryArchiveRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveLibraryArchiveWorkUseCase @Inject constructor(
    private val repository: LibraryArchiveRepository,
) {
    operator fun invoke(): Flow<ArchiveWorkState> = repository.observeWorkState()
}
