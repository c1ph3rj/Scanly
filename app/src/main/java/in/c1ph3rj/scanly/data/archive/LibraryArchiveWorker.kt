package `in`.c1ph3rj.scanly.data.archive

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import `in`.c1ph3rj.scanly.MainActivity
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.domain.model.ArchiveOperation
import `in`.c1ph3rj.scanly.domain.model.ArchiveWorkPhase
import `in`.c1ph3rj.scanly.domain.model.RestoreMode
import kotlinx.coroutines.CancellationException

@HiltWorker
class LibraryArchiveWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParameters: WorkerParameters,
    private val archiveEngine: LibraryArchiveEngine,
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val operation = ArchiveOperation.valueOf(
            inputData.getString(KEY_OPERATION) ?: return failure("Missing archive operation."),
        )
        setForeground(createForegroundInfo(operation, ArchiveWorkPhase.QUEUED, 0, 0))
        return try {
            val progress: ArchiveProgressCallback = { phase, current, total, message ->
                setProgress(
                    workDataOf(
                        KEY_OPERATION to operation.name,
                        KEY_PHASE to phase.name,
                        KEY_CURRENT to current,
                        KEY_TOTAL to total,
                        KEY_MESSAGE to message,
                    ),
                )
                setForeground(createForegroundInfo(operation, phase, current, total))
            }
            val completionMessage = when (operation) {
                ArchiveOperation.BACKUP -> {
                    val fileName = archiveEngine.createBackup(progress)
                    "Backup saved as $fileName"
                }

                ArchiveOperation.RESTORE -> {
                    val uri = inputData.getString(KEY_SOURCE_URI)?.let(Uri::parse)
                        ?: error("Missing restore file.")
                    val mode = RestoreMode.valueOf(
                        inputData.getString(KEY_RESTORE_MODE) ?: error("Missing restore mode."),
                    )
                    archiveEngine.restore(uri, mode, id.toString(), progress)
                    if (mode == RestoreMode.REPLACE) {
                        "Library restored."
                    } else {
                        "Backup merged into the library."
                    }
                }
            }
            Result.success(workDataOf(KEY_MESSAGE to completionMessage, KEY_OPERATION to operation.name))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            failure(error.message ?: "Library ${operation.name.lowercase()} failed.")
        }
    }

    private fun failure(message: String): Result =
        Result.failure(workDataOf(KEY_MESSAGE to message))

    private fun createForegroundInfo(
        operation: ArchiveOperation,
        phase: ArchiveWorkPhase,
        current: Int,
        total: Int,
    ): ForegroundInfo {
        createNotificationChannel()
        val title = if (operation == ArchiveOperation.BACKUP) {
            "Backing up Scanly library"
        } else {
            "Restoring Scanly library"
        }
        val progressText = when (phase) {
            ArchiveWorkPhase.VALIDATING -> "Validating"
            ArchiveWorkPhase.ARCHIVING -> "Compressing library"
            ArchiveWorkPhase.RESTORING -> "Restoring files"
            ArchiveWorkPhase.FINALIZING -> "Finishing"
            else -> "Preparing"
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            Intent(applicationContext, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(applicationContext, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(progressText)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(total.coerceAtLeast(0), current.coerceAtLeast(0), total <= 0)
            .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun createNotificationChannel() {
        val manager = applicationContext.getSystemService(Service.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Library backup and restore",
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "scanly-library-archive"
        const val KEY_OPERATION = "operation"
        const val KEY_SOURCE_URI = "source_uri"
        const val KEY_RESTORE_MODE = "restore_mode"
        const val KEY_PHASE = "phase"
        const val KEY_CURRENT = "current"
        const val KEY_TOTAL = "total"
        const val KEY_MESSAGE = "message"
        const val TAG_BACKUP = "scanly-library-backup"
        const val TAG_RESTORE = "scanly-library-restore"
        private const val NOTIFICATION_CHANNEL_ID = "library_archive"
        private const val NOTIFICATION_ID = 4102
    }
}
