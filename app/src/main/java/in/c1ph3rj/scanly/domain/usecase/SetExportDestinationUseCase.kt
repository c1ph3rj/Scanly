package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportDestination
import `in`.c1ph3rj.scanly.domain.repository.SettingsRepository
import javax.inject.Inject

class SetExportDestinationUseCase @Inject constructor(
    private val settingsRepository: SettingsRepository,
) {
    suspend operator fun invoke(destination: ExportDestination.CustomTree): ScanlyResult<Unit> =
        settingsRepository.setExportDestination(destination)
}
