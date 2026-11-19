package `in`.c1ph3rj.scanly.feature.rating

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import `in`.c1ph3rj.scanly.core.common.PlayStoreLinks

/**
 * Explicit rating buttons open Google Play, bypassing the quota-limited in-app review API.
 * The reviews hint is best effort: Play controls the final screen and review eligibility.
 * A successful hand-off does not prove that a rating was submitted.
 */
fun launchRating(context: Context): Boolean = openRating { context.startActivity(it) }

internal fun openRating(startActivity: (Intent) -> Unit): Boolean {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse(PlayStoreLinks.MARKET_REVIEWS_URL))
        .setPackage("com.android.vending")
    val web = Intent(Intent.ACTION_VIEW, Uri.parse(PlayStoreLinks.REVIEWS_URL))
    for (intent in listOf(market, web)) {
        try {
            startActivity(intent)
            return true
        } catch (_: ActivityNotFoundException) {
            // Try the browser next.
        } catch (_: SecurityException) {
            // A disabled/restricted store may reject the hand-off.
        }
    }
    return false
}
