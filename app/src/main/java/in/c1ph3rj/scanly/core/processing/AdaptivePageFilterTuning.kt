package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import kotlin.math.roundToInt

/**
 * Builds adaptive [FilterRecipe] values from image profile + user adjustments.
 *
 * Recipes are intentionally conservative by default. User [PageFilterAdjustments]
 * scale shadow reduction, detail, tone, intensity blend, and binary aggressiveness
 * so a single bad adaptive guess is always recoverable from the editor.
 */
internal object AdaptivePageFilterTuning {
    internal enum class RenderMode {
        ORIGINAL,
        COLOR,
        GRAY,
        BINARY,
        SOFT_BINARY,
    }

    /**
     * Fully resolved, unit-less pipeline parameters ready for OpenCV stages.
     */
    internal data class FilterRecipe(
        val mode: RenderMode,
        val denoiseStrength: Double,
        val backgroundBlurSigma: Double,
        val shadowStrength: Double,
        val backgroundTarget: Double,
        val textMaskSensitivity: Double,
        val textProtectBlend: Double,
        val clipLimit: Double,
        val tileGridSize: Int,
        val contrastScale: Double,
        val brightnessShift: Double,
        val saturationScale: Double,
        val whiteBalance: Boolean,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
        val blockSize: Int,
        val thresholdC: Double,
        val binaryBlend: Double,
        val intensity: Double,
        val cleanupSpeckles: Boolean,
    )

    internal fun automatic(profile: PageImageProfile?): PageFilterPreset {
        profile ?: return PageFilterPreset.GRAYSCALE

        val receiptLike = profile.aspectRatio >= 1.7 &&
            profile.textDensity >= 0.015 &&
            profile.colorRatio < 0.12
        val photoLike = profile.colorRatio >= 0.08 &&
            profile.saturation >= 40.0 &&
            profile.textDensity < 0.02 &&
            profile.edgeDensity < 0.08
        val carriesUsefulColor = profile.colorRatio >= 0.02 || profile.saturation >= 24.0
        val unevenBackground = profile.backgroundUnevenness >= 12.0 || profile.shadowRatio >= 0.12
        val textHeavy = profile.textDensity >= 0.025 || profile.edgeDensity >= 0.055
        val difficultLighting = profile.brightness < 125.0 || profile.contrast < 22.0
        val fadedPrint = profile.contrast < 18.0 ||
            (profile.brightness in 140.0..210.0 && profile.contrast < 26.0 && profile.textDensity >= 0.02)

        return when {
            receiptLike -> PageFilterPreset.RECEIPT
            photoLike -> PageFilterPreset.PHOTO
            carriesUsefulColor && unevenBackground -> PageFilterPreset.SHADOW_REDUCTION
            carriesUsefulColor && fadedPrint -> PageFilterPreset.MAGIC_COLOR
            carriesUsefulColor -> PageFilterPreset.ENHANCED_COLOR
            fadedPrint && textHeavy -> PageFilterPreset.HIGH_CONTRAST
            textHeavy && unevenBackground -> PageFilterPreset.CLEAN
            textHeavy && difficultLighting -> PageFilterPreset.SOFT_BLACK_AND_WHITE
            else -> PageFilterPreset.GRAYSCALE
        }
    }

    internal fun recipe(
        preset: PageFilterPreset,
        profile: PageImageProfile?,
        adjustments: PageFilterAdjustments = PageFilterAdjustments.Default,
    ): FilterRecipe {
        val safeAdjustments = adjustments.sanitized()
        val resolvedPreset = if (preset == PageFilterPreset.AUTO) {
            automatic(profile)
        } else {
            preset
        }
        val base = baseRecipe(resolvedPreset, profile)
        return applyAdjustments(base, safeAdjustments, resolvedPreset)
    }

