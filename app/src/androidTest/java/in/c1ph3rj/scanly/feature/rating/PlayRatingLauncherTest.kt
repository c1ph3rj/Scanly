package `in`.c1ph3rj.scanly.feature.rating

import android.content.ActivityNotFoundException
import android.content.Intent
import org.junit.Assert.*
import org.junit.Test

class PlayRatingLauncherTest {
    @Test fun rateUsesPlayStoreAndRequestsReviews() {
        val calls = mutableListOf<Intent>()
        assertTrue(openRating { calls += it })
        val intent = calls.single()
        assertEquals("com.android.vending", intent.`package`)
        assertEquals(Intent.ACTION_VIEW, intent.action)
        assertEquals("market", intent.data?.scheme)
        assertEquals("in.c1ph3rj.scanly", intent.data?.getQueryParameter("id"))
        assertEquals("true", intent.data?.getQueryParameter("showAllReviews"))
    }

    @Test fun missingOrRestrictedPlayStoreFallsBackToBrowser() {
        for (failure in listOf(ActivityNotFoundException(), SecurityException())) {
            val calls = mutableListOf<Intent>()
            assertTrue(openRating {
                calls += it
                if (calls.size == 1) throw failure
            })
            assertEquals(2, calls.size)
            assertNull(calls.last().`package`)
            assertEquals("https", calls.last().data?.scheme)
            assertEquals("play.google.com", calls.last().data?.host)
            assertEquals("true", calls.last().data?.getQueryParameter("showAllReviews"))
        }
    }

    @Test fun noStoreOrBrowserReportsFailure() {
        var attempts = 0
        assertFalse(openRating {
            attempts++
            throw ActivityNotFoundException()
        })
        assertEquals(2, attempts)
    }
}
