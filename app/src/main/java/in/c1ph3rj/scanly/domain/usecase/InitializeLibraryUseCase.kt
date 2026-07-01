package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.LibraryAccessRepository
import javax.inject.Inject

class InitializeLibraryUseCase @Inject constructor(
    private val repository: LibraryAccessRepository,
) {
    suspend operator fun invoke() = repository.initialize()
}

