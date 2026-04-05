package `in`.c1ph3rj.scanly.core.processing

import kotlin.math.roundToInt

internal object AdaptivePageFilterTuning {
    internal data class OriginalTuning(
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class EnhancedColorTuning(
        val bilateralDiameter: Int,
        val bilateralSigmaColor: Double,
        val bilateralSigmaSpace: Double,
        val clipLimit: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
        val sharpenBias: Double,
    )

    internal data class GrayscaleTuning(
        val clipLimit: Double,
        val bilateralDiameter: Int,
        val bilateralSigmaColor: Double,
        val bilateralSigmaSpace: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class BlackAndWhiteTuning(
        val backgroundKernelSize: Int,
        val clipLimit: Double,
        val denoiseDiameter: Int,
        val denoiseSigmaColor: Double,
        val denoiseSigmaSpace: Double,
        val windowSize: Int,
        val k: Double,
    )

    internal data class CleanTuning(
        val backgroundKernelSize: Int,
        val clipLimit: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class MagicColorTuning(
        val clipLimit: Double,
        val saturationScale: Double,
        val saturationShift: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
        val sharpenBias: Double,
    )

    internal data class ReceiptTuning(
        val clipLimit: Double,
        val bilateralDiameter: Int,
        val bilateralSigmaColor: Double,
        val bilateralSigmaSpace: Double,
        val blockSize: Int,
        val c: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal data class SoftBlackAndWhiteTuning(
        val backgroundKernelSize: Int,
        val clipLimit: Double,
        val denoiseDiameter: Int,
        val denoiseSigmaColor: Double,
        val denoiseSigmaSpace: Double,
        val windowSize: Int,
        val k: Double,
        val blurSigma: Double,
        val sharpenAmount: Double,
        val sharpenSigma: Double,
    )

    internal fun original(profile: PageImageProfile?): OriginalTuning =
        profile?.let {
            val detailBoost = detailBoost(it)
            val clarityBoost = clarityBoost(it)
            OriginalTuning(
                sharpenAmount = lerp(1.08, 1.22, detailBoost),
                sharpenSigma = lerp(0.8, 1.15, clarityBoost),
            )
        } ?: OriginalTuning(
            sharpenAmount = 1.14,
            sharpenSigma = 1.0,
        )

    internal fun enhancedColor(profile: PageImageProfile?): EnhancedColorTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            EnhancedColorTuning(
                bilateralDiameter = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 230.0,
                    min = 5,
                    max = 11,
                ),
                bilateralSigmaColor = lerp(32.0, 60.0, cleanup),
                bilateralSigmaSpace = lerp(32.0, 60.0, cleanup),
                clipLimit = lerp(2.0, 3.5, cleanup),
                sharpenAmount = lerp(1.10, 1.24, detail),
                sharpenSigma = lerp(0.85, 1.25, detail),
                sharpenBias = lerp(0.5, 3.5, cleanup),
            )
        } ?: EnhancedColorTuning(
            bilateralDiameter = 7,
            bilateralSigmaColor = 40.0,
            bilateralSigmaSpace = 40.0,
            clipLimit = 2.4,
            sharpenAmount = 1.16,
            sharpenSigma = 1.1,
            sharpenBias = 2.0,
        )

    internal fun grayscale(profile: PageImageProfile?): GrayscaleTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            GrayscaleTuning(
                clipLimit = lerp(2.0, 3.1, cleanup),
                bilateralDiameter = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 260.0,
                    min = 5,
                    max = 9,
                ),
                bilateralSigmaColor = lerp(28.0, 48.0, cleanup),
                bilateralSigmaSpace = lerp(28.0, 48.0, cleanup),
                sharpenAmount = lerp(1.08, 1.25, detail),
                sharpenSigma = lerp(0.75, 1.1, detail),
            )
        } ?: GrayscaleTuning(
            clipLimit = 2.4,
            bilateralDiameter = 7,
            bilateralSigmaColor = 35.0,
            bilateralSigmaSpace = 35.0,
            sharpenAmount = 1.18,
            sharpenSigma = 0.9,
        )

