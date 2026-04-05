package `in`.c1ph3rj.scanly.core.processing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptivePageFilterTuningTest {
    @Test
    fun enhancedColorBecomesMoreAggressiveForDimLowContrastPages() {
        val crispProfile = PageImageProfile(
            brightness = 198.0,
            contrast = 50.0,
            shadowRatio = 0.04,
            highlightRatio = 0.05,
            saturation = 92.0,
            edgeDensity = 0.12,
            sharpness = 68.0,
            longestEdge = 2400,
        )
        val dimProfile = PageImageProfile(
            brightness = 82.0,
            contrast = 16.0,
            shadowRatio = 0.36,
            highlightRatio = 0.03,
            saturation = 28.0,
            edgeDensity = 0.05,
            sharpness = 18.0,
            longestEdge = 2400,
        )

        val crisp = AdaptivePageFilterTuning.enhancedColor(crispProfile)
        val dim = AdaptivePageFilterTuning.enhancedColor(dimProfile)

        assertTrue(dim.clipLimit > crisp.clipLimit)
        assertTrue(dim.sharpenAmount > crisp.sharpenAmount)
        assertTrue(dim.bilateralSigmaColor > crisp.bilateralSigmaColor)
    }

    @Test
    fun blackAndWhiteFallbackKeepsTheFixedRecipe() {
        val fallback = AdaptivePageFilterTuning.blackAndWhite(null)

        assertEquals(31, fallback.backgroundKernelSize)
        assertEquals(2.1, fallback.clipLimit, 0.0001)
        assertEquals(5, fallback.denoiseDiameter)
        assertEquals(35, fallback.windowSize)
        assertEquals(0.2, fallback.k, 0.0001)
    }
}
