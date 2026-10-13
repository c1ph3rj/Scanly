package `in`.c1ph3rj.scanly.core.processing.filter

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.pow
import kotlin.math.roundToInt

internal object PageFilterOperators {
    /**
     * Levels + gamma curve: [blackPoint] → 0, [whitePoint] and above → 255, with
     * `gamma > 1` darkening mid-tones (ink) while paper stays clipped white.
     */
    fun toneCurve(blackPoint: Double, whitePoint: Double, gamma: Double): ByteArray {
        val range = (whitePoint - blackPoint).coerceAtLeast(1.0)
        return ByteArray(256) { i ->
            val x = ((i - blackPoint) / range).coerceIn(0.0, 1.0)
            (x.pow(gamma) * 255.0).roundToInt().coerceIn(0, 255).toByte()
        }
    }

    /** Applies a 256-entry LUT to every channel of an 8-bit Mat. */
    fun applyLut(source: Mat, output: Mat, table: ByteArray) {
        val lut = Mat(1, 256, CvType.CV_8U)
        try {
            lut.put(0, 0, table)
            Core.LUT(source, lut, output)
        } finally {
            lut.release()
        }
    }

    /**
     * Vibrance on an RGB page: lifts muted colors (stamps, highlighter, pen) more than
     * already-saturated ones, then forces near-white low-chroma pixels to neutral so the
     * paper carries no tint.
     */
    fun vibrance(sourceRgb: Mat, outputRgb: Mat, amount: Double) {
        val hsv = Mat()
        val channels = mutableListOf<Mat>()
        val brightMask = Mat()
        val lowChromaMask = Mat()
        try {
            Imgproc.cvtColor(sourceRgb, hsv, Imgproc.COLOR_RGB2HSV)
            Core.split(hsv, channels)
            if (amount > 0.005) {
                val table = ByteArray(256) { i ->
                    val s = i.toDouble()
                    (s + amount * s * (1.0 - s / 255.0) * 1.2).roundToInt().coerceIn(0, 255).toByte()
                }
                applyLut(channels[1], channels[1], table)
            }
            Core.compare(channels[2], Scalar.all(PAPER_VALUE_MIN), brightMask, Core.CMP_GT)
            Core.compare(channels[1], Scalar.all(PAPER_SATURATION_MAX), lowChromaMask, Core.CMP_LT)
            Core.bitwise_and(brightMask, lowChromaMask, brightMask)
            channels[1].setTo(Scalar.all(0.0), brightMask)
            Core.merge(channels, hsv)
            Imgproc.cvtColor(hsv, outputRgb, Imgproc.COLOR_HSV2RGB)
        } finally {
            hsv.release()
            channels.forEach(Mat::release)
            brightMask.release()
            lowChromaMask.release()
        }
    }

    /** Unsharp mask: `src + amount × (src − blur)`. Bright overshoot clips into white paper. */
    fun sharpen(source: Mat, output: Mat, amount: Double, sigma: Double) {
        if (amount <= 0.01) {
            if (source !== output) source.copyTo(output)
            return
        }
        val blurred = Mat()
        try {
            Imgproc.GaussianBlur(source, blurred, Size(0.0, 0.0), sigma.coerceAtLeast(0.4))
            Core.addWeighted(source, 1.0 + amount, blurred, -amount, 0.0, output)
        } finally {
            blurred.release()
        }
    }

    /**
     * Global threshold on a background-normalized gray page with a short linear ramp, so
     * glyph edges stay anti-aliased instead of jagged 1-bit steps. Shading is already
     * removed, so a global threshold keeps bold strokes solid (no hollow letters).
     */
    fun binarize(sourceGray: Mat, outputGray: Mat, threshold: Double, ramp: Double) {
        val low = threshold - ramp
        val width = (2.0 * ramp).coerceAtLeast(1.0)
        val table = ByteArray(256) { i ->
            (((i - low) / width).coerceIn(0.0, 1.0) * 255.0).roundToInt().toByte()
        }
        applyLut(sourceGray, outputGray, table)
    }

    /** Whitens isolated ink components smaller than [minArea] pixels (sensor / paper specks). */
    fun despeckle(gray: Mat, minArea: Int) {
        if (minArea <= 1) return
        val inkMask = Mat()
        val labels = Mat()
        val stats = Mat()
        val centroids = Mat()
        try {
            Imgproc.threshold(gray, inkMask, 127.0, 1.0, Imgproc.THRESH_BINARY_INV)
            val count = Imgproc.connectedComponentsWithStats(inkMask, labels, stats, centroids, 8, CvType.CV_32S)
            if (count <= 1) return
            val statValues = IntArray(count * stats.cols())
            stats.get(0, 0, statValues)
            val small = BooleanArray(count)
            var anySmall = false
            for (label in 1 until count) {
                if (statValues[label * stats.cols() + Imgproc.CC_STAT_AREA] < minArea) {
                    small[label] = true
                    anySmall = true
                }
            }
            if (!anySmall) return
            val pixelCount = gray.rows() * gray.cols()
            val labelValues = IntArray(pixelCount)
            labels.get(0, 0, labelValues)
            val pixels = ByteArray(pixelCount)
            gray.get(0, 0, pixels)
            for (i in 0 until pixelCount) {
                if (small[labelValues[i]]) pixels[i] = 0xFF.toByte()
            }
            gray.put(0, 0, pixels)
        } finally {
            inkMask.release()
            labels.release()
            stats.release()
            centroids.release()
        }
    }

    private const val PAPER_VALUE_MIN = 232.0
    private const val PAPER_SATURATION_MAX = 40.0
}
