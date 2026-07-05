package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.SavedExport
import `in`.c1ph3rj.scanly.domain.repository.ExportStorageRepository
import javax.inject.Inject

class SaveExportArtifactUseCase @Inject constructor(
    private val exportStorageRepository: ExportStorageRepository,
) {
    suspend operator fun invoke(artifact: ExportArtifact): ScanlyResult<SavedExport> =
        exportStorageRepository.saveExport(artifact)
}
