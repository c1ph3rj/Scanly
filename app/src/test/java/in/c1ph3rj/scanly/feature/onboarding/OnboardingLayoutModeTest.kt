package `in`.c1ph3rj.scanly.feature.onboarding

import `in`.c1ph3rj.scanly.core.ui.WindowWidthClass
import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingLayoutModeTest {
    @Test
    fun compactWidth_usesMobileLayoutInEitherOrientation() {
        assertEquals(
            OnboardingLayoutMode.COMPACT,
            resolveOnboardingLayoutMode(
                widthClass = WindowWidthClass.Compact,
                isLandscape = false,
            ),
        )
        assertEquals(
            OnboardingLayoutMode.COMPACT,
            resolveOnboardingLayoutMode(
                widthClass = WindowWidthClass.Compact,
                isLandscape = true,
            ),
        )
    }

    @Test
    fun mediumPortrait_usesCenteredTabletLayout() {
        assertEquals(
            OnboardingLayoutMode.MEDIUM,
            resolveOnboardingLayoutMode(
                widthClass = WindowWidthClass.Medium,
                isLandscape = false,
            ),
        )
    }

    @Test
    fun mediumLandscape_usesWideLayout() {
        assertEquals(
            OnboardingLayoutMode.WIDE,
            resolveOnboardingLayoutMode(
                widthClass = WindowWidthClass.Medium,
                isLandscape = true,
            ),
        )
    }

    @Test
    fun expandedWidth_alwaysUsesWideLayout() {
        assertEquals(
            OnboardingLayoutMode.WIDE,
            resolveOnboardingLayoutMode(
                widthClass = WindowWidthClass.Expanded,
                isLandscape = false,
            ),
        )
        assertEquals(
            OnboardingLayoutMode.WIDE,
            resolveOnboardingLayoutMode(
                widthClass = WindowWidthClass.Expanded,
                isLandscape = true,
            ),
        )
    }
}
