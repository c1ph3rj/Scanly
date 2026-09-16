package `in`.c1ph3rj.scanly.testing

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.data.archive.LibraryArchivePolicy
import `in`.c1ph3rj.scanly.domain.model.ArchiveOperation
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkPhase
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkState
import `in`.c1ph3rj.scanly.domain.model.BackupEstimate
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import `in`.c1ph3rj.scanly.domain.repository.LibraryArchiveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class InMemoryLibraryArchiveRepository(
    private val library: InMemoryScanlyLibrary,
) : LibraryArchiveRepository {
    private val workState = MutableStateFlow(ArchiveWorkState())
    private val backups = mutableMapOf<String, LibrarySnapshot>()

    var lastBackupUri: String? = null
        private set
    var lastRestoreUri: String? = null
        private set
    var lastRestoreMode: RestoreMode? = null
        private set

    override fun observeWorkState(): Flow<ArchiveWorkState> = workState.asStateFlow()

    override suspend fun estimateBackup(): ScanlyResult<BackupEstimate> =
        runLibraryResult("Could not estimate backup.") {
            val sourceBytes = (library.pages.value.size * 1_024L).coerceAtLeast(1L)
            val requiredBytes = LibraryArchivePolicy.backupRequiredBytes(sourceBytes)
            BackupEstimate(
                sourceBytes = sourceBytes,
                requiredBytes = requiredBytes,
                availableBytes = requiredBytes + 1L,
                documentCount = library.documents.value.size,
                pageCount = library.pages.value.size,
                destinationLabel = "Downloads/Scanly/backup",
                canBackup = true,
            )
        }

    override suspend fun startBackup(): ScanlyResult<Unit> =
        runLibraryResult("Could not start backup.") {
            val uri = "memory://backup-${backups.size + 1}"
            backups[uri] = library.snapshot()
            lastBackupUri = uri
            workState.value = ArchiveWorkState(
                operation = ArchiveOperation.BACKUP,
                phase = ArchiveWorkPhase.SUCCEEDED,
                message = uri,
            )
        }

    override suspend fun startRestore(uriString: String, mode: RestoreMode): ScanlyResult<Unit> =
        runLibraryResult("Could not start restore.") {
            val snapshot = backups[uriString] ?: error("Backup not found.")
            lastRestoreUri = uriString
            lastRestoreMode = mode
            library.restore(snapshot, mode)
            workState.value = ArchiveWorkState(
                operation = ArchiveOperation.RESTORE,
                phase = ArchiveWorkPhase.SUCCEEDED,
                message = mode.name,
            )
        }

    override suspend fun cancelActiveWork() {
        workState.value = ArchiveWorkState(phase = ArchiveWorkPhase.CANCELLED)
    }
}
