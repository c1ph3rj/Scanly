package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.PipPreference
import `in`.c1ph3rj.scanly.domain.model.PipPreferences
import `in`.c1ph3rj.scanly.domain.repository.PipRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObservePipPreferencesUseCase @Inject constructor(
    private val pipRepository: PipRepository,
) {
    operator fun invoke(): Flow<PipPreferences> = pipRepository.observePreferences()
}

class SetPipPreferenceUseCase @Inject constructor(
    private val pipRepository: PipRepository,
) {
    suspend operator fun invoke(preference: PipPreference, enabled: Boolean) =
        pipRepository.setPreference(preference, enabled)
}

class DismissPipTipUseCase @Inject constructor(
    private val pipRepository: PipRepository,
) {
    suspend operator fun invoke(epochDay: Long) = pipRepository.dismissTip(epochDay)
}
