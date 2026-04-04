package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import javax.inject.Inject

class PrepareDocumentImageShareUseCase @Inject constructor(
    private val documentExportRepository: DocumentExportRepository,
) {
    suspend operator fun invoke(documentId: String): ScanlyResult<ShareArtifact> =
        documentExportRepository.prepareImageShare(documentId)
}
