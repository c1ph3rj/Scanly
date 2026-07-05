package `in`.c1ph3rj.scanly.domain.model

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
    val sourceLabel: String,
) {
    GITHUB("GitHub"),
    PLAY_STORE("Google Play"),
    ;

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
