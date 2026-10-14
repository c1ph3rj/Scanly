package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.model.RatingPromptState
import `in`.c1ph3rj.scanly.domain.repository.RatingPromptRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow

class ObserveRatingPromptStateUseCase @Inject constructor(
    private val repository: RatingPromptRepository,
) {
    operator fun invoke(): Flow<RatingPromptState> = repository.observeState()
}
