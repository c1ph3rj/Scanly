package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.LibraryAccessRepository
import javax.inject.Inject

class ObserveLibraryAccessUseCase @Inject constructor(
    private val repository: LibraryAccessRepository,
) {
    operator fun invoke() = repository.state
}

