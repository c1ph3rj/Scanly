package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.DetectionFrame
import kotlin.math.abs
import kotlin.math.sqrt
import androidx.annotation.StringRes
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.R

enum class CaptureSceneIssue(@param:StringRes val guidanceRes: Int) {
    LENS_BLOCKED(R.string.capture_scene_issue_lens_may_be_covered_clear),
    TOO_DARK(R.string.capture_scene_issue_too_dark_add_light_or),
    BLURRY(R.string.capture_scene_issue_image_looks_blurry_clean_the),
    /** Light reflecting off a glossy surface (ID cards); only checked when a mode asks for it. */
    GLARE(R.string.capture_scene_issue_glare_tilt_to_reduce),
    ;

    val guidance: String get() = appString(guidanceRes)
}

/**
 * @property edgeStrength mean luminance difference between pixels [SampleStep] apart. This is a
 * texture measure (it falls on a blank page) and no longer decides [CaptureSceneIssue.BLURRY].
 * @property edgeSharpness strength of the sharpest edges in the frame (adjacent-pixel luminance
 * steps near the top of their distribution) divided by the frame's contrast range, in 0..1. A crisp
 * paper edge scores near 1 regardless of how much content the page has; blur spreads the step over
 * several pixels and lowers it. [UNMEASURED_EDGE_SHARPNESS] when it cannot be judged (empty or flat
 * frame), which never reads as blurry.
 * @property centreHighlightFraction share (0..1) of samples in the central part of the frame that
 * are clipped to near white: the hot spot a lamp leaves on a glossy card.
 */
data class CaptureFrameQuality(
    val meanLuminance: Float,
    val luminanceDeviation: Float,
    val edgeStrength: Float,
    val edgeSharpness: Float = UNMEASURED_EDGE_SHARPNESS,
    val centreHighlightFraction: Float = 0f,
) {
    /** @param checkGlare report [CaptureSceneIssue.GLARE] for a clipped hot spot on a detected subject. */
    fun sceneIssue(hasDocumentCandidate: Boolean, checkGlare: Boolean = false): CaptureSceneIssue? = when {
        meanLuminance < BlockedMeanLuminance && luminanceDeviation < BlockedLuminanceDeviation -> {
            CaptureSceneIssue.LENS_BLOCKED
        }
        meanLuminance < DarkMeanLuminance -> CaptureSceneIssue.TOO_DARK
        hasDocumentCandidate && edgeSharpness < BlurryEdgeSharpness -> CaptureSceneIssue.BLURRY
        checkGlare && hasDocumentCandidate && centreHighlightFraction >= GlareHighlightFraction -> {
            CaptureSceneIssue.GLARE
        }
        else -> null
    }

    companion object {
        /** Reported when sharpness cannot be judged; treated as sharp so it never blocks auto-capture. */
        const val UNMEASURED_EDGE_SHARPNESS = 1f
    }
}

object CaptureFrameQualityAnalyzer {
    fun analyze(frame: DetectionFrame, checkSharpness: Boolean = true): CaptureFrameQuality {
        if (frame.width <= 0 || frame.height <= 0 || frame.bytes.size < RgbaPixelStride) {
            return CaptureFrameQuality(
                meanLuminance = 0f,
                luminanceDeviation = 0f,
                edgeStrength = 0f,
            )
        }

        var luminanceSum = 0.0
        var squaredLuminanceSum = 0.0
        var sampleCount = 0
        var edgeSum = 0.0
        var edgeCount = 0
        var centreSamples = 0
        var centreHighlights = 0
        val centreLeft = frame.width * CentreRegionMargin
        val centreRight = frame.width * (1f - CentreRegionMargin)
        val centreTop = frame.height * CentreRegionMargin
        val centreBottom = frame.height * (1f - CentreRegionMargin)

        var y = 0
        while (y < frame.height) {
            var previousLuminance: Float? = null
            var x = 0
            while (x < frame.width) {
                val luminance = frame.luminanceAt(x, y) ?: break
                luminanceSum += luminance
                squaredLuminanceSum += luminance * luminance
                sampleCount += 1
                if (x >= centreLeft && x < centreRight && y >= centreTop && y < centreBottom) {
                    centreSamples += 1
                    if (luminance >= HighlightLuminance) centreHighlights += 1
                }

                previousLuminance?.let { previous ->
                    edgeSum += abs(luminance - previous)
                    edgeCount += 1
                }
                if (y >= SampleStep) {
                    frame.luminanceAt(x, y - SampleStep)?.let { above ->
                        edgeSum += abs(luminance - above)
                        edgeCount += 1
                    }
                }

                previousLuminance = luminance
                x += SampleStep
            }
            y += SampleStep
        }

        if (sampleCount == 0) {
            return CaptureFrameQuality(0f, 0f, 0f)
        }

        val mean = luminanceSum / sampleCount
        val variance = ((squaredLuminanceSum / sampleCount) - (mean * mean)).coerceAtLeast(0.0)
        return CaptureFrameQuality(
            meanLuminance = mean.toFloat(),
            luminanceDeviation = sqrt(variance).toFloat(),
            edgeStrength = if (edgeCount == 0) 0f else (edgeSum / edgeCount).toFloat(),
            edgeSharpness = if (checkSharpness) edgeSharpness(frame) else CaptureFrameQuality.UNMEASURED_EDGE_SHARPNESS,
            centreHighlightFraction = if (centreSamples == 0) 0f else centreHighlights.toFloat() / centreSamples,
        )
    }

