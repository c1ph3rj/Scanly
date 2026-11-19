package `in`.c1ph3rj.scanly.baselineprofile

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Start-up time and frame timing for tab navigation, each measured twice: with nothing
 * precompiled (what a fresh install without a profile gets) and with the baseline profile.
 * The gap between the two is what the profile buys; the absolute frame numbers are the
 * regression check for navigation smoothness.
 */
@RunWith(AndroidJUnit4::class)
class NavigationBenchmark {
    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun coldStartWithoutProfile() = coldStart(CompilationMode.None())

    @Test
    fun coldStartWithBaselineProfile() = coldStart(CompilationMode.Partial())

    @Test
    fun tabNavigationWithoutProfile() = tabNavigation(CompilationMode.None())

    @Test
    fun tabNavigationWithBaselineProfile() = tabNavigation(CompilationMode.Partial())

    private fun coldStart(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        compilationMode = compilationMode,
        startupMode = StartupMode.COLD,
        iterations = 8,
        setupBlock = { pressHome() },
    ) {
        startOnHome()
    }

    private fun tabNavigation(compilationMode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        compilationMode = compilationMode,
        iterations = 6,
        setupBlock = { startOnHome() },
    ) {
        navigateTabs()
    }
}