    private fun baseRecipe(
        preset: PageFilterPreset,
        profile: PageImageProfile?,
    ): FilterRecipe {
        val longestEdge = profile?.longestEdge ?: 1_800
        val cleanup = profile?.let(::cleanupBoost) ?: 0.45
        val detail = profile?.let(::detailBoost) ?: 0.45
        val shadows = profile?.let(::shadowBoost) ?: 0.40
        val faintText = profile?.let(::lowContrastNeed) ?: 0.40
        val lowLight = profile?.let(::lowLightNeed) ?: 0.30

        return when (preset) {
            PageFilterPreset.ORIGINAL,
            PageFilterPreset.AUTO,
            -> FilterRecipe(
                mode = RenderMode.ORIGINAL,
                denoiseStrength = 0.0,
                backgroundBlurSigma = 1.0,
                shadowStrength = 0.0,
                backgroundTarget = 232.0,
                textMaskSensitivity = 12.0,
                textProtectBlend = 0.0,
                clipLimit = 1.0,
                tileGridSize = 8,
                contrastScale = 1.0,
                brightnessShift = 0.0,
                saturationScale = 1.0,
                whiteBalance = false,
                sharpenAmount = 1.0,
                sharpenSigma = 0.8,
                blockSize = 41,
                thresholdC = 12.0,
                binaryBlend = 0.0,
                intensity = 1.0,
                cleanupSpeckles = false,
            )

            PageFilterPreset.ENHANCED_COLOR -> FilterRecipe(
                mode = RenderMode.COLOR,
                denoiseStrength = lerp(0.35, 0.65, cleanup),
                backgroundBlurSigma = scaledSigma(longestEdge, 90.0, 12.0, 30.0),
                shadowStrength = lerp(0.08, 0.26, shadows),
                backgroundTarget = 230.0,
                textMaskSensitivity = lerp(14.0, 10.0, faintText),
                textProtectBlend = 0.40,
                clipLimit = lerp(1.20, 1.65, cleanup),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = lerp(1.0, 1.02, faintText),
                brightnessShift = lerp(0.0, 3.0, lowLight),
                saturationScale = naturalSaturationScale(profile),
                whiteBalance = true,
                sharpenAmount = lerp(1.04, 1.12, detail),
                sharpenSigma = lerp(0.75, 0.95, detail),
                blockSize = 41,
                thresholdC = 12.0,
                binaryBlend = 0.0,
                intensity = 1.0,
                cleanupSpeckles = false,
            )

            PageFilterPreset.PHOTO -> FilterRecipe(
                mode = RenderMode.COLOR,
                denoiseStrength = lerp(0.20, 0.45, cleanup),
                backgroundBlurSigma = scaledSigma(longestEdge, 110.0, 10.0, 24.0),
                shadowStrength = lerp(0.04, 0.16, shadows),
                backgroundTarget = 224.0,
                textMaskSensitivity = 14.0,
                textProtectBlend = 0.55,
                clipLimit = lerp(1.05, 1.35, cleanup),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = lerp(1.0, 1.015, faintText),
                brightnessShift = lerp(0.0, 2.0, lowLight),
                saturationScale = lerp(0.98, 1.04, colorRecoveryBoost(profile)),
                whiteBalance = true,
                sharpenAmount = lerp(1.02, 1.08, detail),
                sharpenSigma = lerp(0.70, 0.90, detail),
                blockSize = 41,
                thresholdC = 12.0,
                binaryBlend = 0.0,
                intensity = 1.0,
                cleanupSpeckles = false,
            )

            PageFilterPreset.SHADOW_REDUCTION -> FilterRecipe(
                mode = RenderMode.COLOR,
                denoiseStrength = lerp(0.25, 0.50, shadows),
                backgroundBlurSigma = scaledSigma(longestEdge, 72.0, 16.0, 38.0),
                shadowStrength = lerp(0.38, 0.68, shadows),
                backgroundTarget = 234.0,
                textMaskSensitivity = lerp(13.0, 9.0, faintText),
                textProtectBlend = 0.28,
                clipLimit = lerp(1.10, 1.40, shadows),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = lerp(1.0, 1.015, faintText),
                brightnessShift = lerp(0.0, 2.0, lowLight),
                saturationScale = naturalSaturationScale(profile),
                whiteBalance = true,
                sharpenAmount = lerp(1.02, 1.08, detail),
                sharpenSigma = lerp(0.75, 0.90, detail),
                blockSize = 41,
                thresholdC = 12.0,
                binaryBlend = 0.0,
                intensity = 1.0,
                cleanupSpeckles = false,
            )

            PageFilterPreset.MAGIC_COLOR -> FilterRecipe(
                mode = RenderMode.COLOR,
                denoiseStrength = lerp(0.15, 0.35, cleanup),
                backgroundBlurSigma = scaledSigma(longestEdge, 100.0, 10.0, 22.0),
                shadowStrength = lerp(0.06, 0.18, shadows),
                backgroundTarget = 228.0,
                textMaskSensitivity = 12.0,
                textProtectBlend = 0.45,
                clipLimit = lerp(1.50, 2.10, cleanup),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = lerp(1.005, 1.04, faintText),
                brightnessShift = lerp(0.0, 3.5, lowLight),
                saturationScale = lerp(1.05, 1.14, colorRecoveryBoost(profile)),
                whiteBalance = true,
                sharpenAmount = lerp(1.06, 1.14, detail),
                sharpenSigma = lerp(0.75, 0.95, detail),
                blockSize = 41,
                thresholdC = 12.0,
                binaryBlend = 0.0,
                intensity = 1.0,
                cleanupSpeckles = false,
            )

            PageFilterPreset.GRAYSCALE -> FilterRecipe(
                mode = RenderMode.GRAY,
                denoiseStrength = lerp(0.30, 0.60, cleanup),
                backgroundBlurSigma = scaledSigma(longestEdge, 84.0, 12.0, 32.0),
                shadowStrength = lerp(0.16, 0.40, shadows),
                backgroundTarget = 236.0,
                textMaskSensitivity = lerp(14.0, 9.0, faintText),
                textProtectBlend = 0.35,
                clipLimit = lerp(1.30, 1.85, cleanup),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = lerp(1.0, 1.03, faintText),
                brightnessShift = lerp(1.0, 4.0, lowLight),
                saturationScale = 1.0,
                whiteBalance = false,
                sharpenAmount = lerp(1.04, 1.12, detail),
                sharpenSigma = lerp(0.70, 0.90, detail),
                blockSize = 41,
                thresholdC = 12.0,
                binaryBlend = 0.0,
                intensity = 1.0,
                cleanupSpeckles = false,
            )

            PageFilterPreset.CLEAN -> FilterRecipe(
                mode = RenderMode.GRAY,
                denoiseStrength = lerp(0.20, 0.40, cleanup),
                backgroundBlurSigma = scaledSigma(longestEdge, 64.0, 18.0, 42.0),
                shadowStrength = lerp(0.42, 0.70, shadows),
                backgroundTarget = 242.0,
                textMaskSensitivity = lerp(12.0, 7.5, faintText),
                textProtectBlend = 0.22,
                clipLimit = lerp(1.20, 1.65, cleanup),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = lerp(1.0, 1.02, faintText),
                brightnessShift = lerp(2.0, 5.0, cleanup),
                saturationScale = 1.0,
                whiteBalance = false,
                sharpenAmount = lerp(1.03, 1.09, detail),
                sharpenSigma = lerp(0.65, 0.85, detail),
                blockSize = 41,
                thresholdC = 12.0,
                binaryBlend = 0.0,
                intensity = 1.0,
                cleanupSpeckles = false,
            )

            PageFilterPreset.BLACK_AND_WHITE -> FilterRecipe(
                mode = RenderMode.BINARY,
                denoiseStrength = lerp(0.35, 0.60, cleanup),
                backgroundBlurSigma = scaledSigma(longestEdge, 70.0, 16.0, 36.0),
                shadowStrength = lerp(0.55, 0.78, shadows),
                backgroundTarget = 242.0,
                textMaskSensitivity = lerp(13.0, 8.0, faintText),
                textProtectBlend = 0.18,
                clipLimit = lerp(1.20, 1.60, cleanup),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = 1.0,
                brightnessShift = 0.0,
                saturationScale = 1.0,
                whiteBalance = false,
                sharpenAmount = 1.0,
                sharpenSigma = 0.7,
                blockSize = scaledOddKernel(longestEdge, 36.0, 35, 75),
                thresholdC = (12.0 + (shadows * 1.2) - (faintText * 1.4)).coerceIn(10.0, 14.0),
                binaryBlend = 1.0,
                intensity = 1.0,
                cleanupSpeckles = true,
            )

            PageFilterPreset.SOFT_BLACK_AND_WHITE -> FilterRecipe(
                mode = RenderMode.SOFT_BINARY,
                denoiseStrength = lerp(0.30, 0.55, cleanup),
                backgroundBlurSigma = scaledSigma(longestEdge, 80.0, 14.0, 32.0),
                shadowStrength = lerp(0.36, 0.58, shadows),
                backgroundTarget = 238.0,
                textMaskSensitivity = lerp(12.0, 8.0, faintText),
                textProtectBlend = 0.28,
                clipLimit = lerp(1.25, 1.70, cleanup),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = 1.0,
                brightnessShift = 0.0,
                saturationScale = 1.0,
                whiteBalance = false,
                sharpenAmount = lerp(1.02, 1.08, detail),
                sharpenSigma = lerp(0.65, 0.82, detail),
                blockSize = scaledOddKernel(longestEdge, 38.0, 31, 71),
                thresholdC = (10.5 + shadows - faintText).coerceIn(9.0, 12.5),
                binaryBlend = lerp(0.32, 0.46, cleanup),
                intensity = 1.0,
                cleanupSpeckles = false,
            )

            PageFilterPreset.RECEIPT -> FilterRecipe(
                mode = RenderMode.SOFT_BINARY,
                denoiseStrength = lerp(0.30, 0.55, cleanup),
                backgroundBlurSigma = scaledSigma(longestEdge, 76.0, 16.0, 34.0),
                shadowStrength = lerp(0.55, 0.78, shadows),
                backgroundTarget = 244.0,
                textMaskSensitivity = lerp(11.0, 7.0, faintText),
                textProtectBlend = 0.20,
                clipLimit = lerp(1.55, 2.20, cleanup),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = 1.0,
                brightnessShift = 0.0,
                saturationScale = 1.0,
                whiteBalance = false,
                sharpenAmount = lerp(1.02, 1.08, detail),
                sharpenSigma = lerp(0.60, 0.78, detail),
                blockSize = scaledOddKernel(longestEdge, 30.0, 41, 85),
                thresholdC = (10.5 + (shadows * 1.3) - (faintText * 1.1)).coerceIn(9.5, 13.0),
                binaryBlend = lerp(0.50, 0.64, cleanup),
                intensity = 1.0,
                cleanupSpeckles = true,
            )

            PageFilterPreset.HIGH_CONTRAST -> FilterRecipe(
                mode = RenderMode.SOFT_BINARY,
                denoiseStrength = lerp(0.25, 0.50, cleanup),
                backgroundBlurSigma = scaledSigma(longestEdge, 78.0, 14.0, 34.0),
                shadowStrength = lerp(0.30, 0.55, shadows),
                backgroundTarget = 240.0,
                textMaskSensitivity = lerp(12.0, 7.5, faintText),
                textProtectBlend = 0.25,
                clipLimit = lerp(1.55, 2.15, cleanup),
                tileGridSize = tileGridSize(longestEdge),
                contrastScale = lerp(1.02, 1.06, faintText),
                brightnessShift = lerp(0.0, 2.0, lowLight),
                saturationScale = 1.0,
                whiteBalance = false,
                sharpenAmount = lerp(1.05, 1.14, detail),
                sharpenSigma = lerp(0.70, 0.90, detail),
                blockSize = scaledOddKernel(longestEdge, 40.0, 31, 71),
                thresholdC = (11.0 + shadows - (faintText * 1.2)).coerceIn(9.0, 13.0),
                binaryBlend = lerp(0.18, 0.34, cleanup),
                intensity = 1.0,
                cleanupSpeckles = false,
            )
        }
    }

