package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.PdfExportOptions
import `in`.c1ph3rj.scanly.domain.model.ShareArtifact
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import javax.inject.Inject

class PrepareGroupZippedPdfsShareUseCase @Inject constructor(
    private val documentExportRepository: DocumentExportRepository,
) {
    suspend operator fun invoke(
        groupId: String,
        options: PdfExportOptions,
        onProgress: (currentDoc: Int, totalDocs: Int) -> Unit,
    ): ScanlyResult<ShareArtifact> =
        documentExportRepository.prepareGroupZippedPdfsShare(groupId, options, onProgress)
}
