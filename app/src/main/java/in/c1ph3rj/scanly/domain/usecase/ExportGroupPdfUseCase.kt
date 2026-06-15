package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import javax.inject.Inject

class ExportGroupPdfUseCase @Inject constructor(
    private val documentExportRepository: DocumentExportRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (current: Int, total: Int) -> Unit,
    ): ScanlyResult<ExportArtifact> =
        documentExportRepository.exportGroupAsSinglePdf(groupId, options, onProgress)
}
