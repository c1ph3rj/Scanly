package `in`.c1ph3rj.scanly.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records which classes and methods start-up and tab navigation use, so Android can compile
 * them ahead of time on install instead of interpreting them on first use.
 *
 * Needs a rooted device or emulator, or any device on Android 13+. The result is copied into
 * the app by `:baselineprofile:copyBaselineProfile` (see README.md).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TARGET_PACKAGE) {
        startOnHome()
        navigateTabs()
    }
}
