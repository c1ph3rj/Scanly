package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
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
        return when (filterPreset) {
            PageFilterPreset.ORIGINAL -> original(sourceBitmap)
            PageFilterPreset.ENHANCED_COLOR -> enhancedColor(sourceBitmap)
            PageFilterPreset.GRAYSCALE -> grayscale(sourceBitmap)
            PageFilterPreset.BLACK_AND_WHITE -> blackAndWhite(sourceBitmap)
            PageFilterPreset.CLEAN -> clean(sourceBitmap)
            PageFilterPreset.MAGIC_COLOR -> magicColor(sourceBitmap)
            PageFilterPreset.RECEIPT -> receipt(sourceBitmap)
            PageFilterPreset.SOFT_BLACK_AND_WHITE -> softBlackAndWhite(sourceBitmap)
        }
    }

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            check(OpenCVLoader.initLocal()) { "OpenCV could not be initialized." }
            initialized = true
        }
    }

    private fun original(sourceBitmap: Bitmap): Bitmap {
        val rgba = sourceBitmap.toMat()
        val bgr = Mat()
        val sharpened = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            sharpenColor(
                sourceBgr = bgr,
                outputBgr = sharpened,
                amount = 1.14,
                sigma = 1.0,
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

    private fun enhancedColor(sourceBitmap: Bitmap): Bitmap {
        val rgba = sourceBitmap.toMat()
        val bgr = Mat()
        val denoised = Mat()
        val enhancedBgr = Mat()
        val sharpened = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.bilateralFilter(bgr, denoised, 7, 40.0, 40.0)
            enhanceLabLightness(
                sourceBgr = denoised,
                outputBgr = enhancedBgr,
                clipLimit = 2.4,
            )
            sharpenColor(
                sourceBgr = enhancedBgr,
                outputBgr = sharpened,
                amount = 1.16,
                sigma = 1.1,
                bias = 2.0,
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

    private fun grayscale(sourceBitmap: Bitmap): Bitmap {
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
                clipLimit = 2.4,
            )
            Imgproc.bilateralFilter(claheGray, denoisedGray, 7, 35.0, 35.0)
            sharpenGray(
                sourceGray = denoisedGray,
                outputGray = sharpenedGray,
                amount = 1.18,
                sigma = 0.9,
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

    private fun blackAndWhite(sourceBitmap: Bitmap): Bitmap {
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
                backgroundKernelSize = 31,
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = 2.1,
            )
            Imgproc.bilateralFilter(claheGray, denoisedGray, 5, 30.0, 30.0)
            sauvolaThreshold(
                sourceGray = denoisedGray,
                outputBinary = sauvolaBinary,
                windowSize = 35,
                k = 0.2,
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

    private fun clean(sourceBitmap: Bitmap): Bitmap {
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
            Imgproc.medianBlur(gray, background, 21)
            Core.add(background, Scalar.all(1.0), safeBackground)
            Core.divide(gray, safeBackground, flattened, 255.0, gray.type())
            Core.normalize(flattened, normalizedGray, 0.0, 255.0, Core.NORM_MINMAX)
            applyClahe(
                sourceGray = normalizedGray,
                outputGray = claheGray,
                clipLimit = 2.2,
            )
            sharpenGray(
                sourceGray = claheGray,
                outputGray = sharpenedGray,
                amount = 1.16,
                sigma = 0.8,
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

    private fun magicColor(sourceBitmap: Bitmap): Bitmap {
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
                clipLimit = 2.8,
            )
            boostSaturation(
                sourceBgr = enhancedBgr,
                outputBgr = saturatedBgr,
                scale = 1.18,
                shift = 10.0,
            )
            sharpenColor(
                sourceBgr = saturatedBgr,
                outputBgr = sharpened,
                amount = 1.18,
                sigma = 1.0,
                bias = 4.0,
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

    private fun receipt(sourceBitmap: Bitmap): Bitmap {
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
                clipLimit = 3.2,
            )
            Imgproc.bilateralFilter(claheGray, denoisedGray, 9, 55.0, 55.0)
            Imgproc.adaptiveThreshold(
                denoisedGray,
                thresholded,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                21,
                7.0,
            )
            sharpenGray(
                sourceGray = thresholded,
                outputGray = sharpenedGray,
                amount = 1.12,
                sigma = 0.7,
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

    private fun softBlackAndWhite(sourceBitmap: Bitmap): Bitmap {
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
                backgroundKernelSize = 27,
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = 1.7,
            )
            Imgproc.bilateralFilter(claheGray, denoisedGray, 5, 24.0, 24.0)
            sauvolaThreshold(
                sourceGray = denoisedGray,
                outputBinary = binaryMask,
                windowSize = 33,
                k = 0.16,
            )
            Imgproc.GaussianBlur(binaryMask, softenedMask, Size(0.0, 0.0), 1.15)
            Core.max(denoisedGray, softenedMask, liftedWhites)
            sharpenGray(
                sourceGray = liftedWhites,
                outputGray = softenedGray,
                amount = 1.04,
                sigma = 0.8,
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

    private fun Mat.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bitmap)
        return bitmap
    }
}
