package `in`.c1ph3rj.scanly.domain.usecase

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.AppVersionComparator
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppUpdateCheckResult
import `in`.c1ph3rj.scanly.domain.repository.AppUpdateRepository
import javax.inject.Inject

class CheckForAppUpdateUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appUpdateRepository: AppUpdateRepository,
) {
    suspend operator fun invoke(): ScanlyResult<AppUpdateCheckResult> =
        when (val result = appUpdateRepository.fetchLatestRelease()) {
            is ScanlyResult.Success -> {
                val installedVersionName = installedVersionName()
                ScanlyResult.Success(
                    AppUpdateCheckResult(
                        installedVersionName = installedVersionName,
                        latestRelease = result.value,
                        updateAvailable = AppVersionComparator.isRemoteNewer(
                            installedVersion = installedVersionName,
                            remoteVersion = result.value.tagName,
                        ),
                    ),
                )
            }

            is ScanlyResult.Failure -> result
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
