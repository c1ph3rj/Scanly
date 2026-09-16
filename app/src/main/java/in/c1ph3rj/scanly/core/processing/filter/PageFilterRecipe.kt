package `in`.c1ph3rj.scanly.core.processing.filter

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset

internal enum class PageFilterFamily {
    Natural,
    Document,
}

internal enum class PageFilterColorMode {
    Color,
    Gray,
    SoftBinary,
    HardBinary,
}

internal enum class PageFilterSaturationPolicy {
    None,
    Preserve,
    Recover,
}

/**
 * Look identity for a preset. Strength values are caps and policies, not the applied gain.
 */
internal data class PageFilterRecipe(
    val family: PageFilterFamily,
    val colorMode: PageFilterColorMode,
    val paperLift: Double,
    val maxPaperL: Double,
    val paperTargetL: Double,
    val minFlatten: Double,
    val maxFlatten: Double,
    val maxClaheStrength: Double,
    val clipLimitMin: Double,
    val clipLimitMax: Double,
    val maxToneLift: Double,
    val saturationPolicy: PageFilterSaturationPolicy,
    val saturationMin: Double,
    val saturationMax: Double,
    val whiteBalanceMin: Double,
    val whiteBalanceMax: Double,
    val maxSharpenAmount: Double,
    val backgroundBlurSigmaAtRef: Double,
    val useBilateral: Boolean,
    val restoreText: Boolean,
    val blockSizeAtRef: Int = 41,
    val cBase: Double = 12.0,
    val cMin: Double = 10.0,
    val cMax: Double = 14.0,
    val binaryBlendMin: Double = 0.0,
    val binaryBlendMax: Double = 0.0,
)

internal object PageFilterRecipes {
    fun forPreset(preset: PageFilterPreset): PageFilterRecipe =
        when (preset) {
            PageFilterPreset.ORIGINAL,
            PageFilterPreset.AUTO,
            PageFilterPreset.ENHANCED_COLOR,
            -> color

            PageFilterPreset.SHADOW_REDUCTION -> shadowReduce
            PageFilterPreset.MAGIC_COLOR -> magic
            PageFilterPreset.GRAYSCALE -> grayscale
            PageFilterPreset.CLEAN -> clean
            PageFilterPreset.BLACK_AND_WHITE -> blackAndWhite
            PageFilterPreset.SOFT_BLACK_AND_WHITE -> softBlackAndWhite
            PageFilterPreset.RECEIPT -> receipt
        }

    private val color = PageFilterRecipe(
        family = PageFilterFamily.Natural,
        colorMode = PageFilterColorMode.Color,
        paperLift = 8.0,
        maxPaperL = 232.0,
        paperTargetL = 232.0,
        minFlatten = 0.00,
        maxFlatten = 0.28,
        maxClaheStrength = 0.32,
        clipLimitMin = 1.15,
        clipLimitMax = 1.45,
        maxToneLift = 8.0,
        saturationPolicy = PageFilterSaturationPolicy.Preserve,
        saturationMin = 0.98,
        saturationMax = 1.02,
        whiteBalanceMin = 0.45,
        whiteBalanceMax = 0.68,
        maxSharpenAmount = 1.08,
        backgroundBlurSigmaAtRef = 20.0,
        useBilateral = true,
        restoreText = false,
    )

    private val shadowReduce = PageFilterRecipe(
        family = PageFilterFamily.Natural,
        colorMode = PageFilterColorMode.Color,
        paperLift = 4.0,
        maxPaperL = 232.0,
        paperTargetL = 232.0,
        minFlatten = 0.30,
        maxFlatten = 0.62,
        maxClaheStrength = 0.20,
        clipLimitMin = 1.10,
        clipLimitMax = 1.32,
        maxToneLift = 4.0,
        saturationPolicy = PageFilterSaturationPolicy.Preserve,
        saturationMin = 0.98,
        saturationMax = 1.02,
        whiteBalanceMin = 0.62,
        whiteBalanceMax = 0.82,
        maxSharpenAmount = 1.06,
        backgroundBlurSigmaAtRef = 22.0,
        useBilateral = true,
        restoreText = false,
    )

    private val magic = PageFilterRecipe(
        family = PageFilterFamily.Natural,
        colorMode = PageFilterColorMode.Color,
        paperLift = 10.0,
        maxPaperL = 236.0,
        paperTargetL = 236.0,
        minFlatten = 0.06,
        maxFlatten = 0.22,
        maxClaheStrength = 0.42,
        clipLimitMin = 1.25,
        clipLimitMax = 1.65,
        maxToneLift = 8.0,
        saturationPolicy = PageFilterSaturationPolicy.Recover,
        saturationMin = 1.00,
        saturationMax = 1.06,
        whiteBalanceMin = 0.20,
        whiteBalanceMax = 0.36,
        maxSharpenAmount = 1.09,
        backgroundBlurSigmaAtRef = 18.0,
        useBilateral = false,
        restoreText = false,
    )

