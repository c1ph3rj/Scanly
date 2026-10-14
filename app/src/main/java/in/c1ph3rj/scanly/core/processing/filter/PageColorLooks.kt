package `in`.c1ph3rj.scanly.core.processing.filter

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Global color looks that do not use the paper model ([PageBackgroundModel]).
 *
 * Paper presets divide out a bright background; that is wrong for emissive displays
 * (dark mode, full-screen images) and for posters / photos, so these looks correct
 * the image as a whole instead:
 *
 * ```
 * Screen: de-screen (luma grid blur + quarter-res chroma blur) → dark-mode veil removal
 *         → bounded white balance + levels → S-curve → vibrance → sharpen
 * Vivid:  bounded white balance + levels → CLAHE on lightness → S-curve → vibrance → sharpen
 * ```
 *
 * All statistics are measured on a [STATS_EDGE] copy so chip, preview and save match.
 */
internal object PageColorLooks {
    private const val STATS_EDGE = 360
    private const val OUTPUT_WHITE = 248.0
    private const val DARK_MODE_MEDIAN = 100.0

    fun renderScreen(rgb: Mat, params: PageFilterRenderParams, outputRgb: Mat) {
        val working = Mat()
        try {
            descreen(rgb, working, params.descreenSigma, params.chromaSigma)
            if (params.veilRemoval > 0.0) {
                removeDarkModeVeil(working, params.veilRemoval)
            }
            finishGlobalLook(working, params, outputRgb, localContrast = 0.0)
        } finally {
            working.release()
        }
    }

    fun renderVivid(rgb: Mat, params: PageFilterRenderParams, outputRgb: Mat) {
        finishGlobalLook(rgb, params, outputRgb, localContrast = params.localContrast)
    }

    private fun finishGlobalLook(
        source: Mat,
        params: PageFilterRenderParams,
        outputRgb: Mat,
        localContrast: Double,
    ) {
        val stats = measure(source)
        val levels = levelsTables(stats, params.whiteBalance, params.blackClip)
        val channels = mutableListOf<Mat>()
        try {
            Core.split(source, channels)
            channels.forEachIndexed { index, channel ->
                PageFilterOperators.applyLut(channel, channel, levels[index])
            }
            Core.merge(channels, outputRgb)
        } finally {
            channels.forEach(Mat::release)
        }
        if (localContrast > 0.005) {
            applyLocalContrast(outputRgb, localContrast)
        }
        if (params.contrastCurve > 0.005) {
            PageFilterOperators.applyLut(outputRgb, outputRgb, sCurve(params.contrastCurve))
        }
        PageFilterOperators.vibrance(outputRgb, outputRgb, params.vibrance, neutralizePaper = false)
        PageFilterOperators.sharpen(outputRgb, outputRgb, params.sharpenAmount, params.sharpenSigma)
    }