    private fun applyAdjustments(
        base: FilterRecipe,
        adjustments: PageFilterAdjustments,
        preset: PageFilterPreset,
    ): FilterRecipe {
        if (base.mode == RenderMode.ORIGINAL && adjustments.isDefault) {
            return base.copy(intensity = adjustments.intensity.toDouble())
        }

        val shadowScale = lerp(0.15, 1.85, adjustments.shadows.toDouble())
        val detailScale = lerp(0.0, 1.0, adjustments.details.toDouble())
        // Midpoint 0.5 keeps adaptive defaults.
        // Higher Ink keeps more white paper (raises adaptive C); lower pulls more ink.
        val thresholdShift = (adjustments.threshold.toDouble() - 0.5) * 5.0
        val brightnessExtra = adjustments.brightness.toDouble() * 28.0
        val contrastExtra = 1.0 + (adjustments.contrast.toDouble() * 0.28)

        val sharpenFloor = if (base.mode == RenderMode.BINARY) 1.0 else 1.0
        val sharpenPeak = if (base.mode == RenderMode.BINARY) 1.0 else maxOf(base.sharpenAmount, 1.02)
        val resolvedSharpen = if (base.mode == RenderMode.BINARY) {
            1.0
        } else {
            lerp(sharpenFloor, sharpenPeak + 0.08, detailScale)
        }

        return base.copy(
            shadowStrength = (base.shadowStrength * shadowScale).coerceIn(0.0, 0.92),
            contrastScale = (base.contrastScale * contrastExtra).coerceIn(0.75, 1.45),
            brightnessShift = (base.brightnessShift + brightnessExtra).coerceIn(-40.0, 40.0),
            sharpenAmount = resolvedSharpen.coerceIn(1.0, 1.30),
            sharpenSigma = if (detailScale < 0.15) {
                base.sharpenSigma
            } else {
                (base.sharpenSigma * lerp(0.85, 1.15, detailScale)).coerceIn(0.5, 1.3)
            },
            thresholdC = if (adjustments.supportsThresholdControl(preset) || base.mode == RenderMode.BINARY || base.mode == RenderMode.SOFT_BINARY) {
                (base.thresholdC + thresholdShift).coerceIn(6.0, 18.0)
            } else {
                base.thresholdC
            },
            binaryBlend = if (base.mode == RenderMode.SOFT_BINARY) {
                // Higher Ink keeps more continuous gray (less pure binary mix).
                (base.binaryBlend * lerp(1.35, 0.55, adjustments.threshold.toDouble()))
                    .coerceIn(0.0, 0.85)
            } else {
                base.binaryBlend
            },
            denoiseStrength = (base.denoiseStrength * lerp(1.15, 0.75, detailScale))
                .coerceIn(0.0, 1.0),
            intensity = adjustments.intensity.toDouble().coerceIn(0.0, 1.0),
        )
    }

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

    private fun colorRecoveryBoost(profile: PageImageProfile?): Double {
        profile ?: return 0.5
        return ((1.0 - normalized(profile.saturation, 20.0, 95.0)) * 0.55 +
            lowContrastNeed(profile) * 0.25 +
            lowLightNeed(profile) * 0.20)
            .coerceIn(0.0, 1.0)
    }

    private fun naturalSaturationScale(profile: PageImageProfile?): Double {
        profile ?: return 1.0
        return when {
            profile.saturation >= 100.0 -> 0.96
            profile.saturation >= 70.0 -> 0.98
            profile.saturation < 20.0 -> 1.04
            profile.saturation < 40.0 -> 1.02
            else -> 1.0
        }
    }

    private fun tileGridSize(longestEdge: Int): Int =
        if (longestEdge >= 1_800) 10 else 8

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
