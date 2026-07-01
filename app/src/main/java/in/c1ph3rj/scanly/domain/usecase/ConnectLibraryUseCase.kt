package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.LibraryAccessRepository
import javax.inject.Inject

class ConnectLibraryUseCase @Inject constructor(
    private val repository: LibraryAccessRepository,
) {
    suspend operator fun invoke(treeUri: String) = repository.connect(treeUri)
}

