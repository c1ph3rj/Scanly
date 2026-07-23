package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import javax.inject.Inject

class CreateDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
) {
    suspend operator fun invoke(
        title: String,
        groupId: String? = null,
        initialScanMode: ScanMode = ScanMode.DOCUMENT,
    ): ScanlyResult<String> =
        documentRepository.createDocument(title, groupId, initialScanMode)

    suspend fun createImported(groupId: String? = null): ScanlyResult<String> =
        documentRepository.createImportedDocument(groupId)
}