    private val grayscale = PageFilterRecipe(
        family = PageFilterFamily.Document,
        colorMode = PageFilterColorMode.Gray,
        paperLift = 0.0,
        maxPaperL = 236.0,
        paperTargetL = 236.0,
        minFlatten = 0.12,
        maxFlatten = 0.44,
        maxClaheStrength = 0.40,
        clipLimitMin = 1.25,
        clipLimitMax = 1.65,
        maxToneLift = 5.0,
        saturationPolicy = PageFilterSaturationPolicy.None,
        saturationMin = 1.0,
        saturationMax = 1.0,
        whiteBalanceMin = 0.0,
        whiteBalanceMax = 0.0,
        maxSharpenAmount = 1.08,
        backgroundBlurSigmaAtRef = 20.0,
        useBilateral = true,
        restoreText = true,
    )

    private val clean = PageFilterRecipe(
        family = PageFilterFamily.Document,
        colorMode = PageFilterColorMode.Gray,
        paperLift = 0.0,
        maxPaperL = 242.0,
        paperTargetL = 242.0,
        minFlatten = 0.40,
        maxFlatten = 0.74,
        maxClaheStrength = 0.26,
        clipLimitMin = 1.15,
        clipLimitMax = 1.42,
        maxToneLift = 6.0,
        saturationPolicy = PageFilterSaturationPolicy.None,
        saturationMin = 1.0,
        saturationMax = 1.0,
        whiteBalanceMin = 0.0,
        whiteBalanceMax = 0.0,
        maxSharpenAmount = 1.06,
        backgroundBlurSigmaAtRef = 24.0,
        useBilateral = false,
        restoreText = true,
    )

    private val blackAndWhite = PageFilterRecipe(
        family = PageFilterFamily.Document,
        colorMode = PageFilterColorMode.HardBinary,
        paperLift = 0.0,
        maxPaperL = 248.0,
        paperTargetL = 248.0,
        minFlatten = 0.62,
        maxFlatten = 0.88,
        maxClaheStrength = 0.32,
        clipLimitMin = 1.15,
        clipLimitMax = 1.48,
        maxToneLift = 0.0,
        saturationPolicy = PageFilterSaturationPolicy.None,
        saturationMin = 1.0,
        saturationMax = 1.0,
        whiteBalanceMin = 0.0,
        whiteBalanceMax = 0.0,
        maxSharpenAmount = 1.0,
        backgroundBlurSigmaAtRef = 22.0,
        useBilateral = true,
        restoreText = false,
        blockSizeAtRef = 41,
        cBase = 13.0,
        cMin = 11.0,
        cMax = 14.5,
        binaryBlendMin = 1.0,
        binaryBlendMax = 1.0,
    )

    private val softBlackAndWhite = PageFilterRecipe(
        family = PageFilterFamily.Document,
        colorMode = PageFilterColorMode.SoftBinary,
        paperLift = 0.0,
        maxPaperL = 238.0,
        paperTargetL = 238.0,
        minFlatten = 0.36,
        maxFlatten = 0.64,
        maxClaheStrength = 0.36,
        clipLimitMin = 1.18,
        clipLimitMax = 1.55,
        maxToneLift = 3.0,
        saturationPolicy = PageFilterSaturationPolicy.None,
        saturationMin = 1.0,
        saturationMax = 1.0,
        whiteBalanceMin = 0.0,
        whiteBalanceMax = 0.0,
        maxSharpenAmount = 1.07,
        backgroundBlurSigmaAtRef = 20.0,
        useBilateral = true,
        restoreText = false,
        blockSizeAtRef = 41,
        cBase = 11.0,
        cMin = 9.5,
        cMax = 12.5,
        binaryBlendMin = 0.36,
        binaryBlendMax = 0.48,
    )

    private val receipt = PageFilterRecipe(
        family = PageFilterFamily.Document,
        colorMode = PageFilterColorMode.SoftBinary,
        paperLift = 0.0,
        maxPaperL = 244.0,
        paperTargetL = 244.0,
        minFlatten = 0.56,
        maxFlatten = 0.82,
        maxClaheStrength = 0.48,
        clipLimitMin = 1.35,
        clipLimitMax = 1.85,
        maxToneLift = 4.0,
        saturationPolicy = PageFilterSaturationPolicy.None,
        saturationMin = 1.0,
        saturationMax = 1.0,
        whiteBalanceMin = 0.0,
        whiteBalanceMax = 0.0,
        maxSharpenAmount = 1.07,
        backgroundBlurSigmaAtRef = 20.0,
        useBilateral = true,
        restoreText = false,
        blockSizeAtRef = 51,
        cBase = 11.5,
        cMin = 10.0,
        cMax = 13.0,
        binaryBlendMin = 0.58,
        binaryBlendMax = 0.68,
    )
}
