package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import javax.inject.Inject

class MovePageUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(
        pageId: String,
        targetIndex: Int,
    ): ScanlyResult<Unit> = pageRepository.movePage(pageId, targetIndex)
}
