package `in`.c1ph3rj.scanly.core.processing.filter

import `in`.c1ph3rj.scanly.core.processing.PageImageProfile
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val FILTER_REFERENCE_EDGE = 1_600

internal data class PageFilterAppliedParams(
    val flattenStrength: Double,
    val flattenTarget: Double,
    val paperTarget: Double,
    val backgroundBlurSigma: Double,
    val claheStrength: Double,
    val clipLimit: Double,
    val tileGridSize: Int,
    val toneLift: Double,
    val highlightRolloff: Double,
    val saturationScale: Double,
    val whiteBalanceStrength: Double,
    val sharpenAmount: Double,
    val sharpenSigma: Double,
    val binaryBlend: Double,
    val blockSize: Int,
    val adaptiveC: Double,
    val bilateralDiameter: Int,
    val bilateralSigmaColor: Double,
    val bilateralSigmaSpace: Double,
    val restoreText: Boolean,
)

internal object PageFilterStrengthController {
    fun resolve(
        recipe: PageFilterRecipe,
        profile: PageImageProfile?,
        renderLongestEdge: Int,
    ): PageFilterAppliedParams {
        val scale = (renderLongestEdge.coerceAtLeast(1).toDouble() / FILTER_REFERENCE_EDGE.toDouble())
            .coerceIn(0.12, 2.0)
        if (profile == null) {
            return fallback(recipe, scale)
        }

        val paperL = profile.paperL.takeIf { it > 1.0 } ?: profile.brightness
        val lightingNeed = (
            (normalized(profile.backgroundUnevenness, 4.0, 24.0) * 0.58) +
                (normalized(profile.shadowRatio, 0.02, 0.28) * 0.42)
            ).coerceIn(0.0, 1.0)
        val glare = normalized(
            maxOf(profile.glareRatio, profile.highlightRatio * 0.65),
            0.15,
            0.70,
        )
        val evenLighting = lightingNeed < 0.18
        val alreadyGoodPaper = paperL >= 185.0 && evenLighting && glare < 0.25
        val flattenTarget = when (recipe.family) {
            PageFilterFamily.Natural -> paperL.coerceIn(80.0, recipe.maxPaperL)
            PageFilterFamily.Document -> recipe.paperTargetL
        }
        val paperTarget = when {
            recipe.family == PageFilterFamily.Natural && alreadyGoodPaper -> paperL
            recipe.family == PageFilterFamily.Natural ->
                (paperL + recipe.paperLift).coerceAtMost(recipe.maxPaperL)
            else -> recipe.paperTargetL
        }
        val paperGap = (paperTarget - paperL).coerceAtLeast(0.0)
        val lowContrastNeed = 1.0 - normalized(profile.contrast, 20.0, 52.0)
        val lowLightNeed = 1.0 - normalized(profile.brightness, 120.0, 205.0)
        val blurNeed = 1.0 - normalized(profile.sharpness, 14.0, 70.0)
        val evenAndGood =
            evenLighting &&
                paperGap < 4.0 &&
                lowContrastNeed < 0.10 &&
                blurNeed < 0.10 &&
                glare < 0.25

        val flattenNeed = if (evenAndGood) {
            0.0
        } else {
            (normalized(paperGap, 0.0, 48.0) * 0.35 + lightingNeed * 0.65)
                .coerceIn(0.0, 1.0)
        }
        val flattenStrength = (
            lerp(recipe.minFlatten, recipe.maxFlatten, flattenNeed) * (1.0 - 0.65 * glare)
            ).coerceIn(0.0, recipe.maxFlatten)

        val flattenUsed = if (recipe.maxFlatten <= 0.01) {
            0.0
        } else {
            (flattenStrength / recipe.maxFlatten).coerceIn(0.0, 1.0)
        }
        val remainingGap = paperGap * (1.0 - 0.70 * flattenUsed)
        val toneLift = if (evenAndGood) {
            0.0
        } else {
            (remainingGap * 0.28).coerceIn(0.0, recipe.maxToneLift) * (1.0 - 0.50 * glare)
        }
        val highlightRolloff = (0.28 + glare * 0.50).coerceIn(0.20, 0.85)

        val claheNeed = if (evenAndGood) {
            0.0
        } else {
            (lowContrastNeed * (1.0 - 0.55 * flattenUsed) * (1.0 - 0.70 * glare))
                .coerceIn(0.0, 1.0)
        }
        val claheStrength = (recipe.maxClaheStrength * claheNeed).coerceIn(0.0, recipe.maxClaheStrength)
        val clipLimit = lerp(recipe.clipLimitMin, recipe.clipLimitMax, claheNeed)

        val sharpenAmount = if (evenAndGood) {
            1.0
        } else {
            lerp(1.0, recipe.maxSharpenAmount, blurNeed * (1.0 - 0.40 * claheNeed))
        }

        val saturationScale = saturationScale(recipe, profile, lowContrastNeed, lowLightNeed)
        val paperChroma = abs(profile.paperA - 128.0) + abs(profile.paperB - 128.0)
        val castNeed = normalized(paperChroma, 6.0, 28.0)
        val whiteBalanceStrength = if (recipe.whiteBalanceMax <= 0.0) {
            0.0
        } else if (evenAndGood) {
            lerp(0.0, recipe.whiteBalanceMin, castNeed)
        } else {
            lerp(recipe.whiteBalanceMin, recipe.whiteBalanceMax, lightingNeed)
        }

        val binaryNeed = (lowContrastNeed * 0.6 + lightingNeed * 0.4).coerceIn(0.0, 1.0)
        val faintText = lowContrastNeed

        return PageFilterAppliedParams(
            flattenStrength = flattenStrength,
            flattenTarget = flattenTarget,
            paperTarget = paperTarget,
            backgroundBlurSigma = (recipe.backgroundBlurSigmaAtRef * scale).coerceAtLeast(1.0),
            claheStrength = claheStrength,
            clipLimit = clipLimit,
            tileGridSize = scaledTileGridSize(scale),
            toneLift = toneLift,
            highlightRolloff = highlightRolloff,
            saturationScale = saturationScale,
            whiteBalanceStrength = whiteBalanceStrength,
            sharpenAmount = sharpenAmount,
            sharpenSigma = (0.85 * scale).coerceAtLeast(0.40),
            binaryBlend = lerp(recipe.binaryBlendMin, recipe.binaryBlendMax, binaryNeed),
            blockSize = scaledOddKernel(recipe.blockSizeAtRef, scale, min = 3, max = 91),
            adaptiveC = (recipe.cBase + (lightingNeed * 1.2) - (faintText * 1.2))
                .coerceIn(recipe.cMin, recipe.cMax),
            bilateralDiameter = bilateralDiameter(recipe.useBilateral && !evenAndGood, scale),
            bilateralSigmaColor = lerp(14.0, 24.0, (lowContrastNeed + lightingNeed) * 0.5),
            bilateralSigmaSpace = lerp(18.0, 28.0, (lowContrastNeed + lightingNeed) * 0.5),
            restoreText = recipe.restoreText,
        )
    }

