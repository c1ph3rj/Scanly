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
import kotlin.math.sqrt

/**
 * Low-resolution model of the paper's illumination (`Image ≈ Reflectance × Shading`).
 *
 * The shading is estimated at a fixed [BACKGROUND_EDGE] so the same page gets the same
 * background shape at chip, preview and save sizes:
 * 1. Morphological close removes ink strokes up to `strokeFraction` of the page.
 * 2. Pixels that do not look like paper (photos, logos, solid fills — strong chroma,
 *    texture or very dark) are masked out and filled from surrounding paper with
 *    multi-scale normalized convolution, so they are not "flattened" to white.
 *
 * Channels are RGB. Callers own the instance and must [release] it.
 */
internal class PageBackgroundModel private constructor(
    /** Per-channel (R, G, B) background at model size, CV_32F. */
    private val channels: List<Mat>,
    /** Paper reference color (R, G, B) — median of the brightest background quarter. */
    val paperReference: DoubleArray,
    /** Fraction of the model area that looked like paper. */
    val paperRatio: Double,
) {
    val paperReferenceGray: Double
        get() = grayOf(paperReference[0], paperReference[1], paperReference[2])

    fun channel(index: Int): Mat = channels[index]

    /** Gray background at model size (caller releases). */
    fun grayBackground(): Mat {
        val gray = Mat()
        Core.addWeighted(channels[0], GRAY_R, channels[1], GRAY_G, 0.0, gray)
        Core.scaleAdd(channels[2], GRAY_B, gray, gray)
        return gray
    }

    fun release() {
        channels.forEach(Mat::release)
    }

    companion object {
        internal const val BACKGROUND_EDGE = 360

        private const val GRAY_R = 0.299
        private const val GRAY_G = 0.587
        private const val GRAY_B = 0.114

        /** Paper pixels must be at least this bright relative to the paper reference. */
        private const val MIN_PAPER_LUMINANCE = 0.30
        /** Max L1 chromaticity distance from the paper color. */
        private const val MAX_PAPER_CHROMA = 0.06
        /** Max local std-dev of the (ink-free) background, relative to paper luminance. */
        private const val MAX_PAPER_TEXTURE = 0.06
        private const val MIN_FILL_CONFIDENCE = 0.15f

        fun grayOf(r: Double, g: Double, b: Double): Double = GRAY_R * r + GRAY_G * g + GRAY_B * b

        /**
         * @param sourceRgb 8-bit RGB page (any size).
         * @param strokeFraction widest ink stroke to remove, as a fraction of the page edge.
         */
        fun estimate(sourceRgb: Mat, strokeFraction: Double): PageBackgroundModel {
            val small = Mat()
            val closed = Mat()
            try {
                val longest = max(sourceRgb.cols(), sourceRgb.rows())
                if (longest > BACKGROUND_EDGE) {
                    val scale = BACKGROUND_EDGE.toDouble() / longest
                    Imgproc.resize(sourceRgb, small, Size(), scale, scale, Imgproc.INTER_AREA)
                } else {
                    sourceRgb.copyTo(small)
                }
                val edge = max(small.cols(), small.rows())
                val closeSize = toOddWithin((edge * strokeFraction).roundToInt(), 3, 99)
                val closeKernel = Imgproc.getStructuringElement(
                    Imgproc.MORPH_ELLIPSE,
                    Size(closeSize.toDouble(), closeSize.toDouble()),
                )
                Imgproc.morphologyEx(small, closed, Imgproc.MORPH_CLOSE, closeKernel)
                closeKernel.release()
                val medianSize = toOddWithin((edge * 0.025).roundToInt(), 3, 15)
                Imgproc.medianBlur(closed, closed, medianSize)
                return fromClosed(closed, edge)
            } finally {
                small.release()
                closed.release()
            }
        }

        private fun fromClosed(closed: Mat, edge: Int): PageBackgroundModel {
            val width = closed.cols()
            val height = closed.rows()
            val count = width * height
            val bytes = ByteArray(count * 3)
            closed.get(0, 0, bytes)

            val r = FloatArray(count)
            val g = FloatArray(count)
            val b = FloatArray(count)
            val lum = FloatArray(count)
            val lumHist = IntArray(256)
            for (i in 0 until count) {
                val rv = (bytes[i * 3].toInt() and 0xFF).toFloat()
                val gv = (bytes[i * 3 + 1].toInt() and 0xFF).toFloat()
                val bv = (bytes[i * 3 + 2].toInt() and 0xFF).toFloat()
                r[i] = rv
                g[i] = gv
                b[i] = bv
                val l = (GRAY_R * rv + GRAY_G * gv + GRAY_B * bv).toFloat()
                lum[i] = l
                lumHist[l.roundToInt().coerceIn(0, 255)]++
            }
            val paperLum = percentileOf(lumHist, count, 0.90).coerceAtLeast(1.0)
            val brightCut = percentileOf(lumHist, count, 0.75)

            val rHist = IntArray(256)
            val gHist = IntArray(256)
            val bHist = IntArray(256)
            var brightCount = 0
            for (i in 0 until count) {
                if (lum[i] >= brightCut) {
                    rHist[r[i].toInt()]++
                    gHist[g[i].toInt()]++
                    bHist[b[i].toInt()]++
                    brightCount++
                }
            }
            val reference = doubleArrayOf(
                percentileOf(rHist, brightCount, 0.5).coerceAtLeast(1.0),
                percentileOf(gHist, brightCount, 0.5).coerceAtLeast(1.0),
                percentileOf(bHist, brightCount, 0.5).coerceAtLeast(1.0),
            )
            val referenceSum = reference.sum()
            val pr = reference[0] / referenceSum
            val pg = reference[1] / referenceSum
            val pb = reference[2] / referenceSum

            val texture = localStdDev(lum, width, height)
            val mask = FloatArray(count)
            var paperCount = 0
            val minLum = MIN_PAPER_LUMINANCE * paperLum
            val maxTexture = MAX_PAPER_TEXTURE * paperLum
            for (i in 0 until count) {
                val sum = r[i] + g[i] + b[i] + 1f
                val chroma = abs(r[i] / sum - pr) + abs(g[i] / sum - pg) + abs(b[i] / sum - pb)
                if (lum[i] >= minLum && chroma < MAX_PAPER_CHROMA && texture[i] < maxTexture) {
                    mask[i] = 1f
                    paperCount++
                }
            }

            val weight = floatMat(mask, width, height)
            Imgproc.GaussianBlur(weight, weight, Size(0.0, 0.0), 1.5)
            val weights = FloatArray(count)
            weight.get(0, 0, weights)

            val fillSigmas = doubleArrayOf(edge * 0.03, edge * 0.08, edge * 0.2, edge * 0.5)
            val denominators = fillSigmas.map { sigma ->
                val den = Mat()
                blurLarge(weight, den, sigma)
                FloatArray(count).also { den.get(0, 0, it); den.release() }
            }

            val finalSigma = (edge * 0.008).coerceAtLeast(0.8)
            val channels = listOf(r, g, b).mapIndexed { index, values ->
                val weighted = FloatArray(count) { values[it] * weights[it] }
                val weightedMat = floatMat(weighted, width, height)
                val filled = FloatArray(count)
                val confidence = FloatArray(count)
                val numMat = Mat()
                try {
                    fillSigmas.forEachIndexed { level, sigma ->
                        blurLarge(weightedMat, numMat, sigma)
                        val num = FloatArray(count)
                        numMat.get(0, 0, num)
                        val den = denominators[level]
                        for (i in 0 until count) {
                            if (level == 0 || confidence[i] < MIN_FILL_CONFIDENCE) {
                                filled[i] = num[i] / max(den[i], 1e-4f)
                            }
                            confidence[i] = max(confidence[i], den[i])
                        }
                    }
                } finally {
                    weightedMat.release()
                    numMat.release()
                }
                val fallback = reference[index].toFloat()
                val blended = FloatArray(count) { i ->
                    val fill = if (confidence[i] < MIN_FILL_CONFIDENCE) fallback else filled[i]
                    weights[i] * values[i] + (1f - weights[i]) * fill
                }
                floatMat(blended, width, height).also {
                    Imgproc.GaussianBlur(it, it, Size(0.0, 0.0), finalSigma)
                }
            }
            weight.release()
            return PageBackgroundModel(
                channels = channels,
                paperReference = reference,
                paperRatio = paperCount.toDouble() / count.coerceAtLeast(1),
            )
        }

        /**
         * Normalizes one 8-bit channel: `out = src × target / background`, flooring the
         * background at `reference / maxGain`. Pixels lifted by more than 1.6× (shadows)
         * are progressively smoothed first so amplified sensor noise does not show.
         *
         * @param backgroundSmall model-size CV_32F background for this channel.
         * @param denoiseSigma blur used for shadow denoising; ≤ 0 disables it.
         */
        fun normalizeChannel(
            source: Mat,
            backgroundSmall: Mat,
            reference: Double,
            target: Double,
            maxGain: Double,
            denoiseSigma: Double,
            output: Mat,
        ) {
            val fullSize = source.size()
            val floored = Mat()
            val scaleSmall = Mat()
            val gainWeightSmall = Mat()
            val sourceFloat = Mat()
            val work = Mat()
            val work2 = Mat()
            try {
                val floor = max(8.0, reference / maxGain)
                Core.max(backgroundSmall, Scalar.all(floor), floored)
                Core.divide(target, floored, scaleSmall)
                source.convertTo(sourceFloat, CvType.CV_32F)

                if (denoiseSigma > 0.0) {
                    // weight = clamp((reference / background - 1.6) / 1.4, 0, 1)
                    Core.divide(reference, floored, gainWeightSmall)
                    gainWeightSmall.convertTo(gainWeightSmall, -1, 1.0 / 1.4, -1.6 / 1.4)
                    Core.min(gainWeightSmall, Scalar.all(1.0), gainWeightSmall)
                    Core.max(gainWeightSmall, Scalar.all(0.0), gainWeightSmall)
                    if (Core.minMaxLoc(gainWeightSmall).maxVal > 0.01) {
                        Imgproc.GaussianBlur(source, work, Size(0.0, 0.0), denoiseSigma)
                        work.convertTo(work, CvType.CV_32F)
                        Core.subtract(work, sourceFloat, work)
                        Imgproc.resize(gainWeightSmall, work2, fullSize, 0.0, 0.0, Imgproc.INTER_LINEAR)
                        Core.multiply(work, work2, work)
                        Core.add(sourceFloat, work, sourceFloat)
                    }
                }

                Imgproc.resize(scaleSmall, work2, fullSize, 0.0, 0.0, Imgproc.INTER_LINEAR)
                Core.multiply(sourceFloat, work2, sourceFloat)
                sourceFloat.convertTo(output, CvType.CV_8U)
            } finally {
                floored.release()
                scaleSmall.release()
                gainWeightSmall.release()
                sourceFloat.release()
                work.release()
                work2.release()
            }
        }

        /** Gaussian blur that stays cheap for sigmas comparable to the image size. */
        private fun blurLarge(source: Mat, output: Mat, sigma: Double) {
            if (sigma <= LARGE_SIGMA) {
                Imgproc.GaussianBlur(source, output, Size(0.0, 0.0), sigma.coerceAtLeast(0.5))
                return
            }
            val factor = sigma / SMALL_SIGMA
            val reduced = Mat()
            try {
                val width = (source.cols() / factor).roundToInt().coerceAtLeast(1)
                val height = (source.rows() / factor).roundToInt().coerceAtLeast(1)
                Imgproc.resize(source, reduced, Size(width.toDouble(), height.toDouble()), 0.0, 0.0, Imgproc.INTER_AREA)
                Imgproc.GaussianBlur(reduced, reduced, Size(0.0, 0.0), SMALL_SIGMA)
                Imgproc.resize(reduced, output, source.size(), 0.0, 0.0, Imgproc.INTER_LINEAR)
            } finally {
                reduced.release()
            }
        }

        private fun localStdDev(values: FloatArray, width: Int, height: Int): FloatArray {
            val source = floatMat(values, width, height)
            val squares = Mat()
            val mean = Mat()
            val meanSquares = Mat()
            try {
                Core.multiply(source, source, squares)
                Imgproc.blur(source, mean, Size(7.0, 7.0))
                Imgproc.blur(squares, meanSquares, Size(7.0, 7.0))
                val m = FloatArray(values.size)
                val m2 = FloatArray(values.size)
                mean.get(0, 0, m)
                meanSquares.get(0, 0, m2)
                return FloatArray(values.size) { i -> sqrt(max(m2[i] - m[i] * m[i], 0f)) }
            } finally {
                source.release()
                squares.release()
                mean.release()
                meanSquares.release()
            }
        }

        private fun floatMat(values: FloatArray, width: Int, height: Int): Mat =
            Mat(height, width, CvType.CV_32F).also { it.put(0, 0, values) }

        private const val LARGE_SIGMA = 8.0
        private const val SMALL_SIGMA = 4.0
    }
}

/** Value at [fraction] (0..1) of a 256-bin histogram holding [total] samples. */
internal fun percentileOf(histogram: IntArray, total: Int, fraction: Double): Double {
    if (total <= 0) return 0.0
    val target = (fraction.coerceIn(0.0, 1.0) * (total - 1)).toLong()
    var cumulative = 0L
    for (bin in histogram.indices) {
        cumulative += histogram[bin]
        if (cumulative > target) return bin.toDouble()
    }
    return (histogram.size - 1).toDouble()
}
