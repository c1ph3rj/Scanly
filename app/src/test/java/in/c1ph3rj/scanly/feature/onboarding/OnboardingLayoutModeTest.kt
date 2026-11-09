package `in`.c1ph3rj.scanly.feature.onboarding

import org.junit.Assert.assertEquals
import org.junit.Test

class OnboardingLayoutModeTest {
    @Test
    fun smallAndSplitWindows_stackContent() {
        assertEquals(OnboardingLayoutMode.COMPACT, resolveOnboardingLayoutMode(320f, 480f))
        assertEquals(OnboardingLayoutMode.COMPACT, resolveOnboardingLayoutMode(599f, 900f))
        assertEquals(OnboardingLayoutMode.COMPACT, resolveOnboardingLayoutMode(420f, 320f))
    }

    @Test
    fun tabletPortrait_centersContent() {
        assertEquals(OnboardingLayoutMode.MEDIUM, resolveOnboardingLayoutMode(600f, 960f))
        assertEquals(OnboardingLayoutMode.MEDIUM, resolveOnboardingLayoutMode(839f, 1280f))
    }

    @Test
    fun phoneLandscapeAndExpandedWindows_useTwoPanes() {
        assertEquals(OnboardingLayoutMode.WIDE, resolveOnboardingLayoutMode(640f, 360f))
        assertEquals(OnboardingLayoutMode.WIDE, resolveOnboardingLayoutMode(840f, 1280f))
        assertEquals(OnboardingLayoutMode.WIDE, resolveOnboardingLayoutMode(1280f, 800f))
    }
}
