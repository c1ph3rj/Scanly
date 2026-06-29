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

data class AppUpdateCheckResult(
    val installedVersionName: String,
    val latestRelease: AppRelease,
    val updateAvailable: Boolean,
    val playUpdateType: PlayInAppUpdateType? = null,
    val availableVersionCode: Int? = null,
)
