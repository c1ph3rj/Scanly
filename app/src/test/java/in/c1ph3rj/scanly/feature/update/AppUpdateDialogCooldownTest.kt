package `in`.c1ph3rj.scanly.feature.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateDialogCooldownTest {
    @Test
    fun canShowAgain_returnsTrueWhenNoPreviousPromptExists() {
        assertTrue(AppUpdateDialogCooldown.canShowAgain(null, nowMillis = 1_000L))
    }

    @Test
    fun canShowAgain_returnsFalseWithinSixHours() {
        val lastShownAtMillis = 10_000L
        val nowMillis = lastShownAtMillis + 6L * 60L * 60L * 1000L - 1L

        assertFalse(AppUpdateDialogCooldown.canShowAgain(lastShownAtMillis, nowMillis))
    }

    @Test
    fun canShowAgain_returnsTrueAtSixHourBoundary() {
        val lastShownAtMillis = 10_000L
        val nowMillis = lastShownAtMillis + 6L * 60L * 60L * 1000L

        assertTrue(AppUpdateDialogCooldown.canShowAgain(lastShownAtMillis, nowMillis))
    }
}
