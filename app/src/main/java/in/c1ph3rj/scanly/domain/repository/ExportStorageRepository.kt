package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.SavedExport

interface ExportStorageRepository {
    suspend fun saveExport(artifact: ExportArtifact): ScanlyResult<SavedExport>
}