    /**
     * Samples every [SharpnessSampleStep]-th row (horizontal neighbours) and column (vertical
     * neighbours), always comparing adjacent pixels, into 256-bin histograms; no per-pixel
     * allocation or sorting.
     */
    internal fun edgeSharpness(frame: DetectionFrame): Float {
        val width = frame.width
        val rowBytes = width.toLong() * RgbaPixelStride
        if (width < 2 || rowBytes <= 0L) return CaptureFrameQuality.UNMEASURED_EDGE_SHARPNESS
        // Only rows fully present in the buffer are read, so short arrays are safe.
        val height = minOf(frame.height.toLong(), frame.bytes.size / rowBytes).toInt()
        if (height < 2) return CaptureFrameQuality.UNMEASURED_EDGE_SHARPNESS

        val bytes = frame.bytes
        val luminanceHistogram = IntArray(HistogramBins)
        val differenceHistogram = IntArray(HistogramBins)

        var y = 0
        while (y < height) {
            var previous = -1
            for (x in 0 until width) {
                val luminance = bytes.lumaAt(((y * width) + x) * RgbaPixelStride)
                luminanceHistogram[luminance] += 1
                if (previous >= 0) {
                    differenceHistogram[abs(luminance - previous)] += 1
                }
                previous = luminance
            }
            y += SharpnessSampleStep
        }
        var x = 0
        while (x < width) {
            var previous = bytes.lumaAt(x * RgbaPixelStride)
            for (row in 1 until height) {
                val luminance = bytes.lumaAt(((row * width) + x) * RgbaPixelStride)
                differenceHistogram[abs(luminance - previous)] += 1
                previous = luminance
            }
            x += SharpnessSampleStep
        }

        val luminanceCount = luminanceHistogram.sum()
        val differenceCount = differenceHistogram.sum()
        if (luminanceCount == 0 || differenceCount == 0) return CaptureFrameQuality.UNMEASURED_EDGE_SHARPNESS

        val low = luminanceHistogram.lowerPercentile(ContrastRangePercentile, luminanceCount)
        val high = luminanceHistogram.lowerPercentile(1f - ContrastRangePercentile, luminanceCount)
        val contrastRange = high - low
        if (contrastRange < MinContrastRangeForSharpness) return CaptureFrameQuality.UNMEASURED_EDGE_SHARPNESS

        val sharpEdgeSamples = maxOf(MinSharpEdgeSamples, (differenceCount * SharpEdgeFraction).toInt())
        val sharpEdge = differenceHistogram.topValue(sharpEdgeSamples)
        return (sharpEdge.toFloat() / contrastRange).coerceIn(0f, 1f)
    }

    /** Smallest bin value at or below which at least [fraction] of the samples fall. */
    private fun IntArray.lowerPercentile(fraction: Float, total: Int): Int {
        val target = maxOf(1, (total * fraction).toInt())
        var seen = 0
        for (bin in indices) {
            seen += this[bin]
            if (seen >= target) return bin
        }
        return lastIndex
    }

    /** Largest bin value with at least [samples] samples at or above it. */
    private fun IntArray.topValue(samples: Int): Int {
        var seen = 0
        for (bin in lastIndex downTo 0) {
            seen += this[bin]
            if (seen >= samples) return bin
        }
        return 0
    }

    /** Integer Rec. 709 luma (0..255) of the RGBA pixel starting at [index]. */
    private fun ByteArray.lumaAt(index: Int): Int {
        val red = this[index].toInt() and 0xFF
        val green = this[index + 1].toInt() and 0xFF
        val blue = this[index + 2].toInt() and 0xFF
        return ((54 * red) + (183 * green) + (19 * blue)) shr 8
    }

    private fun DetectionFrame.luminanceAt(x: Int, y: Int): Float? {
        val index = ((y * width) + x) * RgbaPixelStride
        if (index < 0 || index + 2 >= bytes.size) {
            return null
        }
        val red = bytes[index].toInt() and 0xFF
        val green = bytes[index + 1].toInt() and 0xFF
        val blue = bytes[index + 2].toInt() and 0xFF
        return (0.2126f * red) + (0.7152f * green) + (0.0722f * blue)
    }
}

private const val RgbaPixelStride = 4
private const val SampleStep = 8
private const val BlockedMeanLuminance = 20f
private const val BlockedLuminanceDeviation = 12f
private const val DarkMeanLuminance = 48f

// Glare: the central 60% x 60% of the frame (where the ID guide sits, so rotation doesn't matter)
// with at least 4% of its samples clipped. Matte white paper under room light stays below ~245.
private const val CentreRegionMargin = 0.2f
private const val HighlightLuminance = 252f
private const val GlareHighlightFraction = 0.04f

private const val HistogramBins = 256

// Every 8th row and column: ~76k adjacent-pixel comparisons on a 640x480 frame.
private const val SharpnessSampleStep = 8

// p2..p98 luminance: ignores specks and highlights but keeps a page covering a few % of the frame.
private const val ContrastRangePercentile = 0.02f

// Below ~16 grey levels the scene is too flat for blur to be judged (uniform frame, lens fog).
private const val MinContrastRangeForSharpness = 16

// "Sharpest edges" = the top 0.1% of differences, but never fewer than 8 samples, so one paper
// edge crossing a few dozen sampled rows/columns is enough regardless of how blank the page is.
private const val SharpEdgeFraction = 0.001f
private const val MinSharpEdgeSamples = 8

// Synthetic crisp edges measure ~1.0 and box-blurred (radius 4-6) ones ~0.07-0.11; real camera
// edges spread over 2-3 px (~0.3-0.5), so 0.2 leaves margin on both sides and errs towards sharp.
private const val BlurryEdgeSharpness = 0.2f
