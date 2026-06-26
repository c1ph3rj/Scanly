package `in`.c1ph3rj.scanly.data.update

import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppRelease
import `in`.c1ph3rj.scanly.domain.model.AppReleaseAsset
import `in`.c1ph3rj.scanly.domain.repository.AppUpdateRepository
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.withContext
import org.json.JSONObject

@Singleton
class GitHubReleaseUpdateRepository @Inject constructor(
    private val dispatchers: ScanlyDispatchers,
) : AppUpdateRepository {

    override suspend fun fetchLatestRelease(): ScanlyResult<AppRelease> =
        withContext(dispatchers.io) {
            runCatching {
                parseRelease(fetchLatestReleaseJson())
            }.fold(
                onSuccess = { release -> ScanlyResult.Success(release) },
                onFailure = { throwable ->
                    ScanlyResult.Failure(
                        ScanlyError(
                            message = throwable.message ?: "Could not check GitHub releases.",
                            cause = throwable,
                        ),
                    )
                },
            )
        }

    private fun fetchLatestReleaseJson(): String {
        val connection = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
            connectTimeout = REQUEST_TIMEOUT_MILLIS
            readTimeout = REQUEST_TIMEOUT_MILLIS
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "Scanly-Android")
        }

        return try {
            val responseBody = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            if (connection.responseCode !in 200..299) {
                throw IOException("GitHub returned HTTP ${connection.responseCode}.")
            }

            responseBody
        } finally {
            connection.disconnect()
        }
    }

    private fun parseRelease(json: String): AppRelease {
        val releaseJson = JSONObject(json)
        val assetsJson = releaseJson.optJSONArray("assets")
        var apkAsset: AppReleaseAsset? = null

        if (assetsJson != null) {
            for (index in 0 until assetsJson.length()) {
                val assetJson = assetsJson.getJSONObject(index)
                val assetName = assetJson.optString("name")
                val contentType = assetJson.optString("content_type")
                val downloadUrl = assetJson.optString("browser_download_url")
                val isApk = assetName.endsWith(".apk", ignoreCase = true) ||
                    contentType == APK_CONTENT_TYPE

                if (isApk && downloadUrl.isNotBlank()) {
                    apkAsset = AppReleaseAsset(
                        name = assetName,
                        downloadUrl = downloadUrl,
                        sizeBytes = assetJson.optLong("size").takeIf { it > 0L },
                    )
                    break
                }
            }
        }

        return AppRelease(
            tagName = releaseJson.getString("tag_name"),
            title = releaseJson.optString("name").ifBlank { releaseJson.getString("tag_name") },
            bodyMarkdown = releaseJson.optString("body"),
            htmlUrl = releaseJson.getString("html_url"),
            publishedAt = releaseJson.optString("published_at").takeIf { it.isNotBlank() },
            apkAsset = apkAsset,
        )
    }

    private companion object {
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/c1ph3rj/Scanly/releases/latest"
        const val APK_CONTENT_TYPE = "application/vnd.android.package-archive"
        const val REQUEST_TIMEOUT_MILLIS = 10_000
    }
}
