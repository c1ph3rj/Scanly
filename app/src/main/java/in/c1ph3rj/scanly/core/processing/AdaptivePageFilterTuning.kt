package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import kotlin.math.roundToInt

internal object AdaptivePageFilterTuning {
    internal data class EnhancedColorTuning(
        val bilateralDiameter: Int,
        val bilateralSigmaColor: Double,
        val bilateralSigmaSpace: Double,
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val textMaskSensitivity: Double,
        val clipLimit: Double,
        val tileGridSize: Int,
        val contrastScale: Double,
        val brightnessShift: Double,
        val saturationScale: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class GrayscaleTuning(
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val textMaskSensitivity: Double,
        val clipLimit: Double,
        val tileGridSize: Int,
        val bilateralDiameter: Int,
        val bilateralSigmaColor: Double,
        val bilateralSigmaSpace: Double,
        val contrastScale: Double,
        val brightnessShift: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class BlackAndWhiteTuning(
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val textMaskSensitivity: Double,
        val clipLimit: Double,
        val tileGridSize: Int,
        val denoiseDiameter: Int,
        val denoiseSigmaColor: Double,
        val denoiseSigmaSpace: Double,
        val blockSize: Int,
        val c: Double,
    )

    internal data class CleanTuning(
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val textMaskSensitivity: Double,
        val clipLimit: Double,
        val tileGridSize: Int,
        val contrastScale: Double,
        val brightnessShift: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class MagicColorTuning(
        val clipLimit: Double,
        val tileGridSize: Int,
        val contrastScale: Double,
        val brightnessShift: Double,
        val saturationScale: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class ReceiptTuning(
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val textMaskSensitivity: Double,
        val clipLimit: Double,
        val tileGridSize: Int,
        val bilateralDiameter: Int,
        val bilateralSigmaColor: Double,
        val bilateralSigmaSpace: Double,
        val blockSize: Int,
        val c: Double,
        val binaryBlend: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class SoftBlackAndWhiteTuning(
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val textMaskSensitivity: Double,
        val clipLimit: Double,
        val tileGridSize: Int,
        val denoiseDiameter: Int,
        val denoiseSigmaColor: Double,
        val denoiseSigmaSpace: Double,
        val blockSize: Int,
        val c: Double,
        val binaryBlend: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal fun automatic(profile: PageImageProfile?): PageFilterPreset {
        profile ?: return PageFilterPreset.GRAYSCALE

        val receiptLike = profile.aspectRatio >= 1.7 &&
            profile.textDensity >= 0.015 &&
            profile.colorRatio < 0.12
        val carriesUsefulColor = profile.colorRatio >= 0.02 || profile.saturation >= 24.0
        val unevenBackground = profile.backgroundUnevenness >= 12.0 || profile.shadowRatio >= 0.12
        val textHeavy = profile.textDensity >= 0.025 || profile.edgeDensity >= 0.055
        val difficultLighting = profile.brightness < 125.0 || profile.contrast < 22.0

        return when {
            receiptLike -> PageFilterPreset.RECEIPT
            carriesUsefulColor && unevenBackground -> PageFilterPreset.SHADOW_REDUCTION
            carriesUsefulColor -> PageFilterPreset.ENHANCED_COLOR
            textHeavy && (unevenBackground || difficultLighting) -> PageFilterPreset.CLEAN
            else -> PageFilterPreset.GRAYSCALE
        }
    }

    internal fun enhancedColor(profile: PageImageProfile?): EnhancedColorTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            val shadows = shadowBoost(it)
            EnhancedColorTuning(
                bilateralDiameter = scaledOddKernel(it.longestEdge, 520.0, 5, 7),
                bilateralSigmaColor = lerp(18.0, 30.0, cleanup),
                bilateralSigmaSpace = lerp(22.0, 34.0, cleanup),
                backgroundBlurSigma = scaledSigma(it.longestEdge, 86.0, 12.0, 32.0),
                shadowStrength = lerp(0.10, 0.30, shadows),
                backgroundTarget = 232.0,
                textMaskSensitivity = lerp(14.0, 10.0, lowContrastNeed(it)),
                clipLimit = lerp(1.35, 1.85, cleanup),
                tileGridSize = tileGridSize(it),
                contrastScale = lerp(1.0, 1.025, lowContrastNeed(it)),
                brightnessShift = lerp(0.0, 4.0, lowLightNeed(it)),
                saturationScale = naturalSaturationScale(it),
                sharpenAmount = lerp(1.06, 1.14, detail),
                sharpenSigma = lerp(0.75, 0.95, detail),
            )
        } ?: EnhancedColorTuning(
            bilateralDiameter = 5,
            bilateralSigmaColor = 22.0,
            bilateralSigmaSpace = 28.0,
            backgroundBlurSigma = 22.0,
            shadowStrength = 0.18,
            backgroundTarget = 232.0,
            textMaskSensitivity = 12.0,
            clipLimit = 1.55,
            tileGridSize = 8,
            contrastScale = 1.01,
            brightnessShift = 2.0,
            saturationScale = 1.0,
            sharpenAmount = 1.10,
            sharpenSigma = 0.85,
        )

    internal fun shadowReduction(profile: PageImageProfile?): EnhancedColorTuning =
        profile?.let {
            val shadows = shadowBoost(it)
            val detail = detailBoost(it)
            EnhancedColorTuning(
                bilateralDiameter = 5,
                bilateralSigmaColor = lerp(14.0, 22.0, shadows),
                bilateralSigmaSpace = lerp(18.0, 26.0, shadows),
                backgroundBlurSigma = scaledSigma(it.longestEdge, 76.0, 14.0, 36.0),
                shadowStrength = lerp(0.34, 0.62, shadows),
                backgroundTarget = 232.0,
                textMaskSensitivity = lerp(14.0, 9.0, lowContrastNeed(it)),
                clipLimit = lerp(1.15, 1.45, shadows),
                tileGridSize = tileGridSize(it),
                contrastScale = lerp(1.0, 1.015, lowContrastNeed(it)),
                brightnessShift = lerp(0.0, 2.0, lowLightNeed(it)),
                saturationScale = naturalSaturationScale(it),
                sharpenAmount = lerp(1.03, 1.08, detail),
                sharpenSigma = lerp(0.75, 0.90, detail),
            )
        } ?: EnhancedColorTuning(
            bilateralDiameter = 5,
            bilateralSigmaColor = 18.0,
            bilateralSigmaSpace = 22.0,
            backgroundBlurSigma = 26.0,
            shadowStrength = 0.48,
            backgroundTarget = 232.0,
            textMaskSensitivity = 11.0,
            clipLimit = 1.30,
            tileGridSize = 8,
            contrastScale = 1.005,
            brightnessShift = 1.0,
            saturationScale = 1.0,
            sharpenAmount = 1.05,
            sharpenSigma = 0.82,
        )

    internal fun grayscale(profile: PageImageProfile?): GrayscaleTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            val shadows = shadowBoost(it)
            GrayscaleTuning(
                backgroundBlurSigma = scaledSigma(it.longestEdge, 82.0, 12.0, 34.0),
                shadowStrength = lerp(0.18, 0.44, shadows),
                backgroundTarget = 236.0,
                textMaskSensitivity = lerp(14.0, 9.0, lowContrastNeed(it)),
                clipLimit = lerp(1.45, 2.05, cleanup),
                tileGridSize = tileGridSize(it),
                bilateralDiameter = scaledOddKernel(it.longestEdge, 520.0, 5, 7),
                bilateralSigmaColor = lerp(16.0, 27.0, cleanup),
                bilateralSigmaSpace = lerp(20.0, 31.0, cleanup),
                contrastScale = lerp(1.0, 1.035, lowContrastNeed(it)),
                brightnessShift = lerp(1.0, 5.0, lowLightNeed(it)),
                sharpenAmount = lerp(1.05, 1.13, detail),
                sharpenSigma = lerp(0.70, 0.90, detail),
            )
        } ?: GrayscaleTuning(
            backgroundBlurSigma = 24.0,
            shadowStrength = 0.30,
            backgroundTarget = 236.0,
            textMaskSensitivity = 11.0,
            clipLimit = 1.70,
            tileGridSize = 8,
            bilateralDiameter = 5,
            bilateralSigmaColor = 21.0,
            bilateralSigmaSpace = 25.0,
            contrastScale = 1.015,
            brightnessShift = 2.5,
            sharpenAmount = 1.09,
            sharpenSigma = 0.80,
        )

    internal fun blackAndWhite(profile: PageImageProfile?): BlackAndWhiteTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val shadows = shadowBoost(it)
            val faintText = lowContrastNeed(it)
            BlackAndWhiteTuning(
                backgroundBlurSigma = scaledSigma(it.longestEdge, 72.0, 16.0, 38.0),
                shadowStrength = lerp(0.68, 0.88, shadows),
                backgroundTarget = 240.0,
                textMaskSensitivity = lerp(13.0, 8.0, faintText),
                clipLimit = lerp(1.30, 1.75, cleanup),
                tileGridSize = tileGridSize(it),
                denoiseDiameter = 5,
                denoiseSigmaColor = lerp(16.0, 24.0, cleanup),
                denoiseSigmaSpace = lerp(20.0, 28.0, cleanup),
                blockSize = scaledOddKernel(it.longestEdge, 38.0, 31, 71),
                c = (13.0 + (shadows * 1.5) - (faintText * 1.5)).coerceIn(11.0, 14.5),
            )
        } ?: BlackAndWhiteTuning(
            backgroundBlurSigma = 24.0,
            shadowStrength = 0.76,
            backgroundTarget = 240.0,
            textMaskSensitivity = 10.0,
            clipLimit = 1.50,
            tileGridSize = 8,
            denoiseDiameter = 5,
            denoiseSigmaColor = 20.0,
            denoiseSigmaSpace = 24.0,
            blockSize = 41,
            c = 13.0,
        )

    internal fun clean(profile: PageImageProfile?): CleanTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            val shadows = shadowBoost(it)
            CleanTuning(
                backgroundBlurSigma = scaledSigma(it.longestEdge, 66.0, 18.0, 42.0),
                shadowStrength = lerp(0.46, 0.74, shadows),
                backgroundTarget = 240.0,
                textMaskSensitivity = lerp(12.0, 7.5, lowContrastNeed(it)),
                clipLimit = lerp(1.30, 1.78, cleanup),
                tileGridSize = tileGridSize(it),
                contrastScale = lerp(1.0, 1.025, lowContrastNeed(it)),
                brightnessShift = lerp(2.0, 6.0, cleanup),
                sharpenAmount = lerp(1.04, 1.10, detail),
                sharpenSigma = lerp(0.65, 0.85, detail),
            )
        } ?: CleanTuning(
            backgroundBlurSigma = 28.0,
            shadowStrength = 0.60,
            backgroundTarget = 240.0,
            textMaskSensitivity = 9.0,
            clipLimit = 1.55,
            tileGridSize = 8,
            contrastScale = 1.01,
            brightnessShift = 4.0,
            sharpenAmount = 1.07,
            sharpenSigma = 0.75,
        )

    internal fun magicColor(profile: PageImageProfile?): MagicColorTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            MagicColorTuning(
                clipLimit = lerp(1.65, 2.25, cleanup),
                tileGridSize = tileGridSize(it),
                contrastScale = lerp(1.005, 1.035, lowContrastNeed(it)),
                brightnessShift = lerp(0.0, 3.0, lowLightNeed(it)),
                saturationScale = lerp(1.04, 1.12, colorRecoveryBoost(it)),
                sharpenAmount = lerp(1.08, 1.16, detail),
                sharpenSigma = lerp(0.75, 0.95, detail),
            )
        } ?: MagicColorTuning(
            clipLimit = 1.90,
            tileGridSize = 8,
            contrastScale = 1.02,
            brightnessShift = 1.5,
            saturationScale = 1.08,
            sharpenAmount = 1.12,
            sharpenSigma = 0.85,
        )

    internal fun receipt(profile: PageImageProfile?): ReceiptTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            val shadows = shadowBoost(it)
            val faintText = lowContrastNeed(it)
            ReceiptTuning(
                backgroundBlurSigma = scaledSigma(it.longestEdge, 78.0, 16.0, 34.0),
                shadowStrength = lerp(0.62, 0.82, shadows),
                backgroundTarget = 242.0,
                textMaskSensitivity = lerp(11.0, 7.0, faintText),
                clipLimit = lerp(1.80, 2.55, cleanup),
                tileGridSize = tileGridSize(it),
                bilateralDiameter = 5,
                bilateralSigmaColor = lerp(14.0, 22.0, cleanup),
                bilateralSigmaSpace = lerp(18.0, 26.0, cleanup),
                blockSize = scaledOddKernel(it.longestEdge, 32.0, 41, 81),
                c = (11.5 + (shadows * 1.5) - (faintText * 1.2)).coerceIn(10.0, 13.0),
                binaryBlend = lerp(0.58, 0.68, cleanup),
                sharpenAmount = lerp(1.03, 1.09, detail),
                sharpenSigma = lerp(0.60, 0.78, detail),
            )
        } ?: ReceiptTuning(
            backgroundBlurSigma = 24.0,
            shadowStrength = 0.72,
            backgroundTarget = 242.0,
            textMaskSensitivity = 9.0,
            clipLimit = 2.10,
            tileGridSize = 8,
            bilateralDiameter = 5,
            bilateralSigmaColor = 18.0,
            bilateralSigmaSpace = 22.0,
            blockSize = 61,
            c = 11.5,
            binaryBlend = 0.63,
            sharpenAmount = 1.06,
            sharpenSigma = 0.68,
        )

