package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import javax.inject.Inject

class DeletePageUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(pageId: String): ScanlyResult<Unit> =
        pageRepository.deletePage(pageId)
}
