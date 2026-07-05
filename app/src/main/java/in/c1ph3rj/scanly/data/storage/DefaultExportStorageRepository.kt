package `in`.c1ph3rj.scanly.data.storage

import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.model.SavedExport
import `in`.c1ph3rj.scanly.domain.repository.ExportStorageRepository
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultExportStorageRepository @Inject constructor(
    private val destinationManager: SharedStorageDestinationManager,
    private val dispatchers: ScanlyDispatchers,
) : ExportStorageRepository {
    override suspend fun saveExport(artifact: ExportArtifact): ScanlyResult<SavedExport> =
        withContext(dispatchers.io) {
            runCatching {
                destinationManager.saveExport(
                    source = File(artifact.filePath),
                    fileName = artifact.fileName,
                    mimeType = artifact.mimeType,
                )
            }.fold(
                onSuccess = { ScanlyResult.Success(it) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not save the exported file.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }
}
