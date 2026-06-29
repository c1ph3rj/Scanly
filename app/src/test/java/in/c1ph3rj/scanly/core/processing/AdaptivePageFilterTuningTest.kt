package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
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
    fun blackAndWhiteFallbackUsesTheConservativeThresholdRecipe() {
        val fallback = AdaptivePageFilterTuning.blackAndWhite(null)

        assertEquals(24.0, fallback.backgroundBlurSigma, 0.0001)
        assertEquals(0.76, fallback.shadowStrength, 0.0001)
        assertEquals(1.5, fallback.clipLimit, 0.0001)
        assertEquals(5, fallback.denoiseDiameter)
        assertEquals(41, fallback.blockSize)
        assertEquals(13.0, fallback.c, 0.0001)
    }

    @Test
    fun thresholdWindowsRemainOddAndWithinBenchmarkedRanges() {
        val difficultProfile = profile(
            brightness = 96.0,
            contrast = 20.0,
            shadowRatio = 0.24,
            backgroundUnevenness = 20.0,
            longestEdge = 2_400,
        )

        val blackAndWhite = AdaptivePageFilterTuning.blackAndWhite(difficultProfile)
        val receipt = AdaptivePageFilterTuning.receipt(difficultProfile)

        assertTrue(blackAndWhite.blockSize in 31..71)
        assertEquals(1, blackAndWhite.blockSize % 2)
        assertTrue(blackAndWhite.c in 11.0..14.5)
        assertTrue(receipt.blockSize in 41..81)
        assertEquals(1, receipt.blockSize % 2)
        assertTrue(receipt.c in 10.0..13.0)
        assertTrue(receipt.binaryBlend in 0.58..0.68)
    }

    @Test
    fun automaticUsesGrayscaleAsTheSafeFallback() {
        val ordinaryDocument = profile(
            brightness = 188.0,
            contrast = 42.0,
            shadowRatio = 0.02,
            backgroundUnevenness = 5.0,
            saturation = 12.0,
            colorRatio = 0.005,
            textDensity = 0.02,
        )

        assertEquals(PageFilterPreset.GRAYSCALE, AdaptivePageFilterTuning.automatic(null))
        assertEquals(PageFilterPreset.GRAYSCALE, AdaptivePageFilterTuning.automatic(ordinaryDocument))
    }

    @Test
    fun automaticPreservesUsefulColorAndRoutesLongReceiptsSeparately() {
        val colorDocument = profile(
            saturation = 36.0,
            colorRatio = 0.04,
            textDensity = 0.04,
        )
        val receipt = profile(
            saturation = 8.0,
            colorRatio = 0.004,
            textDensity = 0.08,
            aspectRatio = 2.4,
        )

        assertEquals(PageFilterPreset.ENHANCED_COLOR, AdaptivePageFilterTuning.automatic(colorDocument))
        assertEquals(PageFilterPreset.RECEIPT, AdaptivePageFilterTuning.automatic(receipt))
    }

    @Test
    fun automaticUsesColorPreservingShadowReductionForUnevenColorPages() {
        val shadowedColorDocument = profile(
            shadowRatio = 0.18,
            backgroundUnevenness = 17.0,
            saturation = 32.0,
            colorRatio = 0.05,
            textDensity = 0.05,
        )

        assertEquals(
            PageFilterPreset.SHADOW_REDUCTION,
            AdaptivePageFilterTuning.automatic(shadowedColorDocument),
        )
    }

    @Test
    fun automaticUsesCleanPaperForUnevenTextHeavyPages() {
        val shadowedDocument = profile(
            brightness = 132.0,
            contrast = 24.0,
            shadowRatio = 0.20,
            backgroundUnevenness = 18.0,
            saturation = 10.0,
            colorRatio = 0.005,
            textDensity = 0.07,
        )

        assertEquals(PageFilterPreset.CLEAN, AdaptivePageFilterTuning.automatic(shadowedDocument))
    }

    private fun profile(
        brightness: Double = 176.0,
        contrast: Double = 38.0,
        shadowRatio: Double = 0.04,
        backgroundUnevenness: Double = 6.0,
        saturation: Double = 14.0,
        colorRatio: Double = 0.008,
        textDensity: Double = 0.04,
        aspectRatio: Double = 1.4,
        longestEdge: Int = 1_800,
    ): PageImageProfile = PageImageProfile(
        brightness = brightness,
        contrast = contrast,
        shadowRatio = shadowRatio,
        highlightRatio = 0.18,
        saturation = saturation,
        edgeDensity = 0.07,
        sharpness = 48.0,
        longestEdge = longestEdge,
        backgroundUnevenness = backgroundUnevenness,
        textDensity = textDensity,
        colorRatio = colorRatio,
        aspectRatio = aspectRatio,
    )
}
