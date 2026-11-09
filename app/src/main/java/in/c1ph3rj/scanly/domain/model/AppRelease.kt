package `in`.c1ph3rj.scanly.domain.model

import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

data class AppRelease(
    val tagName: String,
    val title: String,
    val bodyMarkdown: String,
    val htmlUrl: String,
    val publishedAt: String?,
    val apkAsset: AppReleaseAsset?,
)

data class AppReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long?,
)

enum class AppUpdateChannel(
    @param:StringRes val sourceLabelRes: Int,
) {
    GITHUB(R.string.app_update_channel_github),
    PLAY_STORE(R.string.app_update_channel_google_play),
    ;

    val sourceLabel: String get() = appString(sourceLabelRes)


    companion object {
        fun fromBuildConfig(value: String): AppUpdateChannel = when (value) {
            "github" -> GITHUB
            "playStore" -> PLAY_STORE
            else -> error("Unsupported update channel: $value")
        }
    }
}

data class AppUpdateCheckResult(
    val installedVersionName: String,
    val latestRelease: AppRelease,
    val updateAvailable: Boolean,
    val channel: AppUpdateChannel,
    val playUpdateType: PlayInAppUpdateType? = null,
    val availableVersionCode: Int? = null,
)
