package `in`.c1ph3rj.scanly.domain.repository

import `in`.c1ph3rj.scanly.domain.model.RatingPromptState
import kotlinx.coroutines.flow.Flow

interface RatingPromptRepository {
    fun observeState(): Flow<RatingPromptState>

    suspend fun recordFirstSeenIfAbsent(nowMillis: Long)

    suspend fun recordPositiveMoment()

    /**
     * Positive moments since the app process started. The nudge only follows a success the
     * user just had, so it never greets them on launch.
     */
    fun observeSessionPositiveMoments(): Flow<Int>

    suspend fun recordPromptShown(nowMillis: Long)

    suspend fun recordDismissed()

    suspend fun recordRated()

    /** Hides the friendly rating card in Settings. The Settings row stays available. */
    suspend fun recordSettingsCardDismissed()
}
