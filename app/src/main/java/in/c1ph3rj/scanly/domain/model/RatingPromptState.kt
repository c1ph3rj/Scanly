package `in`.c1ph3rj.scanly.domain.model

data class RatingPromptState(
    val firstSeenAtMillis: Long,
    val positiveMoments: Int,
    val lastPromptAtMillis: Long?,
    val dismissCount: Int,
    val hasRated: Boolean,
    val settingsCardDismissed: Boolean = false,
)
