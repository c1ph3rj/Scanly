package `in`.c1ph3rj.scanly.core.ui

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveLayoutTest {
    @Test
    fun resolveWindowSizeInfo_keepsPhoneLandscapeOutOfTabletLayouts() {
        val info = resolveWindowSizeInfo(
            screenWidthDp = 740,
            smallestScreenWidthDp = 360,
            isLandscape = true,
        )

        assertEquals(WindowWidthClass.Medium, info.widthClass)
        assertFalse(info.isTablet)
        assertTrue(info.useCompactLandscapeLayout)
        assertFalse(info.useTabletLandscapeLayout)
    }

    @Test
    fun resolveWindowSizeInfo_usesTabletLandscapeOnlyForLargeScreenDevices() {
        val info = resolveWindowSizeInfo(
            screenWidthDp = 960,
            smallestScreenWidthDp = 720,
            isLandscape = true,
        )

        assertEquals(WindowWidthClass.Expanded, info.widthClass)
        assertTrue(info.isTablet)
        assertFalse(info.useCompactLandscapeLayout)
        assertTrue(info.useTabletLandscapeLayout)
    }

    @Test
    fun resolveWindowSizeInfo_keepsTabletPortraitAsTabletWithoutLandscapeLayout() {
        val info = resolveWindowSizeInfo(
            screenWidthDp = 720,
            smallestScreenWidthDp = 720,
            isLandscape = false,
        )

        assertEquals(WindowWidthClass.Medium, info.widthClass)
        assertTrue(info.isTablet)
        assertFalse(info.useCompactLandscapeLayout)
        assertFalse(info.useTabletLandscapeLayout)
        assertTrue(info.useToolTwoPaneLayout)
        assertEquals(3, info.toolGridColumns)
        assertEquals(3, info.pdfLibraryGridColumns)
    }

    @Test
    fun resolveWindowSizeInfo_expandedUsesWiderToolColumns() {
        val info = resolveWindowSizeInfo(
            screenWidthDp = 1024,
            smallestScreenWidthDp = 800,
            isLandscape = true,
        )

        assertEquals(WindowWidthClass.Expanded, info.widthClass)
        assertTrue(info.useToolTwoPaneLayout)
        assertEquals(4, info.toolGridColumns)
        assertEquals(4, info.pdfLibraryGridColumns)
    }

    @Test
    fun resolveWindowSizeInfo_phoneLandscapeGetsRailAndCompactHeight() {
        val info = resolveWindowSizeInfo(
            screenWidthDp = 914,
            smallestScreenWidthDp = 411,
            isLandscape = true,
            screenHeightDp = 411,
        )

        assertTrue(info.useNavigationRail)
        assertTrue(info.isHeightConstrained)
        assertTrue(info.useTwoPaneLayout)
        assertFalse(info.isTablet)
    }

    @Test
    fun resolveWindowSizeInfo_phonePortraitKeepsBottomBar() {
        val info = resolveWindowSizeInfo(
            screenWidthDp = 411,
            smallestScreenWidthDp = 411,
            isLandscape = false,
            screenHeightDp = 914,
        )

        assertFalse(info.useNavigationRail)
        assertFalse(info.isHeightConstrained)
        assertFalse(info.useTwoPaneLayout)
        assertEquals(WindowHeightClass.Expanded, info.heightClass)
    }

    @Test
    fun resolveWindowSizeInfo_tabletPortraitUsesRailWithSingleColumnHubs() {
        val info = resolveWindowSizeInfo(
            screenWidthDp = 800,
            smallestScreenWidthDp = 800,
            isLandscape = false,
            screenHeightDp = 1280,
        )

        assertTrue(info.useNavigationRail)
        assertFalse(info.useTwoPaneLayout)
        assertFalse(info.isHeightConstrained)
    }

    @Test
    fun adaptiveColumnCount_fitsCellsAndClamps() {
        assertEquals(2, adaptiveColumnCount(320.dp, 160.dp, minColumns = 2))
        assertEquals(6, adaptiveColumnCount(1104.dp, 160.dp, minColumns = 2))
        assertEquals(6, adaptiveColumnCount(4000.dp, 160.dp, minColumns = 2, maxColumns = 6))
        assertEquals(2, adaptiveColumnCount(100.dp, 160.dp, minColumns = 2))
        assertEquals(1, adaptiveColumnCount(Dp.Unspecified, 160.dp))
    }

    @Test
    fun resolveWindowSizeInfo_compactPhoneKeepsSinglePaneTools() {
        val info = resolveWindowSizeInfo(
            screenWidthDp = 411,
            smallestScreenWidthDp = 411,
            isLandscape = false,
        )

        assertEquals(WindowWidthClass.Compact, info.widthClass)
        assertFalse(info.useToolTwoPaneLayout)
        assertEquals(2, info.toolGridColumns)
        assertEquals(2, info.pdfLibraryGridColumns)
    }
}
