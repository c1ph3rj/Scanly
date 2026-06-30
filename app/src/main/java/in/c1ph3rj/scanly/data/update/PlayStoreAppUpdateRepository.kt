package `in`.c1ph3rj.scanly.data.update

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.AppVersionComparator
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppRelease
import `in`.c1ph3rj.scanly.domain.model.AppUpdateChannel
import `in`.c1ph3rj.scanly.domain.model.AppUpdateCheckResult
import `in`.c1ph3rj.scanly.domain.repository.AppReleaseNotesRepository
import `in`.c1ph3rj.scanly.domain.repository.AppUpdateRepository
import `in`.c1ph3rj.scanly.domain.repository.PlayInAppUpdateCoordinator
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayStoreAppUpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val playInAppUpdateCoordinator: PlayInAppUpdateCoordinator,
    private val releaseNotesRepository: AppReleaseNotesRepository,
) : AppUpdateRepository {

    override suspend fun checkForUpdate(): ScanlyResult<AppUpdateCheckResult> {
        val installedVersionName = installedVersionName()
        return when (val availabilityResult = playInAppUpdateCoordinator.refreshAvailability()) {
            is ScanlyResult.Failure -> availabilityResult
            is ScanlyResult.Success -> {
                val availability = availabilityResult.value
                val releaseNotes = resolveReleaseNotes(
                    installedVersionName = installedVersionName,
                    updateAvailable = availability.updateAvailable,
                )
                ScanlyResult.Success(
                    AppUpdateCheckResult(
                        installedVersionName = installedVersionName,
                        latestRelease = releaseNotes,
                        updateAvailable = availability.updateAvailable &&
                            availability.recommendedUpdateType != null,
                        channel = AppUpdateChannel.PLAY_STORE,
                        playUpdateType = availability.recommendedUpdateType,
                        availableVersionCode = availability.availableVersionCode,
                    ),
                )
            }
        }
    }

    private suspend fun resolveReleaseNotes(
        installedVersionName: String,
        updateAvailable: Boolean,
    ): AppRelease {
        val releaseNotesResult = releaseNotesRepository.fetchLatestReleaseNotes()
        if (releaseNotesResult is ScanlyResult.Success) {
            val release = releaseNotesResult.value
            if (!updateAvailable) {
                return release
            }
            if (AppVersionComparator.isRemoteNewer(installedVersionName, release.tagName)) {
                return release
            }
        }

        return fallbackPlayRelease()
    }

    private fun fallbackPlayRelease(): AppRelease = AppRelease(
        tagName = "Latest",
        title = "Update available",
        bodyMarkdown = """
            A new version of Scanly is available on Google Play.

            Tap **Update** to download and install the update without leaving the app.
        """.trimIndent(),
        htmlUrl = PLAY_STORE_LISTING_URL,
        publishedAt = null,
        apkAsset = null,
    )

    private fun installedVersionName(): String {
        val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                android.content.pm.PackageManager.PackageInfoFlags.of(0),
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
        return packageInfo.versionName ?: "0"
    }

    private companion object {
        const val PLAY_STORE_LISTING_URL =
            "https://play.google.com/store/apps/details?id=in.c1ph3rj.scanly"
    }
}