    internal fun softBlackAndWhite(profile: PageImageProfile?): SoftBlackAndWhiteTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            val shadows = shadowBoost(it)
            val faintText = lowContrastNeed(it)
            SoftBlackAndWhiteTuning(
                backgroundBlurSigma = scaledSigma(it.longestEdge, 82.0, 14.0, 32.0),
                shadowStrength = lerp(0.42, 0.64, shadows),
                backgroundTarget = 238.0,
                textMaskSensitivity = lerp(12.0, 8.0, faintText),
                clipLimit = lerp(1.35, 1.85, cleanup),
                tileGridSize = tileGridSize(it),
                denoiseDiameter = 5,
                denoiseSigmaColor = lerp(14.0, 22.0, cleanup),
                denoiseSigmaSpace = lerp(18.0, 26.0, cleanup),
                blockSize = scaledOddKernel(it.longestEdge, 38.0, 31, 71),
                c = (11.0 + shadows - faintText).coerceIn(9.5, 12.5),
                binaryBlend = lerp(0.36, 0.48, cleanup),
                sharpenAmount = lerp(1.03, 1.09, detail),
                sharpenSigma = lerp(0.65, 0.82, detail),
            )
        } ?: SoftBlackAndWhiteTuning(
            backgroundBlurSigma = 22.0,
            shadowStrength = 0.52,
            backgroundTarget = 238.0,
            textMaskSensitivity = 10.0,
            clipLimit = 1.55,
            tileGridSize = 8,
            denoiseDiameter = 5,
            denoiseSigmaColor = 18.0,
            denoiseSigmaSpace = 22.0,
            blockSize = 41,
            c = 10.5,
            binaryBlend = 0.42,
            sharpenAmount = 1.06,
            sharpenSigma = 0.72,
        )

    private fun detailBoost(profile: PageImageProfile): Double {
        val blurNeed = 1.0 - normalized(profile.sharpness, 14.0, 70.0)
        val edgeSignal = normalized(profile.edgeDensity, 0.025, 0.14)
        return ((blurNeed * 0.72) + (edgeSignal * 0.28)).coerceIn(0.0, 1.0)
    }

    private fun cleanupBoost(profile: PageImageProfile): Double =
        ((lowLightNeed(profile) * 0.18) +
            (lowContrastNeed(profile) * 0.25) +
            (shadowBoost(profile) * 0.35) +
            ((1.0 - normalized(profile.sharpness, 12.0, 70.0)) * 0.12) +
            (normalized(profile.highlightRatio, 0.35, 0.90) * 0.10))
            .coerceIn(0.0, 1.0)

    private fun shadowBoost(profile: PageImageProfile): Double =
        ((normalized(profile.backgroundUnevenness, 4.0, 24.0) * 0.58) +
            (normalized(profile.shadowRatio, 0.02, 0.28) * 0.42))
            .coerceIn(0.0, 1.0)

    private fun lowLightNeed(profile: PageImageProfile): Double =
        1.0 - normalized(profile.brightness, 120.0, 205.0)

    private fun lowContrastNeed(profile: PageImageProfile): Double =
        1.0 - normalized(profile.contrast, 20.0, 52.0)

    private fun colorRecoveryBoost(profile: PageImageProfile): Double =
        ((1.0 - normalized(profile.saturation, 20.0, 95.0)) * 0.55 +
            lowContrastNeed(profile) * 0.25 +
            lowLightNeed(profile) * 0.20)
            .coerceIn(0.0, 1.0)

    private fun naturalSaturationScale(profile: PageImageProfile): Double = when {
        profile.saturation >= 100.0 -> 0.96
        profile.saturation >= 70.0 -> 0.98
        profile.saturation < 20.0 -> 1.04
        profile.saturation < 40.0 -> 1.02
        else -> 1.0
    }

    private fun tileGridSize(profile: PageImageProfile): Int =
        if (profile.longestEdge >= 1_800) 10 else 8

    private fun normalized(value: Double, min: Double, max: Double): Double {
        if (max <= min) return 0.0
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double {
        val normalizedFraction = fraction.coerceIn(0.0, 1.0)
        return start + ((end - start) * normalizedFraction)
    }

    private fun scaledSigma(longestEdge: Int, divisor: Double, min: Double, max: Double): Double =
        (longestEdge / divisor).coerceIn(min, max)

    private fun scaledOddKernel(
        longestEdge: Int,
        divisor: Double,
        min: Int,
        max: Int,
    ): Int {
        val raw = (longestEdge / divisor).roundToInt().coerceIn(min, max)
        return toOddWithin(raw, min, max)
    }

    private fun toOddWithin(value: Int, min: Int, max: Int): Int {
        var candidate = value.coerceIn(min, max)
        if (candidate % 2 == 0) {
            candidate = if (candidate >= max) candidate - 1 else candidate + 1
        }
        return candidate.coerceIn(min, max)
    }
}
