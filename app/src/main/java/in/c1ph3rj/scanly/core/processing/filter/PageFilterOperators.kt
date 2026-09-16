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
    fun flattenIllumination(
        sourceGray: Mat,
        outputGray: Mat,
        backgroundBlurSigma: Double,
        strength: Double,
        targetBackground: Double,
        protectMask: Mat? = null,
        protectMix: Double = 0.75,
    ) {
        val resolvedStrength = strength.coerceIn(0.0, 1.0)
        if (resolvedStrength <= 0.01) {
            sourceGray.copyTo(outputGray)
            return
        }

        val textSuppressed = Mat()
        val background = Mat()
        val sourceFloat = Mat()
        val backgroundFloat = Mat()
        val safeBackgroundFloat = Mat()
        val flattenedFloat = Mat()
        val flattenedGray = Mat()
        val blended = Mat()
        val protectedBlend = Mat()
        val textKernelSize = toOddWithin(
            value = maxOf(sourceGray.rows(), sourceGray.cols()) / 220,
            min = 5,
            max = 13,
        )
        val textRemovalKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(textKernelSize.toDouble(), textKernelSize.toDouble()),
        )

        try {
            Imgproc.morphologyEx(sourceGray, textSuppressed, Imgproc.MORPH_CLOSE, textRemovalKernel)
            Imgproc.GaussianBlur(
                textSuppressed,
                background,
                Size(0.0, 0.0),
                backgroundBlurSigma.coerceAtLeast(1.0),
            )
            sourceGray.convertTo(sourceFloat, CvType.CV_32F)
            background.convertTo(backgroundFloat, CvType.CV_32F)
            Core.add(backgroundFloat, Scalar.all(1.0), safeBackgroundFloat)
            Core.divide(sourceFloat, safeBackgroundFloat, flattenedFloat, targetBackground)
            flattenedFloat.convertTo(flattenedGray, CvType.CV_8U)
            Core.addWeighted(
                sourceGray,
                1.0 - resolvedStrength,
                flattenedGray,
                resolvedStrength,
                0.0,
                blended,
            )
            if (protectMask == null || Core.countNonZero(protectMask) == 0) {
                blended.copyTo(outputGray)
            } else {
                val protectedStrength = (resolvedStrength * (1.0 - protectMix)).coerceIn(0.0, 1.0)
                Core.addWeighted(
                    sourceGray,
                    1.0 - protectedStrength,
                    flattenedGray,
                    protectedStrength,
                    0.0,
                    protectedBlend,
                )
                blended.copyTo(outputGray)
                protectedBlend.copyTo(outputGray, protectMask)
            }
        } finally {
            textSuppressed.release()
            background.release()
            sourceFloat.release()
            backgroundFloat.release()
            safeBackgroundFloat.release()
            flattenedFloat.release()
            flattenedGray.release()
            blended.release()
            protectedBlend.release()
            textRemovalKernel.release()
        }
    }

    fun applyClahe(
        sourceGray: Mat,
        outputGray: Mat,
        clipLimit: Double,
        tileGridSize: Int,
        strength: Double,
    ) {
        val resolvedStrength = strength.coerceIn(0.0, 1.0)
        if (resolvedStrength <= 0.01) {
            sourceGray.copyTo(outputGray)
            return
        }

        val resolvedTileGridSize = tileGridSize.coerceIn(4, 12).toDouble()
        val clahe = Imgproc.createCLAHE(clipLimit, Size(resolvedTileGridSize, resolvedTileGridSize))
        val enhancedGray = Mat()
        try {
            clahe.apply(sourceGray, enhancedGray)
            Core.addWeighted(
                sourceGray,
                1.0 - resolvedStrength,
                enhancedGray,
                resolvedStrength,
                0.0,
                outputGray,
            )
        } finally {
            enhancedGray.release()
            clahe.collectGarbage()
        }
    }

    fun applySoftTone(
        sourceGray: Mat,
        outputGray: Mat,
        lift: Double,
        highlightRolloff: Double,
    ) {
        if (lift <= 0.15 && highlightRolloff <= 0.05) {
            sourceGray.copyTo(outputGray)
            return
        }

        val lut = Mat(1, 256, CvType.CV_8U)
        val data = ByteArray(256)
        for (i in 0..255) {
            val x = i / 255.0
            val lifted = i + lift * (1.0 - x).pow(1.6)
            val highlight = ((lifted - 220.0).coerceAtLeast(0.0) / 35.0).coerceIn(0.0, 1.0)
            val out = lifted - (highlight * highlightRolloff * 18.0)
            data[i] = out.roundToInt().coerceIn(0, 255).toByte()
        }
        try {
            lut.put(0, 0, data)
            Core.LUT(sourceGray, lut, outputGray)
        } finally {
            lut.release()
        }
    }

    fun neutralizePaperCast(
        lightness: Mat,
        channelA: Mat,
        channelB: Mat,
        strength: Double,
    ) {
        if (strength <= 0.0) return

        val aDistance = Mat()
        val bDistance = Mat()
        val chromaDistance = Mat()
        val lightMask = Mat()
        val neutralMask = Mat()
        val paperMask = Mat()
        val adjustedA = Mat()
        val adjustedB = Mat()
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
                return
            }

            val paperA = Core.mean(channelA, paperMask).`val`[0]
            val paperB = Core.mean(channelB, paperMask).`val`[0]
            val resolvedStrength = strength.coerceIn(0.0, 1.0)
            val aShift = ((LAB_NEUTRAL - paperA) * resolvedStrength)
                .coerceIn(-MAX_WHITE_BALANCE_SHIFT, MAX_WHITE_BALANCE_SHIFT)
            val bShift = ((LAB_NEUTRAL - paperB) * resolvedStrength)
                .coerceIn(-MAX_WHITE_BALANCE_SHIFT, MAX_WHITE_BALANCE_SHIFT)
            channelA.convertTo(adjustedA, -1, 1.0, aShift)
            channelB.convertTo(adjustedB, -1, 1.0, bShift)
            adjustedA.copyTo(channelA)
            adjustedB.copyTo(channelB)
        } finally {
            aDistance.release()
            bDistance.release()
            chromaDistance.release()
            lightMask.release()
            neutralMask.release()
            paperMask.release()
            adjustedA.release()
            adjustedB.release()
        }
    }

    fun boostSaturation(
        sourceBgr: Mat,
        outputBgr: Mat,
        scale: Double,
    ) {
        if (kotlin.math.abs(scale - 1.0) <= 0.005) {
            sourceBgr.copyTo(outputBgr)
            return
        }

        val hsv = Mat()
        val mergedHsv = Mat()
        try {
            Imgproc.cvtColor(sourceBgr, hsv, Imgproc.COLOR_BGR2HSV)
            val hsvChannels = mutableListOf<Mat>()
            try {
                Core.split(hsv, hsvChannels)
                hsvChannels[1].convertTo(hsvChannels[1], -1, scale, 0.0)
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

    fun buildContentProtectMask(sourceBgr: Mat, outputMask: Mat) {
        val hsv = Mat()
        val hsvChannels = mutableListOf<Mat>()
        try {
            Imgproc.cvtColor(sourceBgr, hsv, Imgproc.COLOR_BGR2HSV)
            Core.split(hsv, hsvChannels)
            val saturation = hsvChannels.getOrNull(1) ?: error("Missing saturation channel.")
            Core.compare(saturation, Scalar.all(COLOR_SATURATION_THRESHOLD), outputMask, Core.CMP_GT)
        } finally {
            hsv.release()
            hsvChannels.forEach(Mat::release)
        }
    }

    fun restoreTextDetails(
        referenceGray: Mat,
        cleanedGray: Mat,
        outputGray: Mat,
        sensitivity: Double = TEXT_DETAIL_SENSITIVITY,
    ) {
        val textMask = Mat()
        try {
            createTextMask(referenceGray, textMask, sensitivity)
            cleanedGray.copyTo(outputGray)
            referenceGray.copyTo(outputGray, textMask)
        } finally {
            textMask.release()
        }
    }

    fun sharpenGray(
        sourceGray: Mat,
        outputGray: Mat,
        amount: Double,
        sigma: Double,
        textOnly: Boolean,
    ) {
        if (amount <= 1.01) {
            sourceGray.copyTo(outputGray)
            return
        }

        val blurred = Mat()
        val sharpened = Mat()
        val textMask = Mat()
        try {
            Imgproc.GaussianBlur(sourceGray, blurred, Size(0.0, 0.0), sigma.coerceAtLeast(0.4))
            Core.addWeighted(sourceGray, amount, blurred, 1.0 - amount, 0.0, sharpened)
            if (!textOnly) {
                sharpened.copyTo(outputGray)
            } else {
                createTextMask(sourceGray, textMask, TEXT_DETAIL_SENSITIVITY)
                sourceGray.copyTo(outputGray)
                sharpened.copyTo(outputGray, textMask)
            }
        } finally {
            blurred.release()
            sharpened.release()
            textMask.release()
        }
    }

    fun sharpenColor(
        sourceBgr: Mat,
        outputBgr: Mat,
        amount: Double,
        sigma: Double,
        textOnly: Boolean,
    ) {
        if (amount <= 1.01) {
            sourceBgr.copyTo(outputBgr)
            return
        }

        val blurred = Mat()
        val sharpened = Mat()
        val gray = Mat()
        val textMask = Mat()
        try {
            Imgproc.GaussianBlur(sourceBgr, blurred, Size(0.0, 0.0), sigma.coerceAtLeast(0.4))
            Core.addWeighted(sourceBgr, amount, blurred, 1.0 - amount, 0.0, sharpened)
            if (!textOnly) {
                sharpened.copyTo(outputBgr)
            } else {
                Imgproc.cvtColor(sourceBgr, gray, Imgproc.COLOR_BGR2GRAY)
                createTextMask(gray, textMask, TEXT_DETAIL_SENSITIVITY)
                sourceBgr.copyTo(outputBgr)
                sharpened.copyTo(outputBgr, textMask)
            }
        } finally {
            blurred.release()
            sharpened.release()
            gray.release()
            textMask.release()
        }
    }

    fun maybeBilateral(
        source: Mat,
        output: Mat,
        diameter: Int,
        sigmaColor: Double,
        sigmaSpace: Double,
    ) {
        if (diameter < 3) {
            source.copyTo(output)
            return
        }
        Imgproc.bilateralFilter(source, output, diameter, sigmaColor, sigmaSpace)
    }

    fun createTextMask(
        sourceGray: Mat,
        outputMask: Mat,
        sensitivity: Double,
    ) {
        val localBackground = Mat()
        val textResponse = Mat()
        val kernelSize = toOddWithin(
            value = maxOf(sourceGray.rows(), sourceGray.cols()) / 220,
            min = 5,
            max = 13,
        )
        val backgroundKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(kernelSize.toDouble(), kernelSize.toDouble()),
        )
        val dilationKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))
        try {
            Imgproc.morphologyEx(sourceGray, localBackground, Imgproc.MORPH_CLOSE, backgroundKernel)
            Core.subtract(localBackground, sourceGray, textResponse)
            Imgproc.threshold(textResponse, outputMask, sensitivity, 255.0, Imgproc.THRESH_BINARY)
            Imgproc.dilate(outputMask, outputMask, dilationKernel)
        } finally {
            localBackground.release()
            textResponse.release()
            backgroundKernel.release()
            dilationKernel.release()
        }
    }

    fun textResponseMean(sourceGray: Mat): Double {
        val localBackground = Mat()
        val textResponse = Mat()
        val kernelSize = toOddWithin(
            value = maxOf(sourceGray.rows(), sourceGray.cols()) / 220,
            min = 5,
            max = 13,
        )
        val backgroundKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(kernelSize.toDouble(), kernelSize.toDouble()),
        )
        try {
            Imgproc.morphologyEx(sourceGray, localBackground, Imgproc.MORPH_CLOSE, backgroundKernel)
            Core.subtract(localBackground, sourceGray, textResponse)
            return Core.mean(textResponse).`val`[0]
        } finally {
            localBackground.release()
            textResponse.release()
            backgroundKernel.release()
        }
    }

    fun applyOutputGuard(
        sourceRgba: Mat,
        resultRgba: Mat,
        recipe: PageFilterRecipe,
        params: PageFilterAppliedParams,
    ) {
        if (recipe.colorMode == PageFilterColorMode.HardBinary) {
            return
        }

        val sourceGray = Mat()
        val resultGray = Mat()
        val paperMask = Mat()
        val textMask = Mat()
        val pulled = Mat()
        val boosted = Mat()
        try {
            Imgproc.cvtColor(sourceRgba, sourceGray, Imgproc.COLOR_RGBA2GRAY)
            Imgproc.cvtColor(resultRgba, resultGray, Imgproc.COLOR_RGBA2GRAY)
            // Only Color recipes may mix the capture back in. Gray / soft-binary
            // output is GRAY2RGBA; blending sourceRgba would reintroduce chroma.
            val canPullPaperTowardCapture = recipe.colorMode == PageFilterColorMode.Color
            Core.compare(resultGray, Scalar.all(PAPER_LIGHTNESS_THRESHOLD), paperMask, Core.CMP_GT)
            val paperPixels = Core.countNonZero(paperMask)
            if (canPullPaperTowardCapture && paperPixels > 0) {
                val paperMean = Core.mean(resultGray, paperMask).`val`[0]
                if (paperMean > params.paperTarget + GUARD_PAPER_SLACK) {
                    val pull = ((paperMean - params.paperTarget - GUARD_PAPER_SLACK) / 28.0)
                        .coerceIn(0.0, 0.40)
                    Core.addWeighted(resultRgba, 1.0 - pull, sourceRgba, pull, 0.0, pulled)
                    pulled.copyTo(resultRgba)
                    Imgproc.cvtColor(resultRgba, resultGray, Imgproc.COLOR_RGBA2GRAY)
                }
            }

            val sourceText = textResponseMean(sourceGray)
            val resultText = textResponseMean(resultGray)
            if (sourceText > 2.0 && resultText < sourceText * 0.92) {
                createTextMask(resultGray, textMask, TEXT_DETAIL_SENSITIVITY)
                resultRgba.convertTo(boosted, -1, 1.06, -6.0)
                boosted.copyTo(resultRgba, textMask)
            }
        } finally {
            sourceGray.release()
            resultGray.release()
            paperMask.release()
            textMask.release()
            pulled.release()
            boosted.release()
        }
    }

    private const val COLOR_SATURATION_THRESHOLD = 40.0
    private const val TEXT_DETAIL_SENSITIVITY = 10.0
    private const val LAB_NEUTRAL = 128.0
    private const val PAPER_LIGHTNESS_THRESHOLD = 150.0
    private const val PAPER_CHROMA_DISTANCE_THRESHOLD = 42.0
    private const val MIN_PAPER_MASK_RATIO = 0.08
    private const val MAX_WHITE_BALANCE_SHIFT = 18.0
    private const val GUARD_PAPER_SLACK = 6.0
}
