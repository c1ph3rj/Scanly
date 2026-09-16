package `in`.c1ph3rj.scanly.core.processing.filter

import `in`.c1ph3rj.scanly.core.processing.PageImageProfile
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PageFilterStrengthControllerTest {
    @Test
    fun alreadyGoodPageStaysNearIdentityForColor() {
        val goodPage = profile(
            brightness = 198.0,
            paperL = 198.0,
            contrast = 50.0,
            shadowRatio = 0.03,
            highlightRatio = 0.04,
            glareRatio = 0.01,
            backgroundUnevenness = 4.0,
            sharpness = 68.0,
        )

        val color = PageFilterStrengthController.resolve(
            recipe = PageFilterRecipes.forPreset(PageFilterPreset.ENHANCED_COLOR),
            profile = goodPage,
            renderLongestEdge = 1_600,
        )

        assertTrue(color.flattenStrength < 0.04)
        assertTrue(color.claheStrength < 0.04)
        assertTrue(color.toneLift < 0.5)
        assertEquals(1.0, color.sharpenAmount, 0.001)
        assertEquals(0, color.bilateralDiameter)
    }

    @Test
    fun fadedEvenPageStillGetsCleanup() {
        val faded = profile(
            brightness = 198.0,
            paperL = 198.0,
            contrast = 16.0,
            shadowRatio = 0.03,
            highlightRatio = 0.04,
            glareRatio = 0.01,
            backgroundUnevenness = 4.0,
            sharpness = 18.0,
        )

        val color = PageFilterStrengthController.resolve(
            recipe = PageFilterRecipes.forPreset(PageFilterPreset.ENHANCED_COLOR),
            profile = faded,
            renderLongestEdge = 1_600,
        )

        assertTrue(color.claheStrength > 0.04)
        assertTrue(color.sharpenAmount > 1.01)
    }

    @Test
    fun dimUnevenPageGetsStrongerButCappedColor() {
        val crisp = profile(
            brightness = 198.0,
            paperL = 198.0,
            contrast = 50.0,
            shadowRatio = 0.04,
            backgroundUnevenness = 5.0,
            sharpness = 68.0,
        )
        val dim = profile(
            brightness = 82.0,
            paperL = 88.0,
            contrast = 16.0,
            shadowRatio = 0.36,
            highlightRatio = 0.03,
            glareRatio = 0.02,
            backgroundUnevenness = 20.0,
            sharpness = 18.0,
            saturation = 28.0,
        )

        val recipe = PageFilterRecipes.forPreset(PageFilterPreset.ENHANCED_COLOR)
        val crispParams = PageFilterStrengthController.resolve(recipe, crisp, 1_600)
        val dimParams = PageFilterStrengthController.resolve(recipe, dim, 1_600)

        assertTrue(dimParams.flattenStrength > crispParams.flattenStrength)
        assertTrue(dimParams.claheStrength > crispParams.claheStrength)
        assertTrue(dimParams.flattenStrength <= recipe.maxFlatten)
        assertTrue(dimParams.claheStrength <= recipe.maxClaheStrength)
        assertTrue(dimParams.toneLift <= recipe.maxToneLift)
    }

    @Test
    fun glareReducesFlattenAndLocalContrast() {
        val shadowed = profile(
            brightness = 150.0,
            paperL = 150.0,
            contrast = 28.0,
            shadowRatio = 0.20,
            highlightRatio = 0.08,
            glareRatio = 0.04,
            backgroundUnevenness = 18.0,
        )
        val glared = shadowed.copy(
            highlightRatio = 0.62,
            glareRatio = 0.48,
        )

        val recipe = PageFilterRecipes.forPreset(PageFilterPreset.ENHANCED_COLOR)
        val normal = PageFilterStrengthController.resolve(recipe, shadowed, 1_600)
        val withGlare = PageFilterStrengthController.resolve(recipe, glared, 1_600)

        assertTrue(withGlare.flattenStrength < normal.flattenStrength)
        assertTrue(withGlare.claheStrength <= normal.claheStrength)
        assertTrue(withGlare.highlightRolloff > normal.highlightRolloff)
    }

    @Test
    fun shadowReductionUsesConservativeLocalContrastAndStrongerWhiteBalance() {
        val difficultPage = profile(
            brightness = 140.0,
            paperL = 142.0,
            contrast = 24.0,
            shadowRatio = 0.22,
            backgroundUnevenness = 20.0,
            saturation = 38.0,
            colorRatio = 0.05,
        )

        val color = PageFilterStrengthController.resolve(
            recipe = PageFilterRecipes.forPreset(PageFilterPreset.ENHANCED_COLOR),
            profile = difficultPage,
            renderLongestEdge = 1_600,
        )
        val shadowReduction = PageFilterStrengthController.resolve(
            recipe = PageFilterRecipes.forPreset(PageFilterPreset.SHADOW_REDUCTION),
            profile = difficultPage,
            renderLongestEdge = 1_600,
        )

        assertTrue(shadowReduction.claheStrength < color.claheStrength)
        assertTrue(shadowReduction.whiteBalanceStrength > color.whiteBalanceStrength)
        assertTrue(shadowReduction.flattenStrength > color.flattenStrength)
    }

    @Test
    fun fallbackUsesMidConservativeRecipe() {
        val fallback = PageFilterStrengthController.resolve(
            recipe = PageFilterRecipes.forPreset(PageFilterPreset.BLACK_AND_WHITE),
            profile = null,
            renderLongestEdge = 1_600,
        )

        assertEquals(41, fallback.blockSize)
        assertEquals(13.0, fallback.adaptiveC, 0.0001)
        assertTrue(fallback.flattenStrength in 0.62..0.88)
        assertTrue(fallback.clipLimit in 1.15..1.48)
        assertEquals(1, fallback.blockSize % 2)
    }

    @Test
    fun sharedProfileKeepsLookStrengthStableAcrossRenderSizes() {
        val page = profile(
            brightness = 140.0,
            paperL = 142.0,
            contrast = 24.0,
            shadowRatio = 0.20,
            backgroundUnevenness = 18.0,
        )
        val recipe = PageFilterRecipes.forPreset(PageFilterPreset.CLEAN)
        val small = PageFilterStrengthController.resolve(recipe, page, 320)
        val preview = PageFilterStrengthController.resolve(recipe, page, 1_600)
        val save = PageFilterStrengthController.resolve(recipe, page, 2_400)

        assertEquals(preview.flattenStrength, small.flattenStrength, 0.0001)
        assertEquals(preview.flattenStrength, save.flattenStrength, 0.0001)
        assertEquals(preview.claheStrength, small.claheStrength, 0.0001)
        assertEquals(preview.claheStrength, save.claheStrength, 0.0001)
        assertEquals(preview.paperTarget, small.paperTarget, 0.0001)
        assertEquals(preview.paperTarget, save.paperTarget, 0.0001)
        assertTrue(small.blockSize < preview.blockSize)
        assertTrue(preview.blockSize <= save.blockSize)
        assertEquals(1, small.blockSize % 2)
        assertEquals(1, preview.blockSize % 2)
        assertEquals(1, save.blockSize % 2)
        assertEquals(4, small.tileGridSize)
        assertEquals(8, preview.tileGridSize)
        assertEquals(12, save.tileGridSize)
        assertEquals(4, scaledTileGridSize(320.0 / 1_600.0))
        assertEquals(8, scaledTileGridSize(1.0))
        assertEquals(12, scaledTileGridSize(1.5))
    }

    @Test
    fun thresholdWindowsRemainOddAndWithinRecipeRanges() {
        val difficultProfile = profile(
            brightness = 96.0,
            paperL = 100.0,
            contrast = 20.0,
            shadowRatio = 0.24,
            backgroundUnevenness = 20.0,
        )

        val blackAndWhite = PageFilterStrengthController.resolve(
            recipe = PageFilterRecipes.forPreset(PageFilterPreset.BLACK_AND_WHITE),
            profile = difficultProfile,
            renderLongestEdge = 2_400,
        )
        val receipt = PageFilterStrengthController.resolve(
            recipe = PageFilterRecipes.forPreset(PageFilterPreset.RECEIPT),
            profile = difficultProfile,
            renderLongestEdge = 2_400,
        )

        assertEquals(1, blackAndWhite.blockSize % 2)
        assertTrue(blackAndWhite.adaptiveC in 11.0..14.5)
        assertEquals(1, receipt.blockSize % 2)
        assertTrue(receipt.adaptiveC in 10.0..13.0)
        assertTrue(receipt.binaryBlend in 0.58..0.68)
    }

    private fun profile(
        brightness: Double = 176.0,
        paperL: Double = brightness,
        contrast: Double = 38.0,
        shadowRatio: Double = 0.04,
        highlightRatio: Double = 0.06,
        glareRatio: Double = 0.03,
        backgroundUnevenness: Double = 6.0,
        saturation: Double = 14.0,
        colorRatio: Double = 0.008,
        textDensity: Double = 0.04,
        sharpness: Double = 48.0,
        aspectRatio: Double = 1.4,
    ): PageImageProfile = PageImageProfile(
        brightness = brightness,
        contrast = contrast,
        shadowRatio = shadowRatio,
        highlightRatio = highlightRatio,
        saturation = saturation,
        edgeDensity = 0.07,
        sharpness = sharpness,
        longestEdge = 1_800,
        backgroundUnevenness = backgroundUnevenness,
        textDensity = textDensity,
        colorRatio = colorRatio,
        aspectRatio = aspectRatio,
        paperL = paperL,
        paperA = 132.0,
        paperB = 124.0,
        glareRatio = glareRatio,
        noise = 3.0,
    )
}
