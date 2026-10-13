package `in`.c1ph3rj.scanly.core.processing.filter

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.processing.PageImageProfile
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Renders a preset with the reflectance pipeline:
 *
 * ```
 * page → paper-aware background model (360 px) → divide shading out (per channel for color)
 *      → one tone curve (black point / white point / gamma) → vibrance (color)
 *      → unsharp mask   |   B&W: anti-aliased global threshold + despeckle
 * ```
 */
internal object PageFilterEngine {
    /** Normalized paper level; the tone curve's white point then clips it to pure white. */
    private const val NORMALIZED_PAPER = 250.0

    fun render(
        sourceRgba: Mat,
        filterPreset: PageFilterPreset,
        profile: PageImageProfile?,
        intensity: Double,
        renderLongestEdge: Int,
    ): Bitmap {
        if (filterPreset == PageFilterPreset.ORIGINAL) {
            return sourceRgba.toBitmap()
        }
        val params = PageFilterLook.resolve(
            preset = filterPreset,
            profile = profile,
            intensity = intensity,
            renderLongestEdge = renderLongestEdge,
        )
        val rgb = Mat()
        val result = Mat()
        var background: PageBackgroundModel? = null
        try {
            Imgproc.cvtColor(sourceRgba, rgb, Imgproc.COLOR_RGBA2RGB)
            background = PageBackgroundModel.estimate(rgb, params.strokeFraction)
            when (params.mode) {
                PageFilterRenderMode.Color -> renderColor(rgb, background, params, result)
                PageFilterRenderMode.Natural -> renderNatural(rgb, background, params, result)
                PageFilterRenderMode.Gray -> renderGray(rgb, background, params, result)
                PageFilterRenderMode.Binary -> renderBinary(rgb, background, params, result)
            }
            return result.toBitmap()
        } finally {
            background?.release()
            rgb.release()
            result.release()
        }
    }

    private fun renderColor(
        rgb: Mat,
        background: PageBackgroundModel,
        params: PageFilterRenderParams,
        resultRgba: Mat,
    ) {
        val normalized = normalizeColor(rgb, background, params) { NORMALIZED_PAPER }
        try {
            PageFilterOperators.applyLut(
                normalized,
                normalized,
                PageFilterOperators.toneCurve(params.blackPoint, params.whitePoint, params.gamma),
            )
            PageFilterOperators.vibrance(normalized, normalized, params.vibrance)
            PageFilterOperators.sharpen(normalized, normalized, params.sharpenAmount, params.sharpenSigma)
            Imgproc.cvtColor(normalized, resultRgba, Imgproc.COLOR_RGB2RGBA)
        } finally {
            normalized.release()
        }
    }

    private fun renderNatural(
        rgb: Mat,
        background: PageBackgroundModel,
        params: PageFilterRenderParams,
        resultRgba: Mat,
    ) {
        val reference = background.paperReference
        val paperGray = background.paperReferenceGray.coerceAtLeast(1.0)
        // Lift paper toward ~238 (never darken an already-bright page).
        val liftedGray = maxOf(paperGray * 1.25, paperGray + 30.0).coerceAtMost(NATURAL_PAPER_MAX)
        val targetGray = paperGray + (liftedGray - paperGray).coerceAtLeast(0.0) * params.naturalLift
        val normalized = normalizeColor(rgb, background, params) { index ->
            val channelPaper = reference[index]
            val balanced = channelPaper + (paperGray - channelPaper) * params.naturalWhiteBalance
            (balanced * targetGray / paperGray).coerceIn(1.0, 255.0)
        }
        try {
            PageFilterOperators.applyLut(
                normalized,
                normalized,
                PageFilterOperators.toneCurve(params.blackPoint, params.whitePoint, params.gamma),
            )
            PageFilterOperators.sharpen(normalized, normalized, params.sharpenAmount, params.sharpenSigma)
            Imgproc.cvtColor(normalized, resultRgba, Imgproc.COLOR_RGB2RGBA)
        } finally {
            normalized.release()
        }
    }

    private fun renderGray(
        rgb: Mat,
        background: PageBackgroundModel,
        params: PageFilterRenderParams,
        resultRgba: Mat,
    ) {
        val gray = normalizeGray(rgb, background, params)
        try {
            PageFilterOperators.applyLut(
                gray,
                gray,
                PageFilterOperators.toneCurve(params.blackPoint, params.whitePoint, params.gamma),
            )
            PageFilterOperators.sharpen(gray, gray, params.sharpenAmount, params.sharpenSigma)
            Imgproc.cvtColor(gray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
        } finally {
            gray.release()
        }
    }

    private fun renderBinary(
        rgb: Mat,
        background: PageBackgroundModel,
        params: PageFilterRenderParams,
        resultRgba: Mat,
    ) {
        val gray = normalizeGray(rgb, background, params)
        try {
            PageFilterOperators.binarize(gray, gray, params.binaryThreshold, params.binaryRamp)
            PageFilterOperators.despeckle(gray, params.despeckleMinArea)
            Imgproc.cvtColor(gray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
        } finally {
            gray.release()
        }
    }

    /** Divides each RGB channel by its own background, mapping paper to [targetFor] (index → level). */
    private inline fun normalizeColor(
        rgb: Mat,
        background: PageBackgroundModel,
        params: PageFilterRenderParams,
        targetFor: (Int) -> Double,
    ): Mat {
        val channels = mutableListOf<Mat>()
        val merged = Mat()
        try {
            Core.split(rgb, channels)
            channels.forEachIndexed { index, channel ->
                PageBackgroundModel.normalizeChannel(
                    source = channel,
                    backgroundSmall = background.channel(index),
                    reference = background.paperReference[index],
                    target = targetFor(index),
                    maxGain = params.maxGain,
                    denoiseSigma = params.denoiseSigma,
                    output = channel,
                )
            }
            Core.merge(channels, merged)
            return merged
        } catch (error: Throwable) {
            merged.release()
            throw error
        } finally {
            channels.forEach(Mat::release)
        }
    }

    private fun normalizeGray(
        rgb: Mat,
        background: PageBackgroundModel,
        params: PageFilterRenderParams,
    ): Mat {
        val gray = Mat()
        val grayBackground = background.grayBackground()
        try {
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            PageBackgroundModel.normalizeChannel(
                source = gray,
                backgroundSmall = grayBackground,
                reference = background.paperReferenceGray,
                target = NORMALIZED_PAPER,
                maxGain = params.maxGain,
                denoiseSigma = params.denoiseSigma,
                output = gray,
            )
            return gray
        } catch (error: Throwable) {
            gray.release()
            throw error
        } finally {
            grayBackground.release()
        }
    }

    private fun Mat.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bitmap)
        return bitmap
    }

    private const val NATURAL_PAPER_MAX = 238.0
}
