package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
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

        val crisp = AdaptivePageFilterTuning.recipe(PageFilterPreset.ENHANCED_COLOR, crispProfile)
        val dim = AdaptivePageFilterTuning.recipe(PageFilterPreset.ENHANCED_COLOR, dimProfile)

        assertTrue(dim.clipLimit > crisp.clipLimit)
        assertTrue(dim.sharpenAmount > crisp.sharpenAmount)
        assertTrue(dim.denoiseStrength > crisp.denoiseStrength)
    }

    @Test
    fun blackAndWhiteFallbackUsesTheConservativeThresholdRecipe() {
        val fallback = AdaptivePageFilterTuning.recipe(PageFilterPreset.BLACK_AND_WHITE, null)

        assertTrue(fallback.backgroundBlurSigma in 16.0..36.0)
        assertTrue(fallback.shadowStrength in 0.5..0.85)
        assertTrue(fallback.clipLimit in 1.1..1.7)
        assertTrue(fallback.blockSize in 35..75)
        assertEquals(1, fallback.blockSize % 2)
        assertTrue(fallback.thresholdC in 10.0..14.0)
        assertEquals(AdaptivePageFilterTuning.RenderMode.BINARY, fallback.mode)
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

        val blackAndWhite = AdaptivePageFilterTuning.recipe(
            PageFilterPreset.BLACK_AND_WHITE,
            difficultProfile,
        )
        val receipt = AdaptivePageFilterTuning.recipe(PageFilterPreset.RECEIPT, difficultProfile)

        assertTrue(blackAndWhite.blockSize in 35..75)
        assertEquals(1, blackAndWhite.blockSize % 2)
        assertTrue(blackAndWhite.thresholdC in 10.0..14.0)
        assertTrue(receipt.blockSize in 41..85)
        assertEquals(1, receipt.blockSize % 2)
        assertTrue(receipt.thresholdC in 9.5..13.0)
        assertTrue(receipt.binaryBlend in 0.45..0.70)
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

    @Test
    fun automaticRoutesPhotoLikePagesToPhotoPreset() {
        val photoLike = profile(
            saturation = 55.0,
            colorRatio = 0.12,
            textDensity = 0.01,
            edgeDensity = 0.05,
        )

        assertEquals(PageFilterPreset.PHOTO, AdaptivePageFilterTuning.automatic(photoLike))
    }

    @Test
    fun automaticRoutesFadedTextPagesToHighContrast() {
        val fadedText = profile(
            brightness = 170.0,
            contrast = 15.0,
            saturation = 8.0,
            colorRatio = 0.004,
            textDensity = 0.06,
            backgroundUnevenness = 5.0,
            shadowRatio = 0.03,
        )

        assertEquals(PageFilterPreset.HIGH_CONTRAST, AdaptivePageFilterTuning.automatic(fadedText))
    }

    @Test
    fun userShadowAdjustmentScalesRecipeShadowStrength() {
        val profile = profile(shadowRatio = 0.15, backgroundUnevenness = 14.0)
        val low = AdaptivePageFilterTuning.recipe(
            preset = PageFilterPreset.SHADOW_REDUCTION,
            profile = profile,
            adjustments = PageFilterAdjustments.Default.copy(shadows = 0.1f),
        )
        val high = AdaptivePageFilterTuning.recipe(
            preset = PageFilterPreset.SHADOW_REDUCTION,
            profile = profile,
            adjustments = PageFilterAdjustments.Default.copy(shadows = 0.9f),
        )

        assertTrue(high.shadowStrength > low.shadowStrength)
    }

    @Test
    fun intensityAdjustmentIsPropagatedToRecipe() {
        val recipe = AdaptivePageFilterTuning.recipe(
            preset = PageFilterPreset.GRAYSCALE,
            profile = profile(),
            adjustments = PageFilterAdjustments.Default.copy(intensity = 0.42f),
        )
        assertEquals(0.42, recipe.intensity, 0.0001)
    }

    @Test
    fun thresholdAdjustmentMovesBinaryInkAggressiveness() {
        val profile = profile()
        val moreWhite = AdaptivePageFilterTuning.recipe(
            preset = PageFilterPreset.BLACK_AND_WHITE,
            profile = profile,
            adjustments = PageFilterAdjustments.Default.copy(threshold = 0.85f),
        )
        val moreInk = AdaptivePageFilterTuning.recipe(
            preset = PageFilterPreset.BLACK_AND_WHITE,
            profile = profile,
            adjustments = PageFilterAdjustments.Default.copy(threshold = 0.15f),
        )

        // Higher Ink control keeps more white paper → larger adaptive C.
        assertTrue(moreWhite.thresholdC > moreInk.thresholdC)
    }

    private fun profile(
        brightness: Double = 176.0,
        contrast: Double = 38.0,
        shadowRatio: Double = 0.04,
        backgroundUnevenness: Double = 6.0,
        saturation: Double = 14.0,
        colorRatio: Double = 0.008,
        textDensity: Double = 0.04,
        edgeDensity: Double = 0.07,
        aspectRatio: Double = 1.4,
        longestEdge: Int = 1_800,
    ): PageImageProfile = PageImageProfile(
        brightness = brightness,
        contrast = contrast,
        shadowRatio = shadowRatio,
        highlightRatio = 0.18,
        saturation = saturation,
        edgeDensity = edgeDensity,
        sharpness = 48.0,
        longestEdge = longestEdge,
        backgroundUnevenness = backgroundUnevenness,
        textDensity = textDensity,
        colorRatio = colorRatio,
        aspectRatio = aspectRatio,
    )
}
