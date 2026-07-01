package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.AppDataRepository
import javax.inject.Inject

class ClearTemporaryCacheUseCase @Inject constructor(
    private val repository: AppDataRepository,
) {
    suspend operator fun invoke() = repository.clearTemporaryCache()
}

