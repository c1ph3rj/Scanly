package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.SettingsContent
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import javax.inject.Inject

class LoadSettingsContentUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(): ScanlyResult<SettingsContent> =
        settingsRepository.loadSettingsContent()
}
