package `in`.c1ph3rj.scanly.data.update

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
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

    private fun sanitizeFileName(fileName: String): String =
        fileName.substringAfterLast('/')
            .replace(Regex("[^A-Za-z0-9._-]"), "_")
            .takeIf { it.endsWith(".apk", ignoreCase = true) }
            ?: DEFAULT_APK_NAME

    private fun deleteExistingDownload(fileName: String) {
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: return
        File(downloadsDir, fileName).takeIf { it.exists() }?.delete()
    }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DEFAULT_APK_NAME = "scanly-update.apk"
    }
}
