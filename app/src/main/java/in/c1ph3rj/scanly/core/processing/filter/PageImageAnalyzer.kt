package `in`.c1ph3rj.scanly.core.processing.filter

import `in`.c1ph3rj.scanly.core.processing.PageImageProfile
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Builds a [PageImageProfile] from a page at a canonical analysis size (max 720px).
 */
internal object PageImageAnalyzer {
    internal const val ANALYSIS_MAX_DIMENSION = 720

    fun analyze(
        sourceRgba: Mat,
        sourceAspectRatio: Double,
    ): PageImageProfile {
        val analysisRgba = sourceRgba.forAnalysis()
        val ownsAnalysisRgba = analysisRgba !== sourceRgba
        val bgr = Mat()
        val gray = Mat()
        val hsv = Mat()
        val lab = Mat()
        val edges = Mat()
        val laplacian = Mat()
        val backgroundSeed = Mat()
        val background = Mat()
        val shadowMask = Mat()
        val highlightMask = Mat()
        val glareMask = Mat()
        val textMask = Mat()
        val colorMask = Mat()
        val residual = Mat()
        val blurredGray = Mat()
        val hsvChannels = mutableListOf<Mat>()
        val labChannels = mutableListOf<Mat>()
        val backgroundKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        val luminanceMean = MatOfDouble()
        val luminanceStdDev = MatOfDouble()
        val backgroundMean = MatOfDouble()
        val backgroundStdDev = MatOfDouble()
        val laplacianMean = MatOfDouble()
        val laplacianStdDev = MatOfDouble()
        val residualMean = MatOfDouble()
        val residualStdDev = MatOfDouble()

        try {
            Imgproc.cvtColor(analysisRgba, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
            Imgproc.cvtColor(bgr, lab, Imgproc.COLOR_BGR2Lab)
            Core.split(hsv, hsvChannels)
            Core.split(lab, labChannels)

            Core.meanStdDev(gray, luminanceMean, luminanceStdDev)

            val saturation = hsvChannels.getOrNull(1) ?: error("Could not analyze image saturation.")
            val saturationMean = Core.mean(saturation).`val`[0]
            val lightness = labChannels.getOrNull(0) ?: error("Could not analyze paper lightness.")
            val channelA = labChannels.getOrNull(1) ?: error("Could not analyze paper chroma.")
            val channelB = labChannels.getOrNull(2) ?: error("Could not analyze paper chroma.")

            val pixelCount = (gray.rows().toLong() * gray.cols().toLong()).toDouble()
            if (pixelCount <= 0.0) {
                error("Could not analyze empty image.")
            }

            Imgproc.morphologyEx(gray, backgroundSeed, Imgproc.MORPH_CLOSE, backgroundKernel)
            val backgroundSigma = (maxOf(gray.rows(), gray.cols()) / 28.0).coerceIn(10.0, 26.0)
            Imgproc.GaussianBlur(backgroundSeed, background, Size(0.0, 0.0), backgroundSigma)
            Core.meanStdDev(background, backgroundMean, backgroundStdDev)

            val resolvedBackgroundMean = backgroundMean.toArray().firstOrNull() ?: 0.0
            val resolvedBackgroundStdDev = backgroundStdDev.toArray().firstOrNull() ?: 0.0
            val shadowCutoff = resolvedBackgroundMean - maxOf(18.0, resolvedBackgroundStdDev * 0.85)
            Core.compare(background, Scalar.all(shadowCutoff), shadowMask, Core.CMP_LT)
            Core.compare(gray, Scalar.all(HIGHLIGHT_THRESHOLD), highlightMask, Core.CMP_GT)
            Core.compare(gray, Scalar.all(GLARE_THRESHOLD), glareMask, Core.CMP_GT)

            val textBlockSize = oddKernel(
                value = maxOf(gray.rows(), gray.cols()) / 18,
                min = 21,
                max = 51,
            )
            Imgproc.adaptiveThreshold(
                gray,
                textMask,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY_INV,
                textBlockSize,
                TEXT_ANALYSIS_C,
            )
            Core.compare(saturation, Scalar.all(COLOR_SATURATION_THRESHOLD), colorMask, Core.CMP_GT)
            Imgproc.Canny(gray, edges, CANNY_LOW_THRESHOLD, CANNY_HIGH_THRESHOLD)
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)
            Core.meanStdDev(laplacian, laplacianMean, laplacianStdDev)

            Imgproc.GaussianBlur(gray, blurredGray, Size(0.0, 0.0), 1.5)
            Core.absdiff(gray, blurredGray, residual)
            Core.meanStdDev(residual, residualMean, residualStdDev)

            val paperStats = estimatePaperLab(
                lightness = lightness,
                channelA = channelA,
                channelB = channelB,
                fallbackL = resolvedBackgroundMean,
            )

            return PageImageProfile(
                brightness = luminanceMean.toArray().firstOrNull() ?: 0.0,
                contrast = luminanceStdDev.toArray().firstOrNull() ?: 0.0,
                shadowRatio = Core.countNonZero(shadowMask).toDouble() / pixelCount,
                highlightRatio = Core.countNonZero(highlightMask).toDouble() / pixelCount,
                saturation = saturationMean,
                edgeDensity = Core.countNonZero(edges).toDouble() / pixelCount,
                sharpness = laplacianStdDev.toArray().firstOrNull() ?: 0.0,
                longestEdge = maxOf(sourceRgba.rows(), sourceRgba.cols()),
                backgroundUnevenness = resolvedBackgroundStdDev,
                textDensity = Core.countNonZero(textMask).toDouble() / pixelCount,
                colorRatio = Core.countNonZero(colorMask).toDouble() / pixelCount,
                aspectRatio = sourceAspectRatio,
                paperL = paperStats.l,
                paperA = paperStats.a,
                paperB = paperStats.b,
                glareRatio = Core.countNonZero(glareMask).toDouble() / pixelCount,
                noise = residualStdDev.toArray().firstOrNull() ?: 0.0,
            )
        } finally {
            if (ownsAnalysisRgba) {
                analysisRgba.release()
            }
            bgr.release()
            gray.release()
            hsv.release()
            lab.release()
            edges.release()
            laplacian.release()
            backgroundSeed.release()
            background.release()
            shadowMask.release()
            highlightMask.release()
            glareMask.release()
            textMask.release()
            colorMask.release()
            residual.release()
            blurredGray.release()
            hsvChannels.forEach(Mat::release)
            labChannels.forEach(Mat::release)
            backgroundKernel.release()
            luminanceMean.release()
            luminanceStdDev.release()
            backgroundMean.release()
            backgroundStdDev.release()
            laplacianMean.release()
            laplacianStdDev.release()
            residualMean.release()
            residualStdDev.release()
        }
    }

