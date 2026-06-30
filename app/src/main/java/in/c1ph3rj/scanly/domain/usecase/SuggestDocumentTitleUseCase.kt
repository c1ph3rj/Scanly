package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.DocumentTitleFormat
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import javax.inject.Inject

class SuggestDocumentTitleUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
) {
    suspend operator fun invoke(
        format: DocumentTitleFormat = DocumentTitleFormat.default,
    ): String = documentRepository.suggestDocumentTitle(format)
}