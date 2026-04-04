package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import javax.inject.Inject

class ExportDocumentImageArchiveUseCase @Inject constructor(
    private val documentExportRepository: DocumentExportRepository,
) {
    suspend operator fun invoke(documentId: String): ScanlyResult<ExportArtifact> =
        documentExportRepository.exportImageArchive(documentId)
}
