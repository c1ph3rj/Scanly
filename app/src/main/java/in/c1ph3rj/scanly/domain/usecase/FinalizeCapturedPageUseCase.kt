package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PageCaptureDraft
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import javax.inject.Inject

class FinalizeCapturedPageUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    suspend operator fun invoke(draft: PageCaptureDraft): ScanlyResult<String> =
        pageRepository.finalizeCapture(draft)
}
