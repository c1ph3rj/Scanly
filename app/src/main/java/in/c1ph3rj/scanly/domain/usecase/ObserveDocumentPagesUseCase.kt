package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.ScanPage
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveDocumentPagesUseCase @Inject constructor(
    private val pageRepository: PageRepository,
) {
    operator fun invoke(documentId: String): Flow<List<ScanPage>> =
        pageRepository.observePages(documentId)
}
