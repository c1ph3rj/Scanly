package `in`.c1ph3rj.scanly.domain.rating

import `in`.c1ph3rj.scanly.domain.model.RatingPromptState

/**
 * Decides whether the in-app "Rate Scanly" nudge may be shown right now.
 *
 * Pure Kotlin on purpose so every rule can be unit tested without Android.
 */
object RatingPromptPolicy {
    const val MIN_DAYS_SINCE_FIRST_SEEN = 3
    const val MIN_POSITIVE_MOMENTS = 3
    const val MAX_DISMISSALS = 3
    const val REPROMPT_INTERVAL_DAYS = 30
    const val REPROMPT_INTERVAL_AFTER_REPEATED_DISMISSALS_DAYS = 60
    const val REPEATED_DISMISSAL_THRESHOLD = 2

    private const val MILLIS_PER_DAY = 24L * 60L * 60L * 1_000L

    fun shouldPrompt(state: RatingPromptState, nowMillis: Long): Boolean {
        if (state.hasRated) return false
        if (state.dismissCount >= MAX_DISMISSALS) return false
        // Unknown first-use time (not recorded yet) must not read as "used since 1970".
        if (state.firstSeenAtMillis <= 0L) return false
        if (nowMillis - state.firstSeenAtMillis < MIN_DAYS_SINCE_FIRST_SEEN * MILLIS_PER_DAY) return false
        if (state.positiveMoments < MIN_POSITIVE_MOMENTS) return false

        val lastPromptAt = state.lastPromptAtMillis
        if (lastPromptAt != null) {
            val intervalDays = if (state.dismissCount >= REPEATED_DISMISSAL_THRESHOLD) {
                REPROMPT_INTERVAL_AFTER_REPEATED_DISMISSALS_DAYS
            } else {
                REPROMPT_INTERVAL_DAYS
            }
            if (nowMillis - lastPromptAt < intervalDays * MILLIS_PER_DAY) return false
        }
        return true
    }
}
