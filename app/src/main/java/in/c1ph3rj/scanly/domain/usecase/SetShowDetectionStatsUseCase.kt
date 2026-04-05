package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import javax.inject.Inject

class SetShowDetectionStatsUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean): ScanlyResult<Unit> =
        settingsRepository.setShowDetectionStats(enabled)
}
