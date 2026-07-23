package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.ScanMode
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
        assertEquals(1.38, fallback.clipLimit, 0.0001)
        assertEquals(0.32, fallback.localContrastStrength, 0.0001)
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
    fun automaticUsesCleanPaperAsTheSafeFallback() {
        val ordinaryDocument = profile(
            brightness = 188.0,
            contrast = 42.0,
            shadowRatio = 0.02,
            backgroundUnevenness = 5.0,
            saturation = 12.0,
            colorRatio = 0.005,
            textDensity = 0.02,
        )

        assertEquals(PageFilterPreset.CLEAN, AdaptivePageFilterTuning.automatic(null))
        assertEquals(PageFilterPreset.CLEAN, AdaptivePageFilterTuning.automatic(ordinaryDocument))
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
    fun automaticUsesColorPreservingShadowReductionForStronglyShadowedColorPages() {
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
    fun automaticKeepsMildUnevenColorAsEnhancedColor() {
        val mildlyUnevenColor = profile(
            shadowRatio = 0.10,
            backgroundUnevenness = 13.0,
            saturation = 36.0,
            colorRatio = 0.04,
            textDensity = 0.04,
        )

        assertEquals(
            PageFilterPreset.ENHANCED_COLOR,
            AdaptivePageFilterTuning.automatic(mildlyUnevenColor),
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
    fun automaticDoesNotMistakeWarmLightingForUsefulDocumentColor() {
        val warmMonochromePage = profile(
            saturation = 36.0,
            colorRatio = 0.004,
            textDensity = 0.05,
        )

        // Warm light without real chroma → clean mono paper, not enhanced color.
        assertEquals(PageFilterPreset.CLEAN, AdaptivePageFilterTuning.automatic(warmMonochromePage))
    }

    @Test
    fun automaticUsesSoftBlackAndWhiteForDenseWellLitText() {
        val denseText = profile(
            brightness = 190.0,
            contrast = 40.0,
            shadowRatio = 0.02,
            backgroundUnevenness = 4.0,
            saturation = 8.0,
            colorRatio = 0.003,
            textDensity = 0.08,
        ).copy(edgeDensity = 0.10)

        assertEquals(
            PageFilterPreset.SOFT_BLACK_AND_WHITE,
            AdaptivePageFilterTuning.automatic(denseText),
        )
    }

    @Test
    fun automaticKeepsLongColorDocumentsOutOfReceiptMode() {
        val longColorDocument = profile(
            saturation = 42.0,
            colorRatio = 0.04,
            textDensity = 0.08,
            aspectRatio = 2.4,
        )

        assertEquals(PageFilterPreset.ENHANCED_COLOR, AdaptivePageFilterTuning.automatic(longColorDocument))
    }

    @Test
    fun lowDetailPagesAvoidAggressiveTextEnhancement() {
        val evenlyLitBlankPage = profile(
            shadowRatio = 0.01,
            backgroundUnevenness = 3.0,
            colorRatio = 0.002,
            textDensity = 0.002,
        ).copy(edgeDensity = 0.008)
        val shadowedBlankPage = evenlyLitBlankPage.copy(
            shadowRatio = 0.18,
            backgroundUnevenness = 16.0,
        )

        assertEquals(PageFilterPreset.ORIGINAL, AdaptivePageFilterTuning.automatic(evenlyLitBlankPage))
        assertEquals(PageFilterPreset.SHADOW_REDUCTION, AdaptivePageFilterTuning.automatic(shadowedBlankPage))
    }

    @Test
    fun shadowReductionUsesConservativeLocalContrastAndStrongerWhiteBalance() {
        val difficultPage = profile(
            shadowRatio = 0.22,
            backgroundUnevenness = 20.0,
            saturation = 38.0,
            colorRatio = 0.05,
        )

        val color = AdaptivePageFilterTuning.enhancedColor(difficultPage)
        val shadowReduction = AdaptivePageFilterTuning.shadowReduction(difficultPage)

        assertTrue(shadowReduction.localContrastStrength < color.localContrastStrength)
        assertTrue(shadowReduction.whiteBalanceStrength > color.whiteBalanceStrength)
    }

    @Test
    fun automaticUsesModeSpecificConservativeDefaults() {
        val colorPage = profile(
            saturation = 42.0,
            colorRatio = 0.12,
        )
        val shadowedBook = profile(
            shadowRatio = 0.22,
            backgroundUnevenness = 20.0,
        )

        assertEquals(
            PageFilterPreset.ID_TEXT,
            AdaptivePageFilterTuning.automatic(colorPage, ScanMode.ID_CARD),
        )
        assertEquals(
            PageFilterPreset.ID_PORTRAIT,
            AdaptivePageFilterTuning.automatic(
                profile = colorPage,
                scanMode = ScanMode.ID_CARD,
                faceDetected = true,
            ),
        )
        assertEquals(
            PageFilterPreset.ID_NATURAL,
            AdaptivePageFilterTuning.automatic(
                profile = colorPage,
                scanMode = ScanMode.ID_CARD,
                faceDetectionAvailable = false,
            ),
        )
        assertEquals(
            PageFilterPreset.SHADOW_REDUCTION,
            AdaptivePageFilterTuning.automatic(shadowedBook, ScanMode.BOOK),
        )
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
