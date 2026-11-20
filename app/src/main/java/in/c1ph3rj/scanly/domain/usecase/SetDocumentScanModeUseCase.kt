package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import javax.inject.Inject

class SetDocumentScanModeUseCase @Inject constructor(
    private val documentRepository: DocumentRepository,
) {
    suspend operator fun invoke(
        documentId: String,
        scanMode: ScanMode,
    ): ScanlyResult<Unit> = documentRepository.setPreferredScanMode(documentId, scanMode)
}
