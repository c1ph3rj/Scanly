package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.AppStorageUsage
import `in`.c1ph3rj.scanly.domain.repository.AppDataRepository
import javax.inject.Inject

class GetAppStorageUsageUseCase @Inject constructor(
    private val appDataRepository: AppDataRepository,
) {
    suspend operator fun invoke(): ScanlyResult<AppStorageUsage> =
        appDataRepository.getStorageUsage()
}
