package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.RatingPromptRepository
import `in`.c1ph3rj.scanly.domain.rating.RatingPromptPolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/**
 * Emits whether the rating nudge is currently allowed. The clock is injectable so tests can pin
 * time; production wiring lives in `RatingModule`.
 */
class ObserveShouldShowRatingPromptUseCase(
    private val repository: RatingPromptRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    operator fun invoke(): Flow<Boolean> = combine(
        repository.observeState(),
        repository.observeSessionPositiveMoments(),
    ) { state, sessionMoments ->
        sessionMoments > 0 && RatingPromptPolicy.shouldPrompt(state, nowMillis())
    }
}
