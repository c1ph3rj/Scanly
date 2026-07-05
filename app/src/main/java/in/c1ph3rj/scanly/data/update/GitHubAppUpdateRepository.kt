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
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GitHubAppUpdateRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val releaseNotesRepository: AppReleaseNotesRepository,
) : AppUpdateRepository {

    override suspend fun checkForUpdate(): ScanlyResult<AppUpdateCheckResult> =
        when (val releaseResult = releaseNotesRepository.fetchLatestReleaseNotes()) {
            is ScanlyResult.Failure -> releaseResult
            is ScanlyResult.Success -> ScanlyResult.Success(
                createGitHubUpdateCheckResult(
                    installedVersionName = installedVersionName(),
                    latestRelease = releaseResult.value,
                ),
            )
        }

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
}

internal fun createGitHubUpdateCheckResult(
    installedVersionName: String,
    latestRelease: AppRelease,
): AppUpdateCheckResult = AppUpdateCheckResult(
    installedVersionName = installedVersionName,
    latestRelease = latestRelease,
    updateAvailable = AppVersionComparator.isRemoteNewer(
        installedVersion = installedVersionName,
        remoteVersion = latestRelease.tagName,
    ),
    channel = AppUpdateChannel.GITHUB,
)