    /**
     * Blurs luma just enough to dissolve the display's pixel grid and smooths chroma at
     * quarter resolution, where color moiré lives; text edges ride on luma and survive.
     */
    private fun descreen(rgb: Mat, output: Mat, lumaSigma: Double, chromaSigma: Double) {
        val ycc = Mat()
        val channels = mutableListOf<Mat>()
        val reduced = Mat()
        try {
            Imgproc.cvtColor(rgb, ycc, Imgproc.COLOR_RGB2YCrCb)
            Core.split(ycc, channels)
            if (lumaSigma > 0.0) {
                Imgproc.GaussianBlur(channels[0], channels[0], Size(0.0, 0.0), lumaSigma)
            }
            if (chromaSigma > 0.0) {
                val width = (rgb.cols() / 4).coerceAtLeast(1).toDouble()
                val height = (rgb.rows() / 4).coerceAtLeast(1).toDouble()
                for (index in 1..2) {
                    Imgproc.resize(channels[index], reduced, Size(width, height), 0.0, 0.0, Imgproc.INTER_AREA)
                    Imgproc.GaussianBlur(reduced, reduced, Size(0.0, 0.0), chromaSigma)
                    Imgproc.resize(reduced, channels[index], rgb.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
                }
            }
            Core.merge(channels, ycc)
            Imgproc.cvtColor(ycc, output, Imgproc.COLOR_YCrCb2RGB)
        } finally {
            ycc.release()
            channels.forEach(Mat::release)
            reduced.release()
        }
    }

    /**
     * Dark-background screens pick up an uneven, often tinted veil from room light
     * reflecting off the glass. The local minimum of each channel (over ~8% of the
     * page) is that veil; subtracting it — down to a near-black floor — removes the haze
     * and its color. Light-background screens are skipped: their local minimum is content.
     */
    private fun removeDarkModeVeil(rgb: Mat, strength: Double) {
        val small = Mat()
        val smallChannels = mutableListOf<Mat>()
        val fullChannels = mutableListOf<Mat>()
        try {
            resizeLongest(rgb, small, STATS_EDGE)
            val stats = measure(small)
            if (stats.medianLuminance >= DARK_MODE_MEDIAN) return

            val edge = max(small.cols(), small.rows())
            val kernelSize = toOddWithin((edge * 0.08).roundToInt(), 3, 61)
            val kernel = Imgproc.getStructuringElement(
                Imgproc.MORPH_ELLIPSE,
                Size(kernelSize.toDouble(), kernelSize.toDouble()),
            )
            small.convertTo(small, CvType.CV_32F)
            Core.split(small, smallChannels)
            smallChannels.forEach { channel ->
                Imgproc.erode(channel, channel, kernel)
                Imgproc.GaussianBlur(channel, channel, Size(0.0, 0.0), edge * 0.05)
            }
            kernel.release()

            val count = small.rows() * small.cols()
            val values = smallChannels.map { channel -> FloatArray(count).also { channel.get(0, 0, it) } }
            val hazeHistogram = IntArray(256)
            for (i in 0 until count) {
                val luminance = PageBackgroundModel.grayOf(
                    values[0][i].toDouble(),
                    values[1][i].toDouble(),
                    values[2][i].toDouble(),
                )
                hazeHistogram[luminance.roundToInt().coerceIn(0, 255)]++
            }
            val floor = percentileOf(hazeHistogram, count, 0.05) * 0.35

            Core.split(rgb, fullChannels)
            val veil = Mat()
            val channelFloat = Mat()
            try {
                fullChannels.forEachIndexed { index, channel ->
                    val smallVeil = smallChannels[index]
                    Core.subtract(smallVeil, Scalar.all(floor), smallVeil)
                    Core.max(smallVeil, Scalar.all(0.0), smallVeil)
                    Core.multiply(smallVeil, Scalar.all(strength), smallVeil)
                    Imgproc.resize(smallVeil, veil, rgb.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
                    channel.convertTo(channelFloat, CvType.CV_32F)
                    Core.subtract(channelFloat, veil, channelFloat)
                    channelFloat.convertTo(channel, CvType.CV_8U)
                }
                Core.merge(fullChannels, rgb)
            } finally {
                veil.release()
                channelFloat.release()
            }
        } finally {
            small.release()
            smallChannels.forEach(Mat::release)
            fullChannels.forEach(Mat::release)
        }
    }

    /** CLAHE on Lab lightness (fixed 8×8 grid, so the look does not depend on render size). */
    private fun applyLocalContrast(rgb: Mat, mix: Double) {
        val lab = Mat()
        val channels = mutableListOf<Mat>()
        val enhanced = Mat()
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        try {
            Imgproc.cvtColor(rgb, lab, Imgproc.COLOR_RGB2Lab)
            Core.split(lab, channels)
            clahe.apply(channels[0], enhanced)
            Core.addWeighted(channels[0], 1.0 - mix, enhanced, mix, 0.0, channels[0])
            Core.merge(channels, lab)
            Imgproc.cvtColor(lab, rgb, Imgproc.COLOR_Lab2RGB)
        } finally {
            lab.release()
            channels.forEach(Mat::release)
            enhanced.release()
            clahe.collectGarbage()
        }
    }

    internal data class GlobalColorStats(
        /** Robust display / paper white per channel (R, G, B). */
        val white: DoubleArray,
        val darkLuminance: Double,
        val medianLuminance: Double,
    )

    /**
     * White = 80th percentile of the brightest 1% low-saturation pixels (falls back to the
     * brightest 1% when the page has no neutral highlights); dark = 0.5th luminance percentile.
     */
    internal fun measure(rgb: Mat): GlobalColorStats {
        val small = Mat()
        try {
            resizeLongest(rgb, small, STATS_EDGE)
            val count = small.rows() * small.cols()
            val bytes = ByteArray(count * 3)
            small.get(0, 0, bytes)
            val luminance = IntArray(count)
            val saturated = BooleanArray(count)
            val lumHistogram = IntArray(256)
            for (i in 0 until count) {
                val r = bytes[i * 3].toInt() and 0xFF
                val g = bytes[i * 3 + 1].toInt() and 0xFF
                val b = bytes[i * 3 + 2].toInt() and 0xFF
                val l = PageBackgroundModel.grayOf(r.toDouble(), g.toDouble(), b.toDouble()).roundToInt()
                luminance[i] = l
                lumHistogram[l]++
                val maxChannel = maxOf(r, g, b)
                val minChannel = minOf(r, g, b)
                saturated[i] = maxChannel > 0 && (maxChannel - minChannel).toDouble() / maxChannel >= 0.25
            }
            val brightCut = percentileOf(lumHistogram, count, 0.99)
            val white = whiteReference(bytes, luminance, saturated, brightCut, requireNeutral = true)
                ?: whiteReference(bytes, luminance, saturated, brightCut, requireNeutral = false)
                ?: doubleArrayOf(255.0, 255.0, 255.0)
            return GlobalColorStats(
                white = white,
                darkLuminance = percentileOf(lumHistogram, count, 0.005),
                medianLuminance = percentileOf(lumHistogram, count, 0.5),
            )
        } finally {
            small.release()
        }
    }

    private fun whiteReference(
        bytes: ByteArray,
        luminance: IntArray,
        saturated: BooleanArray,
        brightCut: Double,
        requireNeutral: Boolean,
    ): DoubleArray? {
        val histograms = Array(3) { IntArray(256) }
        var selected = 0
        for (i in luminance.indices) {
            if (luminance[i] >= brightCut && (!requireNeutral || !saturated[i])) {
                for (channel in 0..2) histograms[channel][bytes[i * 3 + channel].toInt() and 0xFF]++
                selected++
            }
        }
        if (selected < MIN_WHITE_SAMPLES) return null
        return DoubleArray(3) { channel -> percentileOf(histograms[channel], selected, 0.80).coerceAtLeast(1.0) }
    }

    /**
     * Per-channel levels: the dark level × [blackClip] maps to 0 and the (partially
     * white-balanced) white reference maps to [OUTPUT_WHITE]. White-balance gains are
     * limited to ±15% so colorful content cannot be pushed into a false cast, and the
     * overall stretch is capped at [MAX_STRETCH].
     */
    internal fun levelsTables(
        stats: GlobalColorStats,
        whiteBalance: Double,
        blackClip: Double,
    ): List<ByteArray> {
        val neutral = stats.white.average()
        val black = stats.darkLuminance * blackClip.coerceIn(0.0, 1.0)
        return List(3) { channel ->
            val gain = (neutral / stats.white[channel]).coerceIn(0.85, 1.15)
            val balancedWhite = neutral / (1.0 + (gain - 1.0) * whiteBalance.coerceIn(0.0, 1.0))
            // Never stretch more than MAX_STRETCH: low-contrast or dim captures would
            // otherwise have their noise (and residual moiré) amplified several times.
            val minRange = max(MIN_LEVEL_RANGE, OUTPUT_WHITE / MAX_STRETCH)
            val channelBlack = minOf(black, max(balancedWhite - minRange, 0.0))
            val white = max(balancedWhite, channelBlack + minRange)
            ByteArray(256) { i ->
                ((i - channelBlack) / (white - channelBlack) * OUTPUT_WHITE).roundToInt().coerceIn(0, 255).toByte()
            }
        }
    }

    /** Mid-tone contrast that keeps 0 and 255 fixed: `x + k·(x − ½)·(1 − |2x − 1|)`. */
    internal fun sCurve(strength: Double): ByteArray = ByteArray(256) { i ->
        val x = i / 255.0
        val y = x + strength * (x - 0.5) * (1.0 - abs(2.0 * x - 1.0))
        (y * 255.0).roundToInt().coerceIn(0, 255).toByte()
    }

    private fun resizeLongest(source: Mat, output: Mat, edge: Int) {
        val longest = max(source.cols(), source.rows())
        if (longest <= edge) {
            source.copyTo(output)
            return
        }
        val scale = edge.toDouble() / longest
        Imgproc.resize(source, output, Size(), scale, scale, Imgproc.INTER_AREA)
    }

    private const val MIN_WHITE_SAMPLES = 30
    private const val MIN_LEVEL_RANGE = 40.0
    private const val MAX_STRETCH = 2.4
}
