package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.DocumentSplitRepository
import javax.inject.Inject

/** Splits a library document into new documents; see [DocumentSplitRepository.splitDocument]. */
class SplitDocumentUseCase @Inject constructor(
    private val repository: DocumentSplitRepository,
) {
    suspend operator fun invoke(
        documentId: String,
        parts: List<List<Int>>,
        keepOriginal: Boolean,
    ): ScanlyResult<List<String>> = repository.splitDocument(documentId, parts, keepOriginal)
}
