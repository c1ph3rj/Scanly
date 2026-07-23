package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import `in`.c1ph3rj.scanly.domain.model.ScanMode
import kotlin.math.roundToInt

internal object AdaptivePageFilterTuning {
    internal data class EnhancedColorTuning(
        val bilateralDiameter: Int,
        val bilateralSigmaColor: Double,
        val bilateralSigmaSpace: Double,
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val clipLimit: Double,
        val localContrastStrength: Double,
        val tileGridSize: Int,
        val contrastScale: Double,
        val brightnessShift: Double,
        val saturationScale: Double,
        val whiteBalanceStrength: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class GrayscaleTuning(
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val clipLimit: Double,
        val localContrastStrength: Double,
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
        val clipLimit: Double,
        val localContrastStrength: Double,
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
        val clipLimit: Double,
        val localContrastStrength: Double,
        val tileGridSize: Int,
        val contrastScale: Double,
        val brightnessShift: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class MagicColorTuning(
        val clipLimit: Double,
        val localContrastStrength: Double,
        val tileGridSize: Int,
        val contrastScale: Double,
        val brightnessShift: Double,
        val saturationScale: Double,
        val whiteBalanceStrength: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class ReceiptTuning(
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val clipLimit: Double,
        val localContrastStrength: Double,
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
        val clipLimit: Double,
        val localContrastStrength: Double,
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

    /**
     * Chooses a concrete filter for Auto. Prefer readable scan-like output:
     * white paper (Clean), clear text (Soft B&W), real color (Enhanced), and only
     * fall back to flat Grayscale when nothing stronger is justified.
     */
    internal fun automatic(
        profile: PageImageProfile?,
        scanMode: ScanMode = ScanMode.DOCUMENT,
        faceDetected: Boolean = false,
        faceDetectionAvailable: Boolean = true,
    ): PageFilterPreset {
        if (scanMode == ScanMode.ID_CARD) {
            if (!faceDetectionAvailable) return PageFilterPreset.ID_NATURAL
            profile ?: return if (faceDetected) {
                PageFilterPreset.ID_PORTRAIT
            } else {
                PageFilterPreset.ID_CLEAR
            }
            val difficultLighting = profile.brightness < 118.0 ||
                profile.contrast < 22.0 ||
                profile.backgroundUnevenness >= 15.0
            val textHeavy = profile.textDensity >= 0.018 ||
                profile.edgeDensity >= 0.052
            return when {
                faceDetected && difficultLighting -> PageFilterPreset.ID_CLEAR
                faceDetected -> PageFilterPreset.ID_PORTRAIT
                textHeavy -> PageFilterPreset.ID_TEXT
                difficultLighting -> PageFilterPreset.ID_CLEAR
                else -> PageFilterPreset.ID_NATURAL
            }
        }
        if (scanMode == ScanMode.BOOK) {
            profile ?: return PageFilterPreset.SHADOW_REDUCTION
            val carriesUsefulColor = profile.colorRatio >= 0.022 ||
                (profile.colorRatio >= 0.012 && profile.saturation >= 34.0)
            val hasGutterShadows =
                profile.shadowRatio >= 0.10 || profile.backgroundUnevenness >= 10.0
            return when {
                hasGutterShadows -> PageFilterPreset.SHADOW_REDUCTION
                carriesUsefulColor -> PageFilterPreset.ENHANCED_COLOR
                else -> PageFilterPreset.CLEAN
            }
        }
        profile ?: return PageFilterPreset.CLEAN

        val lowContent = profile.textDensity < 0.008 &&
            profile.edgeDensity < 0.02 &&
            profile.colorRatio < 0.01
        val receiptLike = profile.aspectRatio >= 1.7 &&
            profile.textDensity >= 0.015 &&
            profile.colorRatio < 0.025 &&
            profile.saturation < 30.0
        // Require real chromatic area so warm tungsten light doesn't look like "color".
        val carriesUsefulColor = profile.colorRatio >= 0.022 ||
            (profile.colorRatio >= 0.012 && profile.saturation >= 34.0)
        val strongShadows = profile.shadowRatio >= 0.15 || profile.backgroundUnevenness >= 16.0
        val unevenBackground = profile.backgroundUnevenness >= 12.0 || profile.shadowRatio >= 0.12
        val textHeavy = profile.textDensity >= 0.025 || profile.edgeDensity >= 0.055
        val veryTextHeavy = profile.textDensity >= 0.055 || profile.edgeDensity >= 0.09
        val difficultLighting = profile.brightness < 125.0 || profile.contrast < 22.0
        val wellLitPaper = profile.brightness >= 150.0 &&
            profile.contrast >= 26.0 &&
            !strongShadows

        return when {
            lowContent && !unevenBackground -> PageFilterPreset.ORIGINAL
            lowContent -> PageFilterPreset.SHADOW_REDUCTION
            receiptLike -> PageFilterPreset.RECEIPT
            // Strong cast shadows on color pages need dedicated shadow lift.
            carriesUsefulColor && strongShadows -> PageFilterPreset.SHADOW_REDUCTION
            // Mild unevenness: keep color rather than desaturating via shadow reduction.
            carriesUsefulColor && unevenBackground -> PageFilterPreset.ENHANCED_COLOR
            carriesUsefulColor -> PageFilterPreset.ENHANCED_COLOR
            // Dense mono text on clean lighting → soft B&W for crisp glyphs.
            veryTextHeavy && wellLitPaper -> PageFilterPreset.SOFT_BLACK_AND_WHITE
            // Text pages with shadows / dim light → clean paper (white background).
            textHeavy && (unevenBackground || difficultLighting) -> PageFilterPreset.CLEAN
            // Ordinary text documents: clean white paper reads better than flat grayscale.
            textHeavy && wellLitPaper -> PageFilterPreset.CLEAN
            textHeavy -> PageFilterPreset.GRAYSCALE
            // Mild content without strong text metrics: gentle clean-up.
            unevenBackground -> PageFilterPreset.CLEAN
            else -> PageFilterPreset.CLEAN
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
                clipLimit = lerp(1.25, 1.65, cleanup),
                localContrastStrength = lerp(0.28, 0.46, cleanup),
                tileGridSize = tileGridSize(it),
                contrastScale = lerp(1.0, 1.025, lowContrastNeed(it)),
                brightnessShift = lerp(0.0, 4.0, lowLightNeed(it)),
                saturationScale = naturalSaturationScale(it),
                whiteBalanceStrength = lerp(0.50, 0.68, shadows),
                sharpenAmount = lerp(1.04, 1.10, detail),
                sharpenSigma = lerp(0.75, 0.95, detail),
            )
        } ?: EnhancedColorTuning(
            bilateralDiameter = 5,
            bilateralSigmaColor = 22.0,
            bilateralSigmaSpace = 28.0,
            backgroundBlurSigma = 22.0,
            shadowStrength = 0.18,
            backgroundTarget = 232.0,
            clipLimit = 1.45,
            localContrastStrength = 0.36,
            tileGridSize = 8,
            contrastScale = 1.01,
            brightnessShift = 2.0,
            saturationScale = 1.0,
            whiteBalanceStrength = 0.58,
            sharpenAmount = 1.07,
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
                clipLimit = lerp(1.10, 1.35, shadows),
                localContrastStrength = lerp(0.12, 0.24, shadows),
                tileGridSize = tileGridSize(it),
                contrastScale = lerp(1.0, 1.015, lowContrastNeed(it)),
                brightnessShift = lerp(0.0, 2.0, lowLightNeed(it)),
                saturationScale = naturalSaturationScale(it),
                whiteBalanceStrength = lerp(0.62, 0.82, shadows),
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
            clipLimit = 1.25,
            localContrastStrength = 0.18,
            tileGridSize = 8,
            contrastScale = 1.005,
            brightnessShift = 1.0,
            saturationScale = 1.0,
            whiteBalanceStrength = 0.72,
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
                clipLimit = lerp(1.30, 1.75, cleanup),
                localContrastStrength = lerp(0.32, 0.55, cleanup),
                tileGridSize = tileGridSize(it),
                bilateralDiameter = scaledOddKernel(it.longestEdge, 520.0, 5, 7),
                bilateralSigmaColor = lerp(16.0, 27.0, cleanup),
                bilateralSigmaSpace = lerp(20.0, 31.0, cleanup),
                contrastScale = lerp(1.0, 1.035, lowContrastNeed(it)),
                brightnessShift = lerp(1.0, 5.0, lowLightNeed(it)),
                sharpenAmount = lerp(1.04, 1.10, detail),
                sharpenSigma = lerp(0.70, 0.90, detail),
            )
        } ?: GrayscaleTuning(
            backgroundBlurSigma = 24.0,
            shadowStrength = 0.30,
            backgroundTarget = 236.0,
            clipLimit = 1.52,
            localContrastStrength = 0.42,
            tileGridSize = 8,
            bilateralDiameter = 5,
            bilateralSigmaColor = 21.0,
            bilateralSigmaSpace = 25.0,
            contrastScale = 1.015,
            brightnessShift = 2.5,
            sharpenAmount = 1.07,
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
                clipLimit = lerp(1.20, 1.55, cleanup),
                localContrastStrength = lerp(0.24, 0.42, cleanup),
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
            clipLimit = 1.38,
            localContrastStrength = 0.32,
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
                clipLimit = lerp(1.18, 1.48, cleanup),
                localContrastStrength = lerp(0.18, 0.34, cleanup),
                tileGridSize = tileGridSize(it),
                contrastScale = lerp(1.0, 1.025, lowContrastNeed(it)),
                brightnessShift = lerp(2.0, 6.0, cleanup),
                sharpenAmount = lerp(1.03, 1.08, detail),
                sharpenSigma = lerp(0.65, 0.85, detail),
            )
        } ?: CleanTuning(
            backgroundBlurSigma = 28.0,
            shadowStrength = 0.60,
            backgroundTarget = 240.0,
            clipLimit = 1.36,
            localContrastStrength = 0.26,
            tileGridSize = 8,
            contrastScale = 1.01,
            brightnessShift = 4.0,
            sharpenAmount = 1.05,
            sharpenSigma = 0.75,
        )

    internal fun magicColor(profile: PageImageProfile?): MagicColorTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            MagicColorTuning(
                clipLimit = lerp(1.45, 1.90, cleanup),
                localContrastStrength = lerp(0.48, 0.68, cleanup),
                tileGridSize = tileGridSize(it),
                contrastScale = lerp(1.005, 1.035, lowContrastNeed(it)),
                brightnessShift = lerp(0.0, 3.0, lowLightNeed(it)),
                saturationScale = lerp(1.04, 1.12, colorRecoveryBoost(it)),
                whiteBalanceStrength = 0.24,
                sharpenAmount = lerp(1.06, 1.12, detail),
                sharpenSigma = lerp(0.75, 0.95, detail),
            )
        } ?: MagicColorTuning(
            clipLimit = 1.68,
            localContrastStrength = 0.56,
            tileGridSize = 8,
            contrastScale = 1.02,
            brightnessShift = 1.5,
            saturationScale = 1.08,
            whiteBalanceStrength = 0.24,
            sharpenAmount = 1.09,
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
                clipLimit = lerp(1.45, 2.05, cleanup),
                localContrastStrength = lerp(0.38, 0.58, cleanup),
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
            clipLimit = 1.72,
            localContrastStrength = 0.48,
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
                clipLimit = lerp(1.22, 1.62, cleanup),
                localContrastStrength = lerp(0.28, 0.46, cleanup),
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
            clipLimit = 1.42,
            localContrastStrength = 0.36,
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
