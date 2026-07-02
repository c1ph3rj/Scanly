package `in`.c1ph3rj.scanly.data.archive

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.domain.model.ArchiveOperation
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkPhase
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkState
import `in`.c1ph3rj.scanly.domain.model.BackupEstimate
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import `in`.c1ph3rj.scanly.domain.repository.LibraryArchiveRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultLibraryArchiveRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val archiveEngine: LibraryArchiveEngine,
    private val dispatchers: ScanlyDispatchers,
) : LibraryArchiveRepository {
    private val workManager = WorkManager.getInstance(context)

    override fun observeWorkState(): Flow<ArchiveWorkState> =
        workManager.getWorkInfosForUniqueWorkFlow(LibraryArchiveWorker.UNIQUE_WORK_NAME)
            .map { workInfos -> workInfos.lastOrNull()?.toArchiveState() ?: ArchiveWorkState() }

    override suspend fun estimateBackup(): ScanlyResult<BackupEstimate> =
        withContext(dispatchers.io) {
            runCatching { archiveEngine.estimateBackup() }.toScanlyResult("Could not estimate backup size.")
        }

    override suspend fun startBackup(): ScanlyResult<Unit> = withContext(dispatchers.io) {
        runCatching {
            val estimate = archiveEngine.estimateBackup()
            check(estimate.canBackup) { estimate.reason ?: "Backup is not available." }
            val request = OneTimeWorkRequestBuilder<LibraryArchiveWorker>()
                .setInputData(workDataOf(LibraryArchiveWorker.KEY_OPERATION to ArchiveOperation.BACKUP.name))
                .addTag(LibraryArchiveWorker.TAG_BACKUP)
                .build()
            workManager.enqueueUniqueWork(
                LibraryArchiveWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            Unit
        }.toScanlyResult("Could not start library backup.")
    }

    override suspend fun startRestore(uriString: String, mode: RestoreMode): ScanlyResult<Unit> =
        runCatching {
            val request = OneTimeWorkRequestBuilder<LibraryArchiveWorker>()
                .setInputData(
                    workDataOf(
                        LibraryArchiveWorker.KEY_OPERATION to ArchiveOperation.RESTORE.name,
                        LibraryArchiveWorker.KEY_SOURCE_URI to uriString,
                        LibraryArchiveWorker.KEY_RESTORE_MODE to mode.name,
                    ),
                )
                .addTag(LibraryArchiveWorker.TAG_RESTORE)
                .build()
            workManager.enqueueUniqueWork(
                LibraryArchiveWorker.UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request,
            )
            Unit
        }.toScanlyResult("Could not start library restore.")

    override suspend fun cancelActiveWork() {
        workManager.cancelUniqueWork(LibraryArchiveWorker.UNIQUE_WORK_NAME)
    }

    private fun WorkInfo.toArchiveState(): ArchiveWorkState {
        val operationName = progress.getString(LibraryArchiveWorker.KEY_OPERATION)
            ?: outputData.getString(LibraryArchiveWorker.KEY_OPERATION)
            ?: when {
                LibraryArchiveWorker.TAG_BACKUP in tags -> ArchiveOperation.BACKUP.name
                LibraryArchiveWorker.TAG_RESTORE in tags -> ArchiveOperation.RESTORE.name
                else -> null
            }
        val operation = operationName?.let { runCatching { ArchiveOperation.valueOf(it) }.getOrNull() }
        val progressPhase = progress.getString(LibraryArchiveWorker.KEY_PHASE)
            ?.let { runCatching { ArchiveWorkPhase.valueOf(it) }.getOrNull() }
        val phase = when (state) {
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> ArchiveWorkPhase.QUEUED
            WorkInfo.State.RUNNING -> progressPhase ?: ArchiveWorkPhase.QUEUED
            WorkInfo.State.SUCCEEDED -> ArchiveWorkPhase.SUCCEEDED
            WorkInfo.State.FAILED -> ArchiveWorkPhase.FAILED
            WorkInfo.State.CANCELLED -> ArchiveWorkPhase.CANCELLED
        }
        val running = state == WorkInfo.State.ENQUEUED || state == WorkInfo.State.BLOCKED ||
            state == WorkInfo.State.RUNNING
        return ArchiveWorkState(
            operation = operation,
            phase = phase,
            current = progress.getInt(LibraryArchiveWorker.KEY_CURRENT, 0),
            total = progress.getInt(LibraryArchiveWorker.KEY_TOTAL, 0),
            message = if (state.isFinished) {
                outputData.getString(LibraryArchiveWorker.KEY_MESSAGE)
            } else {
                progress.getString(LibraryArchiveWorker.KEY_MESSAGE)
            },
            isRunning = running,
            canCancel = running && phase != ArchiveWorkPhase.FINALIZING,
        )
    }

    private fun <T> Result<T>.toScanlyResult(fallback: String): ScanlyResult<T> = fold(
        onSuccess = { ScanlyResult.Success(it) },
        onFailure = { error ->
            ScanlyResult.Failure(ScanlyError(error.message ?: fallback, error))
        },
    )
}
