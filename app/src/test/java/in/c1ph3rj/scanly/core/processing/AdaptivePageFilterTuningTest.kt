package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.junit.Assert.assertEquals
import org.junit.Test

class AdaptivePageFilterTuningTest {
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
