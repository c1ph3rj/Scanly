package `in`.c1ph3rj.scanly.feature.rating

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
import `in`.c1ph3rj.scanly.BuildConfig
import `in`.c1ph3rj.scanly.core.common.PlayStoreLinks
import `in`.c1ph3rj.scanly.domain.model.AppUpdateChannel
import kotlin.coroutines.cancellation.CancellationException

/**
 * Opens the rating flow for the current distribution channel.
 *
 * Every caller uses this same action regardless of how the user feels about the app. There is
 * deliberately no branching on sentiment or star count, to stay within Google Play review policy.
 */
suspend fun launchRating(activity: Activity, channel: AppUpdateChannel) {
    if (channel == AppUpdateChannel.PLAY_STORE) {
        try {
            val manager = ReviewManagerFactory.create(activity)
            val reviewInfo = manager.requestReview()
            manager.launchReview(activity, reviewInfo)
            return
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            // Fall through to the store listing below.
        }
    }
    openStoreListing(activity)
}

/** The channel this build was produced for. */
fun currentRatingChannel(): AppUpdateChannel =
    AppUpdateChannel.fromBuildConfig(BuildConfig.UPDATE_CHANNEL)

private fun openStoreListing(context: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse(PlayStoreLinks.MARKET_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(market)
        return
    } catch (_: ActivityNotFoundException) {
        // No Play Store app available; use the browser listing instead.
    }
    val web = Intent(Intent.ACTION_VIEW, Uri.parse(PlayStoreLinks.LISTING_URL))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(web)
    } catch (_: ActivityNotFoundException) {
        // Nothing can open the listing on this device.
    }
}
