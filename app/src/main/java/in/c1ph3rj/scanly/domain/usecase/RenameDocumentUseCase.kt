package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import javax.inject.Inject

class RenameDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
) {
    suspend operator fun invoke(
        documentId: String,
        title: String,
    ): ScanlyResult<Unit> = documentRepository.renameDocument(documentId, title)
}
