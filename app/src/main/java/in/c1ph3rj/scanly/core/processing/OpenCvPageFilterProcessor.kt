package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object OpenCvPageFilterProcessor {

    @Volatile
    private var initialized = false

    fun apply(
        sourceBitmap: Bitmap,
        filterPreset: PageFilterPreset,
    ): Bitmap {
        ensureInitialized()
        val profile = runCatching { analyze(sourceBitmap) }.getOrNull()
        return renderWithFallback(
            sourceBitmap = sourceBitmap,
            filterPreset = filterPreset,
            profile = profile,
        )
    }

    internal fun apply(
        sourceBitmap: Bitmap,
        filterPreset: PageFilterPreset,
        profile: PageImageProfile,
    ): Bitmap {
        ensureInitialized()
        return renderWithFallback(
            sourceBitmap = sourceBitmap,
            filterPreset = filterPreset,
            profile = profile,
        )
    }

    internal fun analyze(sourceBitmap: Bitmap): PageImageProfile {
        ensureInitialized()
        val analysisBitmap = sourceBitmap.toMatForAnalysis()
        val bgr = Mat()
        val gray = Mat()
        val hsv = Mat()
        val edges = Mat()
        val laplacian = Mat()
        val shadowMask = Mat()
        val highlightMask = Mat()
        val hsvChannels = mutableListOf<Mat>()

        try {
            Imgproc.cvtColor(analysisBitmap, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
            Core.split(hsv, hsvChannels)

            val luminanceMean = MatOfDouble()
            val luminanceStdDev = MatOfDouble()
            Core.meanStdDev(gray, luminanceMean, luminanceStdDev)

            val saturation = hsvChannels.getOrNull(1) ?: error("Could not analyze image saturation.")
            val saturationMean = Core.mean(saturation).`val`[0]

            val pixelCount = (gray.rows().toLong() * gray.cols().toLong()).toDouble()
            if (pixelCount <= 0.0) {
                error("Could not analyze empty image.")
            }

            Core.compare(gray, Scalar.all(SHADOW_THRESHOLD), shadowMask, Core.CMP_LT)
            Core.compare(gray, Scalar.all(HIGHLIGHT_THRESHOLD), highlightMask, Core.CMP_GT)
            Imgproc.Canny(gray, edges, CANNY_LOW_THRESHOLD, CANNY_HIGH_THRESHOLD)
            Imgproc.Laplacian(gray, laplacian, CvType.CV_64F)

            val laplacianStdDev = MatOfDouble()
            Core.meanStdDev(laplacian, MatOfDouble(), laplacianStdDev)

            return PageImageProfile(
                brightness = luminanceMean.toArray().firstOrNull() ?: 0.0,
                contrast = luminanceStdDev.toArray().firstOrNull() ?: 0.0,
                shadowRatio = Core.countNonZero(shadowMask).toDouble() / pixelCount,
                highlightRatio = Core.countNonZero(highlightMask).toDouble() / pixelCount,
                saturation = saturationMean,
                edgeDensity = Core.countNonZero(edges).toDouble() / pixelCount,
                sharpness = laplacianStdDev.toArray().firstOrNull() ?: 0.0,
                longestEdge = maxOf(sourceBitmap.width, sourceBitmap.height),
            )
        } finally {
            analysisBitmap.release()
            bgr.release()
            gray.release()
            hsv.release()
            edges.release()
            laplacian.release()
            shadowMask.release()
            highlightMask.release()
            hsvChannels.forEach(Mat::release)
        }
    }

    private fun render(
        sourceBitmap: Bitmap,
        filterPreset: PageFilterPreset,
        profile: PageImageProfile?,
    ): Bitmap = when (filterPreset) {
        PageFilterPreset.ORIGINAL -> original(sourceBitmap, profile)
        PageFilterPreset.ENHANCED_COLOR -> enhancedColor(sourceBitmap, profile)
        PageFilterPreset.GRAYSCALE -> grayscale(sourceBitmap, profile)
        PageFilterPreset.BLACK_AND_WHITE -> blackAndWhite(sourceBitmap, profile)
        PageFilterPreset.CLEAN -> clean(sourceBitmap, profile)
        PageFilterPreset.MAGIC_COLOR -> magicColor(sourceBitmap, profile)
        PageFilterPreset.RECEIPT -> receipt(sourceBitmap, profile)
        PageFilterPreset.SOFT_BLACK_AND_WHITE -> softBlackAndWhite(sourceBitmap, profile)
    }

    private fun renderWithFallback(
        sourceBitmap: Bitmap,
        filterPreset: PageFilterPreset,
        profile: PageImageProfile?,
    ): Bitmap {
        val adaptiveAttempt = runCatching {
            render(sourceBitmap, filterPreset, profile)
        }
        if (adaptiveAttempt.isSuccess) {
            return adaptiveAttempt.getOrThrow()
        }

        if (profile != null) {
            return runCatching {
                render(sourceBitmap, filterPreset, null)
            }.getOrElse { adaptiveAttempt.getOrThrow() }
        }

        return adaptiveAttempt.getOrThrow()
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            check(OpenCVLoader.initLocal()) { "OpenCV could not be initialized." }
            initialized = true
        }
    }

    private fun original(
        sourceBitmap: Bitmap,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.original(profile)
        val rgba = sourceBitmap.toMat()
        val bgr = Mat()
        val sharpened = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            sharpenColor(
                sourceBgr = bgr,
                outputBgr = sharpened,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_BGR2RGBA)
            return resultRgba.toBitmap()
        } finally {
            rgba.release()
            bgr.release()
            sharpened.release()
            resultRgba.release()
        }
    }

    private fun enhancedColor(
        sourceBitmap: Bitmap,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.enhancedColor(profile)
        val rgba = sourceBitmap.toMat()
        val bgr = Mat()
        val denoised = Mat()
        val enhancedBgr = Mat()
        val sharpened = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.bilateralFilter(
                bgr,
                denoised,
                tuning.bilateralDiameter,
                tuning.bilateralSigmaColor,
                tuning.bilateralSigmaSpace,
            )
            enhanceLabLightness(
                sourceBgr = denoised,
                outputBgr = enhancedBgr,
                clipLimit = tuning.clipLimit,
            )
            sharpenColor(
                sourceBgr = enhancedBgr,
                outputBgr = sharpened,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
                bias = tuning.sharpenBias,
            )
            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_BGR2RGBA)
            return resultRgba.toBitmap()
        } finally {
            rgba.release()
            bgr.release()
            denoised.release()
            enhancedBgr.release()
            sharpened.release()
            resultRgba.release()
        }
    }

    private fun grayscale(
        sourceBitmap: Bitmap,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.grayscale(profile)
        val rgba = sourceBitmap.toMat()
        val gray = Mat()
        val claheGray = Mat()
        val denoisedGray = Mat()
        val sharpenedGray = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            applyClahe(
                sourceGray = gray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
            )
            Imgproc.bilateralFilter(
                claheGray,
                denoisedGray,
                tuning.bilateralDiameter,
                tuning.bilateralSigmaColor,
                tuning.bilateralSigmaSpace,
            )
            sharpenGray(
                sourceGray = denoisedGray,
                outputGray = sharpenedGray,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(sharpenedGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            rgba.release()
            gray.release()
            claheGray.release()
            denoisedGray.release()
            sharpenedGray.release()
            resultRgba.release()
        }
    }

    private fun blackAndWhite(
        sourceBitmap: Bitmap,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.blackAndWhite(profile)
        val rgba = sourceBitmap.toMat()
        val gray = Mat()
        val flattenedGray = Mat()
        val claheGray = Mat()
        val denoisedGray = Mat()
        val sauvolaBinary = Mat()
        val closed = Mat()
        val finalBinary = Mat()
        val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            flattenIllumination(
                sourceGray = gray,
                outputGray = flattenedGray,
                backgroundKernelSize = tuning.backgroundKernelSize,
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
            )
            Imgproc.bilateralFilter(
                claheGray,
                denoisedGray,
                tuning.denoiseDiameter,
                tuning.denoiseSigmaColor,
                tuning.denoiseSigmaSpace,
            )
            sauvolaThreshold(
                sourceGray = denoisedGray,
                outputBinary = sauvolaBinary,
                windowSize = tuning.windowSize,
                k = tuning.k,
            )
            Imgproc.morphologyEx(
                sauvolaBinary,
                closed,
                Imgproc.MORPH_CLOSE,
                kernel,
            )
            Imgproc.medianBlur(closed, finalBinary, 3)
            Imgproc.threshold(
                finalBinary,
                closed,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU,
            )
            Imgproc.cvtColor(closed, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            rgba.release()
            gray.release()
            flattenedGray.release()
            claheGray.release()
            denoisedGray.release()
            sauvolaBinary.release()
            closed.release()
            finalBinary.release()
            kernel.release()
            resultRgba.release()
        }
    }

    private fun clean(
        sourceBitmap: Bitmap,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.clean(profile)
        val rgba = sourceBitmap.toMat()
        val gray = Mat()
        val background = Mat()
        val safeBackground = Mat()
        val flattened = Mat()
        val normalizedGray = Mat()
        val claheGray = Mat()
        val sharpenedGray = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.medianBlur(gray, background, tuning.backgroundKernelSize)
            Core.add(background, Scalar.all(1.0), safeBackground)
            Core.divide(gray, safeBackground, flattened, 255.0, gray.type())
            Core.normalize(flattened, normalizedGray, 0.0, 255.0, Core.NORM_MINMAX)
            applyClahe(
                sourceGray = normalizedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
            )
            sharpenGray(
                sourceGray = claheGray,
                outputGray = sharpenedGray,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(sharpenedGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            rgba.release()
            gray.release()
            background.release()
            safeBackground.release()
            flattened.release()
            normalizedGray.release()
            claheGray.release()
            sharpenedGray.release()
            resultRgba.release()
        }
    }

    private fun magicColor(
        sourceBitmap: Bitmap,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.magicColor(profile)
        val rgba = sourceBitmap.toMat()
        val bgr = Mat()
        val enhancedBgr = Mat()
        val saturatedBgr = Mat()
        val sharpened = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            enhanceLabLightness(
                sourceBgr = bgr,
                outputBgr = enhancedBgr,
                clipLimit = tuning.clipLimit,
            )
            boostSaturation(
                sourceBgr = enhancedBgr,
                outputBgr = saturatedBgr,
                scale = tuning.saturationScale,
                shift = tuning.saturationShift,
            )
            sharpenColor(
                sourceBgr = saturatedBgr,
                outputBgr = sharpened,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
                bias = tuning.sharpenBias,
            )
            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_BGR2RGBA)
            return resultRgba.toBitmap()
        } finally {
            rgba.release()
            bgr.release()
            enhancedBgr.release()
            saturatedBgr.release()
            sharpened.release()
            resultRgba.release()
        }
    }

    private fun receipt(
        sourceBitmap: Bitmap,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.receipt(profile)
        val rgba = sourceBitmap.toMat()
        val gray = Mat()
        val claheGray = Mat()
        val denoisedGray = Mat()
        val thresholded = Mat()
        val sharpenedGray = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            applyClahe(
                sourceGray = gray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
            )
            Imgproc.bilateralFilter(
                claheGray,
                denoisedGray,
                tuning.bilateralDiameter,
                tuning.bilateralSigmaColor,
                tuning.bilateralSigmaSpace,
            )
            Imgproc.adaptiveThreshold(
                denoisedGray,
                thresholded,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                tuning.blockSize,
                tuning.c,
            )
            sharpenGray(
                sourceGray = thresholded,
                outputGray = sharpenedGray,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.threshold(
                sharpenedGray,
                sharpenedGray,
                0.0,
                255.0,
                Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU,
            )
            Imgproc.cvtColor(sharpenedGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            rgba.release()
            gray.release()
            claheGray.release()
            denoisedGray.release()
            thresholded.release()
            sharpenedGray.release()
            resultRgba.release()
        }
    }

    private fun softBlackAndWhite(
        sourceBitmap: Bitmap,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.softBlackAndWhite(profile)
        val rgba = sourceBitmap.toMat()
        val gray = Mat()
        val flattenedGray = Mat()
        val claheGray = Mat()
        val denoisedGray = Mat()
        val binaryMask = Mat()
        val softenedMask = Mat()
        val liftedWhites = Mat()
        val softenedGray = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            flattenIllumination(
                sourceGray = gray,
                outputGray = flattenedGray,
                backgroundKernelSize = tuning.backgroundKernelSize,
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
            )
            Imgproc.bilateralFilter(
                claheGray,
                denoisedGray,
                tuning.denoiseDiameter,
                tuning.denoiseSigmaColor,
                tuning.denoiseSigmaSpace,
            )
            sauvolaThreshold(
                sourceGray = denoisedGray,
                outputBinary = binaryMask,
                windowSize = tuning.windowSize,
                k = tuning.k,
            )
            Imgproc.GaussianBlur(binaryMask, softenedMask, Size(0.0, 0.0), tuning.blurSigma)
            Core.max(denoisedGray, softenedMask, liftedWhites)
            sharpenGray(
                sourceGray = liftedWhites,
                outputGray = softenedGray,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(softenedGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            rgba.release()
            gray.release()
            flattenedGray.release()
            claheGray.release()
            denoisedGray.release()
            binaryMask.release()
            softenedMask.release()
            liftedWhites.release()
            softenedGray.release()
            resultRgba.release()
        }
    }

    private fun flattenIllumination(
        sourceGray: Mat,
        outputGray: Mat,
        backgroundKernelSize: Int,
    ) {
        val background = Mat()
        val sourceFloat = Mat()
        val backgroundFloat = Mat()
        val safeBackgroundFloat = Mat()
        val flattenedFloat = Mat()

        try {
            val kernelSize = backgroundKernelSize.let { if (it % 2 == 0) it + 1 else it }
            Imgproc.medianBlur(sourceGray, background, kernelSize)
            sourceGray.convertTo(sourceFloat, CvType.CV_32F)
            background.convertTo(backgroundFloat, CvType.CV_32F)
            Core.add(backgroundFloat, Scalar.all(1.0), safeBackgroundFloat)
            Core.divide(sourceFloat, safeBackgroundFloat, flattenedFloat, 255.0)
            Core.normalize(flattenedFloat, outputGray, 0.0, 255.0, Core.NORM_MINMAX, CvType.CV_8U)
        } finally {
            background.release()
            sourceFloat.release()
            backgroundFloat.release()
            safeBackgroundFloat.release()
            flattenedFloat.release()
        }
    }

    private fun sauvolaThreshold(
        sourceGray: Mat,
        outputBinary: Mat,
        windowSize: Int,
        k: Double,
        dynamicRange: Double = 128.0,
    ) {
        val grayFloat = Mat()
        val mean = Mat()
        val squared = Mat()
        val meanOfSquares = Mat()
        val meanSquared = Mat()
        val variance = Mat()
        val stdDev = Mat()
        val ratio = Mat()
        val thresholdFactor = Mat()
        val threshold = Mat()

        try {
            val actualWindow = windowSize.let { if (it % 2 == 0) it + 1 else it }
            sourceGray.convertTo(grayFloat, CvType.CV_32F)
            Imgproc.blur(grayFloat, mean, Size(actualWindow.toDouble(), actualWindow.toDouble()))
            Core.multiply(grayFloat, grayFloat, squared)
            Imgproc.blur(squared, meanOfSquares, Size(actualWindow.toDouble(), actualWindow.toDouble()))
            Core.multiply(mean, mean, meanSquared)
            Core.subtract(meanOfSquares, meanSquared, variance)
            Core.max(variance, Scalar.all(0.0), variance)
            Core.sqrt(variance, stdDev)
            stdDev.convertTo(ratio, CvType.CV_32F, 1.0 / dynamicRange)
            Core.subtract(ratio, Scalar.all(1.0), thresholdFactor)
            Core.multiply(thresholdFactor, Scalar.all(k), thresholdFactor)
            Core.add(thresholdFactor, Scalar.all(1.0), thresholdFactor)
            Core.multiply(mean, thresholdFactor, threshold)
            Core.compare(grayFloat, threshold, outputBinary, Core.CMP_GT)
        } finally {
            grayFloat.release()
            mean.release()
            squared.release()
            meanOfSquares.release()
            meanSquared.release()
            variance.release()
            stdDev.release()
            ratio.release()
            thresholdFactor.release()
            threshold.release()
        }
    }

    private fun applyClahe(
        sourceGray: Mat,
        outputGray: Mat,
        clipLimit: Double,
    ) {
        val clahe = Imgproc.createCLAHE(clipLimit, Size(8.0, 8.0))
        clahe.apply(sourceGray, outputGray)
    }

    private fun enhanceLabLightness(
        sourceBgr: Mat,
        outputBgr: Mat,
        clipLimit: Double,
    ) {
        val lab = Mat()
        val mergedLab = Mat()

        try {
            Imgproc.cvtColor(sourceBgr, lab, Imgproc.COLOR_BGR2Lab)
            val labChannels = mutableListOf<Mat>()
            try {
                Core.split(lab, labChannels)
                applyClahe(
                    sourceGray = labChannels[0],
                    outputGray = labChannels[0],
                    clipLimit = clipLimit,
                )
                Core.merge(labChannels, mergedLab)
            } finally {
                labChannels.forEach(Mat::release)
            }
            Imgproc.cvtColor(mergedLab, outputBgr, Imgproc.COLOR_Lab2BGR)
        } finally {
            lab.release()
            mergedLab.release()
        }
    }

    private fun boostSaturation(
        sourceBgr: Mat,
        outputBgr: Mat,
        scale: Double,
        shift: Double,
    ) {
        val hsv = Mat()
        val mergedHsv = Mat()

        try {
            Imgproc.cvtColor(sourceBgr, hsv, Imgproc.COLOR_BGR2HSV)
            val hsvChannels = mutableListOf<Mat>()
            try {
                Core.split(hsv, hsvChannels)
                hsvChannels[1].convertTo(hsvChannels[1], -1, scale, shift)
                Core.merge(hsvChannels, mergedHsv)
            } finally {
                hsvChannels.forEach(Mat::release)
            }
            Imgproc.cvtColor(mergedHsv, outputBgr, Imgproc.COLOR_HSV2BGR)
        } finally {
            hsv.release()
            mergedHsv.release()
        }
    }

    private fun sharpenColor(
        sourceBgr: Mat,
        outputBgr: Mat,
        amount: Double,
        sigma: Double,
        bias: Double = 0.0,
    ) {
        val blurred = Mat()
        try {
            Imgproc.GaussianBlur(sourceBgr, blurred, Size(0.0, 0.0), sigma)
            Core.addWeighted(sourceBgr, amount, blurred, 1.0 - amount, bias, outputBgr)
        } finally {
            blurred.release()
        }
    }

    private fun sharpenGray(
        sourceGray: Mat,
        outputGray: Mat,
        amount: Double,
        sigma: Double,
        bias: Double = 0.0,
    ) {
        val blurred = Mat()
        try {
            Imgproc.GaussianBlur(sourceGray, blurred, Size(0.0, 0.0), sigma)
            Core.addWeighted(sourceGray, amount, blurred, 1.0 - amount, bias, outputGray)
        } finally {
            blurred.release()
        }
    }

    private fun Bitmap.toMat(): Mat {
        val mat = Mat(height, width, CvType.CV_8UC4)
        Utils.bitmapToMat(this, mat)
        return mat
    }

    private fun Bitmap.toMatForAnalysis(): Mat {
        val rgba = toMat()
        val longestEdge = maxOf(width, height)
        if (longestEdge <= ANALYSIS_MAX_DIMENSION) {
            return rgba
        }

        val scale = ANALYSIS_MAX_DIMENSION / longestEdge.toDouble()
        val resized = Mat()
        Imgproc.resize(rgba, resized, Size(), scale, scale, Imgproc.INTER_AREA)
        rgba.release()
        return resized
    }

    private fun Mat.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bitmap)
        return bitmap
    }

    private const val ANALYSIS_MAX_DIMENSION = 720
    private const val SHADOW_THRESHOLD = 76.0
    private const val HIGHLIGHT_THRESHOLD = 220.0
    private const val CANNY_LOW_THRESHOLD = 40.0
    private const val CANNY_HIGH_THRESHOLD = 120.0
}