    internal fun blackAndWhite(profile: PageImageProfile?): BlackAndWhiteTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val binarization = binarizationBoost(it)
            BlackAndWhiteTuning(
                backgroundKernelSize = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 72.0,
                    min = 23,
                    max = 61,
                ),
                clipLimit = lerp(1.8, 3.1, cleanup),
                denoiseDiameter = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 320.0,
                    min = 5,
                    max = 9,
                ),
                denoiseSigmaColor = lerp(26.0, 48.0, cleanup),
                denoiseSigmaSpace = lerp(26.0, 48.0, cleanup),
                windowSize = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 62.0,
                    min = 31,
                    max = 55,
                ),
                k = lerp(0.14, 0.26, binarization),
            )
        } ?: BlackAndWhiteTuning(
            backgroundKernelSize = 31,
            clipLimit = 2.1,
            denoiseDiameter = 5,
            denoiseSigmaColor = 30.0,
            denoiseSigmaSpace = 30.0,
            windowSize = 35,
            k = 0.2,
        )

    internal fun clean(profile: PageImageProfile?): CleanTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            CleanTuning(
                backgroundKernelSize = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 88.0,
                    min = 19,
                    max = 45,
                ),
                clipLimit = lerp(1.8, 2.8, cleanup),
                sharpenAmount = lerp(1.08, 1.22, detail),
                sharpenSigma = lerp(0.7, 1.0, detail),
            )
        } ?: CleanTuning(
            backgroundKernelSize = 21,
            clipLimit = 2.2,
            sharpenAmount = 1.16,
            sharpenSigma = 0.8,
        )

    internal fun magicColor(profile: PageImageProfile?): MagicColorTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            MagicColorTuning(
                clipLimit = lerp(2.2, 3.4, cleanup),
                saturationScale = lerp(1.10, 1.32, colorRecoveryBoost(it)),
                saturationShift = lerp(6.0, 18.0, cleanup),
                sharpenAmount = lerp(1.10, 1.26, detail),
                sharpenSigma = lerp(0.85, 1.2, detail),
                sharpenBias = lerp(1.5, 5.0, cleanup),
            )
        } ?: MagicColorTuning(
            clipLimit = 2.8,
            saturationScale = 1.18,
            saturationShift = 10.0,
            sharpenAmount = 1.18,
            sharpenSigma = 1.0,
            sharpenBias = 4.0,
        )

    internal fun receipt(profile: PageImageProfile?): ReceiptTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            ReceiptTuning(
                clipLimit = lerp(2.6, 3.8, cleanup),
                bilateralDiameter = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 210.0,
                    min = 7,
                    max = 11,
                ),
                bilateralSigmaColor = lerp(45.0, 70.0, cleanup),
                bilateralSigmaSpace = lerp(45.0, 70.0, cleanup),
                blockSize = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 84.0,
                    min = 19,
                    max = 35,
                ),
                c = lerp(3.0, 10.0, cleanup),
                sharpenAmount = lerp(1.06, 1.2, detail),
                sharpenSigma = lerp(0.65, 0.9, detail),
            )
        } ?: ReceiptTuning(
            clipLimit = 3.2,
            bilateralDiameter = 9,
            bilateralSigmaColor = 55.0,
            bilateralSigmaSpace = 55.0,
            blockSize = 21,
            c = 7.0,
            sharpenAmount = 1.12,
            sharpenSigma = 0.7,
        )

    internal fun softBlackAndWhite(profile: PageImageProfile?): SoftBlackAndWhiteTuning =
        profile?.let {
            val cleanup = cleanupBoost(it)
            val detail = detailBoost(it)
            val binarization = binarizationBoost(it)
            SoftBlackAndWhiteTuning(
                backgroundKernelSize = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 84.0,
                    min = 19,
                    max = 51,
                ),
                clipLimit = lerp(1.4, 2.2, cleanup),
                denoiseDiameter = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 320.0,
                    min = 5,
                    max = 7,
                ),
                denoiseSigmaColor = lerp(20.0, 34.0, cleanup),
                denoiseSigmaSpace = lerp(20.0, 34.0, cleanup),
                windowSize = scaledOddKernel(
                    longestEdge = it.longestEdge,
                    divisor = 66.0,
                    min = 29,
                    max = 49,
                ),
                k = lerp(0.12, 0.22, binarization),
                blurSigma = lerp(0.9, 1.35, cleanup),
                sharpenAmount = lerp(1.02, 1.12, detail),
                sharpenSigma = lerp(0.7, 0.95, detail),
            )
        } ?: SoftBlackAndWhiteTuning(
            backgroundKernelSize = 27,
            clipLimit = 1.7,
            denoiseDiameter = 5,
            denoiseSigmaColor = 24.0,
            denoiseSigmaSpace = 24.0,
            windowSize = 33,
            k = 0.16,
            blurSigma = 1.15,
            sharpenAmount = 1.04,
            sharpenSigma = 0.8,
        )

    private fun detailBoost(profile: PageImageProfile): Double =
        normalized(
            value = (profile.edgeDensity * 0.6) + ((1.0 - normalized(profile.sharpness, 10.0, 85.0)) * 1.4),
            min = 0.0,
            max = 2.0,
        )

    private fun cleanupBoost(profile: PageImageProfile): Double {
        val brightnessNeed = 1.0 - normalized(profile.brightness, 92.0, 188.0)
        val contrastNeed = 1.0 - normalized(profile.contrast, 16.0, 52.0)
        val shadowNeed = profile.shadowRatio.coerceIn(0.0, 1.0)
        val highlightNeed = profile.highlightRatio.coerceIn(0.0, 1.0)
        val saturationNeed = 1.0 - normalized(profile.saturation, 20.0, 120.0)
        return normalized(
            value = (brightnessNeed * 0.22) + (contrastNeed * 0.26) + (shadowNeed * 0.22) + (highlightNeed * 0.10) + (saturationNeed * 0.20),
            min = 0.0,
            max = 1.0,
        )
    }

    private fun clarityBoost(profile: PageImageProfile): Double =
        normalized(profile.sharpness, 12.0, 75.0)

    private fun binarizationBoost(profile: PageImageProfile): Double {
        val brightnessNeed = 1.0 - normalized(profile.brightness, 96.0, 192.0)
        val shadowNeed = profile.shadowRatio.coerceIn(0.0, 1.0)
        val contrastNeed = 1.0 - normalized(profile.contrast, 18.0, 58.0)
        val highlightNeed = profile.highlightRatio.coerceIn(0.0, 1.0)
        val sharpnessNeed = 1.0 - normalized(profile.sharpness, 12.0, 65.0)
        return normalized(
            value = (brightnessNeed * 0.12) + (shadowNeed * 0.32) + (contrastNeed * 0.26) + (highlightNeed * 0.10) + (sharpnessNeed * 0.20),
            min = 0.0,
            max = 1.0,
        )
    }

    private fun colorRecoveryBoost(profile: PageImageProfile): Double {
        val brightnessNeed = 1.0 - normalized(profile.brightness, 92.0, 188.0)
        val saturationNeed = 1.0 - normalized(profile.saturation, 18.0, 120.0)
        val contrastNeed = 1.0 - normalized(profile.contrast, 18.0, 52.0)
        val shadowNeed = profile.shadowRatio.coerceIn(0.0, 1.0)
        return normalized(
            value = (brightnessNeed * 0.18) + (saturationNeed * 0.40) + (contrastNeed * 0.24) + (shadowNeed * 0.18),
            min = 0.0,
            max = 1.0,
        )
    }

    private fun normalized(
        value: Double,
        min: Double,
        max: Double,
    ): Double {
        if (max <= min) {
            return 0.0
        }
        return ((value - min) / (max - min)).coerceIn(0.0, 1.0)
    }

    private fun lerp(
        start: Double,
        end: Double,
        fraction: Double,
    ): Double {
        val normalizedFraction = fraction.coerceIn(0.0, 1.0)
        return start + ((end - start) * normalizedFraction)
    }

    private fun scaledOddKernel(
        longestEdge: Int,
        divisor: Double,
        min: Int,
        max: Int,
    ): Int {
        val raw = (longestEdge / divisor).roundToInt().coerceIn(min, max)
        return toOddWithin(raw, min, max)
    }

    private fun toOddWithin(
        value: Int,
        min: Int,
        max: Int,
    ): Int {
        var candidate = value.coerceIn(min, max)
        if (candidate % 2 == 0) {
            candidate = when {
                candidate >= max -> candidate - 1
                else -> candidate + 1
            }
        }
        return candidate.coerceIn(min, max)
    }
}
