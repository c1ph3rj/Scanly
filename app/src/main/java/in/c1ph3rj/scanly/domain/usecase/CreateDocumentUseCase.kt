package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import javax.inject.Inject

class CreateDocumentUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
) {
    /**
     * Creates an empty document. A non-default [scanMode] is stored as the document's preferred
     * mode so the camera opens in it; failing to store it never fails the create.
     */
    suspend operator fun invoke(
        title: String,
        groupId: String? = null,
        scanMode: ScanMode = ScanMode.DOCUMENT,
    ): ScanlyResult<String> {
        val created = documentRepository.createDocument(title, groupId)
        if (created is ScanlyResult.Success && scanMode != ScanMode.DOCUMENT) {
            documentRepository.setPreferredScanMode(created.value, scanMode)
        }
        return created
    }

    suspend fun createImported(groupId: String? = null): ScanlyResult<String> =
        documentRepository.createImportedDocument(groupId)
}
