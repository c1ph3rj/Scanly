package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.RatingPromptRepository
import javax.inject.Inject

class DismissSettingsRatingCardUseCase @Inject constructor(
    private val repository: RatingPromptRepository,
) {
    suspend operator fun invoke() {
        repository.recordSettingsCardDismissed()
    }
}
