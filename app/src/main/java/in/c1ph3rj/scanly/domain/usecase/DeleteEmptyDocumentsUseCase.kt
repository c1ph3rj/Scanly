package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import javax.inject.Inject

class DeleteEmptyDocumentsUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
) {
    /** @return number of empty documents removed */
    suspend operator fun invoke(): ScanlyResult<Int> =
        documentRepository.deleteEmptyDocuments()
}
