package `in`.c1ph3rj.scanly.domain.rating

import `in`.c1ph3rj.scanly.domain.model.RatingPromptState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RatingPromptPolicyTest {
    private val day = 24L * 60L * 60L * 1_000L
    private val firstSeen = 1_700_000_000_000L
    private val now = firstSeen + 3 * day

    private fun state(
        firstSeenAtMillis: Long = firstSeen,
        positiveMoments: Int = 3,
        lastPromptAtMillis: Long? = null,
        dismissCount: Int = 0,
        hasRated: Boolean = false,
    ) = RatingPromptState(
        firstSeenAtMillis = firstSeenAtMillis,
        positiveMoments = positiveMoments,
        lastPromptAtMillis = lastPromptAtMillis,
        dismissCount = dismissCount,
        hasRated = hasRated,
    )

    @Test
    fun eligibleStatePrompts() {
        assertTrue(RatingPromptPolicy.shouldPrompt(state(), now))
    }

    @Test
    fun ratedUserIsNeverPrompted() {
        assertFalse(RatingPromptPolicy.shouldPrompt(state(hasRated = true), now))
    }

    @Test
    fun threeOrMoreDismissalsStopPrompts() {
        assertFalse(RatingPromptPolicy.shouldPrompt(state(dismissCount = 3), now + 365 * day))
        assertFalse(RatingPromptPolicy.shouldPrompt(state(dismissCount = 4), now + 365 * day))
    }

    @Test
    fun twoDismissalsStillAllowPromptWhenNoPromptWasShown() {
        assertTrue(RatingPromptPolicy.shouldPrompt(state(dismissCount = 2), now))
    }

    @Test
    fun needsThreeDaysSinceFirstSeen() {
        assertFalse(RatingPromptPolicy.shouldPrompt(state(), now - 1))
        assertTrue(RatingPromptPolicy.shouldPrompt(state(), firstSeen + 3 * day))
    }

    @Test
    fun needsThreePositiveMoments() {
        assertFalse(RatingPromptPolicy.shouldPrompt(state(positiveMoments = 2), now))
        assertTrue(RatingPromptPolicy.shouldPrompt(state(positiveMoments = 3), now))
        assertTrue(RatingPromptPolicy.shouldPrompt(state(positiveMoments = 10), now))
    }

    @Test
    fun noDismissalsWithRecentPromptWaitsThirtyDays() {
        val lastPrompt = now - 29 * day
        assertFalse(RatingPromptPolicy.shouldPrompt(state(lastPromptAtMillis = lastPrompt), now))
        assertTrue(RatingPromptPolicy.shouldPrompt(state(lastPromptAtMillis = now - 30 * day), now))
    }

    @Test
    fun oneDismissalWaitsThirtyDays() {
        assertFalse(
            RatingPromptPolicy.shouldPrompt(
                state(lastPromptAtMillis = now - 29 * day, dismissCount = 1),
                now,
            ),
        )
        assertTrue(
            RatingPromptPolicy.shouldPrompt(
                state(lastPromptAtMillis = now - 30 * day, dismissCount = 1),
                now,
            ),
        )
    }

    @Test
    fun twoDismissalsWaitSixtyDays() {
        assertFalse(
            RatingPromptPolicy.shouldPrompt(
                state(lastPromptAtMillis = now - 59 * day, dismissCount = 2),
                now,
            ),
        )
        assertTrue(
            RatingPromptPolicy.shouldPrompt(
                state(lastPromptAtMillis = now - 60 * day, dismissCount = 2),
                now,
            ),
        )
    }

    @Test
    fun oneDismissalDoesNotNeedSixtyDays() {
        assertTrue(
            RatingPromptPolicy.shouldPrompt(
                state(lastPromptAtMillis = now - 45 * day, dismissCount = 1),
                now,
            ),
        )
    }

    @Test
    fun unknownFirstSeenIsNotEligible() {
        assertFalse(RatingPromptPolicy.shouldPrompt(state(firstSeenAtMillis = 0L), now + 365 * day))
    }
}
