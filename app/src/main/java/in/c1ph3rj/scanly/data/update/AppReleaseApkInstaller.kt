package `in`.c1ph3rj.scanly.data.update

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Singleton
class AppReleaseApkInstaller @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) {
    suspend fun enqueueBackgroundDownload(
        downloadUrl: String,
        fileName: String,
        releaseTag: String,
    ): ScanlyResult<Long> = withContext(dispatchers.io) {
        runCatching {
            val safeFileName = sanitizeFileName(fileName)
            deleteExistingDownload(safeFileName)

            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setMimeType(APK_MIME_TYPE)
                .setTitle("Scanly $releaseTag")
                .setDescription("Downloading $safeFileName")
                .setNotificationVisibility(
                    DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED,
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    safeFileName,
                )
                .addRequestHeader("Accept", "application/octet-stream")
                .addRequestHeader("User-Agent", "Scanly-Android")

            val downloadManager = context.getSystemService(DownloadManager::class.java)
                ?: error("System download service is unavailable.")
            downloadManager.enqueue(request)
        }.fold(
            onSuccess = { downloadId -> ScanlyResult.Success(downloadId) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Could not start the update download.",
                        cause = throwable,
                    ),
                )
            },
        )
    }

    suspend fun waitForDownloadComplete(
        downloadId: Long,
        fileName: String,
        timeoutMs: Long = TEN_MINUTES_MS,
    ): ScanlyResult<File> = withContext(dispatchers.io) {
        val downloadManager = context.getSystemService(DownloadManager::class.java)
            ?: return@withContext ScanlyResult.Failure(
                ScanlyError(message = "System download service is unavailable."),
            )

        val safeFileName = sanitizeFileName(fileName)
        val deadline = System.currentTimeMillis() + timeoutMs

        while (System.currentTimeMillis() < deadline) {
            val query = DownloadManager.Query().setFilterById(downloadId)
            downloadManager.query(query).use { cursor ->
                if (!cursor.moveToFirst()) {
                    return@withContext ScanlyResult.Failure(
                        ScanlyError(message = "Download was removed before it finished."),
                    )
                }

                when (cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        val apkFile = resolveApkFile(safeFileName)
                        return@withContext if (apkFile != null) {
                            ScanlyResult.Success(apkFile)
                        } else {
                            ScanlyResult.Failure(
                                ScanlyError(message = "Download finished but the APK file is missing."),
                            )
                        }
                    }

                    DownloadManager.STATUS_FAILED -> {
                        val reason = cursor.getInt(
                            cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON),
                        )
                        return@withContext ScanlyResult.Failure(
                            ScanlyError(message = downloadFailureMessage(reason)),
                        )
                    }
                }
            }

            delay(POLL_INTERVAL_MS)
        }

        ScanlyResult.Failure(
            ScanlyError(message = "Download is taking longer than expected. Try again from Settings."),
        )
    }

    fun canRequestPackageInstalls(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun createInstallPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    fun createInstallIntent(apkFile: File): ScanlyResult<Intent> = runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }.fold(
        onSuccess = { intent -> ScanlyResult.Success(intent) },
        onFailure = { throwable ->
            ScanlyResult.Failure(
                ScanlyError(
                    message = throwable.message ?: "Could not prepare the update installer.",
                    cause = throwable,
                ),
            )
        },
    )

    private fun resolveApkFile(fileName: String): File? {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: return null
        return File(downloadsDir, fileName).takeIf { file ->
            file.exists() && file.length() > 0L
        }
    }

    private fun sanitizeFileName(fileName: String): String =
        fileName.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: DEFAULT_APK_NAME

    private fun deleteExistingDownload(fileName: String) {
        resolveApkFile(fileName)?.delete()
    }

    private fun downloadFailureMessage(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Download could not be resumed."
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "No storage is available for the update download."
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "An update file already exists."
        DownloadManager.ERROR_FILE_ERROR -> "The update file could not be written."
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "The update download was interrupted."
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage for the update download."
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "The update download redirected too many times."
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The update server returned an unexpected response."
        else -> "The update download failed."
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DEFAULT_APK_NAME = "scanly-update.apk"
        const val POLL_INTERVAL_MS = 750L
        const val TEN_MINUTES_MS = 10 * 60 * 1000L
    }
}
