package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppUpdateCheckResult
import `in`.c1ph3rj.scanly.domain.repository.AppUpdateRepository
import javax.inject.Inject

class CheckForAppUpdateUseCase @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
) {
    suspend operator fun invoke(): ScanlyResult<AppUpdateCheckResult> =
        appUpdateRepository.checkForUpdate()
}