    private fun estimatePaperLab(
        lightness: Mat,
        channelA: Mat,
        channelB: Mat,
        fallbackL: Double,
    ): PaperLab {
        val aDistance = Mat()
        val bDistance = Mat()
        val chromaDistance = Mat()
        val lightMask = Mat()
        val neutralMask = Mat()
        val paperMask = Mat()
        try {
            Core.absdiff(channelA, Scalar.all(LAB_NEUTRAL), aDistance)
            Core.absdiff(channelB, Scalar.all(LAB_NEUTRAL), bDistance)
            Core.add(aDistance, bDistance, chromaDistance)
            Core.compare(lightness, Scalar.all(PAPER_LIGHTNESS_THRESHOLD), lightMask, Core.CMP_GT)
            Core.compare(
                chromaDistance,
                Scalar.all(PAPER_CHROMA_DISTANCE_THRESHOLD),
                neutralMask,
                Core.CMP_LT,
            )
            Core.bitwise_and(lightMask, neutralMask, paperMask)

            val pixelCount = lightness.rows().toLong() * lightness.cols().toLong()
            val paperPixelCount = Core.countNonZero(paperMask).toLong()
            if (pixelCount <= 0L || paperPixelCount < (pixelCount * MIN_PAPER_MASK_RATIO).toLong()) {
                return PaperLab(l = fallbackL, a = LAB_NEUTRAL, b = LAB_NEUTRAL)
            }

            return PaperLab(
                l = Core.mean(lightness, paperMask).`val`[0],
                a = Core.mean(channelA, paperMask).`val`[0],
                b = Core.mean(channelB, paperMask).`val`[0],
            )
        } finally {
            aDistance.release()
            bDistance.release()
            chromaDistance.release()
            lightMask.release()
            neutralMask.release()
            paperMask.release()
        }
    }

    private fun Mat.forAnalysis(): Mat {
        val longestEdge = maxOf(cols(), rows())
        if (longestEdge <= ANALYSIS_MAX_DIMENSION) {
            return this
        }

        val scale = ANALYSIS_MAX_DIMENSION / longestEdge.toDouble()
        val resized = Mat()
        Imgproc.resize(this, resized, Size(), scale, scale, Imgproc.INTER_AREA)
        return resized
    }

    private fun oddKernel(value: Int, min: Int, max: Int): Int {
        var candidate = value.coerceIn(min, max)
        if (candidate % 2 == 0) {
            candidate = if (candidate >= max) candidate - 1 else candidate + 1
        }
        return candidate.coerceAtLeast(3)
    }

    private data class PaperLab(
        val l: Double,
        val a: Double,
        val b: Double,
    )

    private const val HIGHLIGHT_THRESHOLD = 220.0
    private const val GLARE_THRESHOLD = 235.0
    private const val COLOR_SATURATION_THRESHOLD = 40.0
    private const val TEXT_ANALYSIS_C = 10.0
    private const val CANNY_LOW_THRESHOLD = 40.0
    private const val CANNY_HIGH_THRESHOLD = 120.0
    private const val LAB_NEUTRAL = 128.0
    private const val PAPER_LIGHTNESS_THRESHOLD = 150.0
    private const val PAPER_CHROMA_DISTANCE_THRESHOLD = 42.0
    private const val MIN_PAPER_MASK_RATIO = 0.08
}
