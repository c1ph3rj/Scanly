package `in`.c1ph3rj.scanly.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext

@Singleton
class AppReleaseApkInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) {
    suspend fun downloadAndPromptInstall(
        downloadUrl: String,
        fileName: String,
    ): ScanlyResult<Unit> = withContext(dispatchers.io) {
        if (!canInstallPackages()) {
            openInstallPermissionSettings()
            return@withContext ScanlyResult.Failure(
                ScanlyError(
                    message = "Allow installs from Scanly in Settings, then tap Download again.",
                ),
            )
        }

        runCatching {
            val apkFile = downloadApk(downloadUrl = downloadUrl, fileName = fileName)
            promptInstall(apkFile)
        }.fold(
            onSuccess = { ScanlyResult.Success(Unit) },
            onFailure = { throwable ->
                ScanlyResult.Failure(
                    ScanlyError(
                        message = throwable.message ?: "Could not download the update.",
                        cause = throwable,
                    ),
                )
            },
        )
    }

    fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    private fun downloadApk(downloadUrl: String, fileName: String): File {
        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val apkFile = File(updatesDir, sanitizeFileName(fileName))
        if (apkFile.exists()) {
            apkFile.delete()
        }

        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            requestMethod = "GET"
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("User-Agent", "Scanly-Android")
        }

        try {
            if (connection.responseCode !in 200..299) {
                throw IOException("Download failed with HTTP ${connection.responseCode}.")
            }

            connection.inputStream.use { input ->
                apkFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        } finally {
            connection.disconnect()
        }

        if (!apkFile.exists() || apkFile.length() == 0L) {
            apkFile.delete()
            throw IOException("Downloaded APK file is empty.")
        }

        return apkFile
    }

    private fun promptInstall(apkFile: File) {
        val apkUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, APK_MIME_TYPE)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        context.startActivity(installIntent)
    }

    private fun sanitizeFileName(fileName: String): String =
        fileName.substringAfterLast('/').ifBlank { DEFAULT_APK_NAME }

    private companion object {
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
        const val DEFAULT_APK_NAME = "scanly-update.apk"
        const val REQUEST_TIMEOUT_MILLIS = 60_000
    }
}
