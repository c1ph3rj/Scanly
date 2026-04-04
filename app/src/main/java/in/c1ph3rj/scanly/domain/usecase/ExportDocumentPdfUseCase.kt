package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import javax.inject.Inject

class ExportDocumentPdfUseCase @Inject constructor(
    private val documentExportRepository: DocumentExportRepository,
) {
    suspend operator fun invoke(
        documentId: String,
        options: PdfExportOptions,
    ): ScanlyResult<ExportArtifact> = documentExportRepository.exportPdf(documentId, options)
}