    private fun fallback(
        recipe: PageFilterRecipe,
        scale: Double,
    ): PageFilterAppliedParams {
        val mid = 0.45
        val flattenTarget = when (recipe.family) {
            PageFilterFamily.Natural -> 210.0
            PageFilterFamily.Document -> recipe.paperTargetL
        }
        return PageFilterAppliedParams(
            flattenStrength = lerp(recipe.minFlatten, recipe.maxFlatten, mid),
            flattenTarget = flattenTarget,
            paperTarget = recipe.paperTargetL,
            backgroundBlurSigma = (recipe.backgroundBlurSigmaAtRef * scale).coerceAtLeast(1.0),
            claheStrength = recipe.maxClaheStrength * mid,
            clipLimit = lerp(recipe.clipLimitMin, recipe.clipLimitMax, mid),
            tileGridSize = scaledTileGridSize(scale),
            toneLift = recipe.maxToneLift * 0.35,
            highlightRolloff = 0.35,
            saturationScale = lerp(recipe.saturationMin, recipe.saturationMax, 0.5),
            whiteBalanceStrength = lerp(recipe.whiteBalanceMin, recipe.whiteBalanceMax, mid),
            sharpenAmount = lerp(1.0, recipe.maxSharpenAmount, 0.4),
            sharpenSigma = (0.85 * scale).coerceAtLeast(0.40),
            binaryBlend = lerp(recipe.binaryBlendMin, recipe.binaryBlendMax, mid),
            blockSize = scaledOddKernel(recipe.blockSizeAtRef, scale, min = 3, max = 91),
            adaptiveC = recipe.cBase,
            bilateralDiameter = bilateralDiameter(recipe.useBilateral, scale),
            bilateralSigmaColor = 18.0,
            bilateralSigmaSpace = 22.0,
            restoreText = recipe.restoreText,
        )
    }

    private fun saturationScale(
        recipe: PageFilterRecipe,
        profile: PageImageProfile,
        lowContrastNeed: Double,
        lowLightNeed: Double,
    ): Double = when (recipe.saturationPolicy) {
        PageFilterSaturationPolicy.None -> 1.0
        PageFilterSaturationPolicy.Preserve -> when {
            profile.saturation >= 100.0 -> 0.96
            profile.saturation >= 70.0 -> 0.98
            profile.saturation < 20.0 -> 1.02
            profile.saturation < 40.0 -> 1.01
            else -> 1.0
        }
        PageFilterSaturationPolicy.Recover -> {
            val recover = (
                ((1.0 - normalized(profile.saturation, 20.0, 95.0)) * 0.55) +
                    (lowContrastNeed * 0.25) +
                    (lowLightNeed * 0.20)
                ).coerceIn(0.0, 1.0)
            lerp(recipe.saturationMin, recipe.saturationMax, recover)
        }
    }

    private fun bilateralDiameter(enabled: Boolean, scale: Double): Int {
        if (!enabled) return 0
        val diameter = scaledOddKernel(5, scale, min = 3, max = 9)
        return if (diameter < 3) 0 else diameter
    }
}

internal fun normalized(value: Double, min: Double, max: Double): Double {
    if (max <= min) return 0.0
    return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
}

internal fun lerp(start: Double, end: Double, fraction: Double): Double {
    val normalizedFraction = fraction.coerceIn(0.0, 1.0)
    return start + ((end - start) * normalizedFraction)
}

internal fun scaledTileGridSize(scale: Double): Int =
    (8 * scale).roundToInt().coerceIn(4, 12)

internal fun scaledOddKernel(
    reference: Int,
    scale: Double,
    min: Int,
    max: Int,
): Int {
    val raw = (reference * scale).roundToInt()
    return toOddWithin(raw, min, max)
}

internal fun toOddWithin(value: Int, min: Int, max: Int): Int {
    var candidate = value.coerceIn(min, max)
    if (candidate % 2 == 0) {
        candidate = if (candidate >= max) candidate - 1 else candidate + 1
    }
    if (candidate % 2 == 0) {
        candidate = (candidate + 1).coerceAtMost(max)
    }
    return candidate.coerceIn(min, max)
}
