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
        if (filterPreset == PageFilterPreset.ORIGINAL) {
            return sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }

        val sourceRgba = sourceBitmap.toMat()
        return try {
            val profile = runCatching {
                analyze(
                    sourceRgba = sourceRgba,
                    sourceLongestEdge = maxOf(sourceBitmap.width, sourceBitmap.height),
                    sourceAspectRatio = aspectRatio(sourceBitmap.width, sourceBitmap.height),
                )
            }.getOrNull()
            renderWithFallback(
                sourceRgba = sourceRgba,
                filterPreset = filterPreset,
                profile = profile,
            )
        } finally {
            sourceRgba.release()
        }
    }

    internal fun applyAll(
        sourceBitmap: Bitmap,
        filterPresets: List<PageFilterPreset> = PageFilterPreset.entries,
    ): Map<PageFilterPreset, Bitmap> {
        ensureInitialized()
        val sourceRgba = sourceBitmap.toMat()
        return try {
            val profile = runCatching {
                analyze(
                    sourceRgba = sourceRgba,
                    sourceLongestEdge = maxOf(sourceBitmap.width, sourceBitmap.height),
                    sourceAspectRatio = aspectRatio(sourceBitmap.width, sourceBitmap.height),
                )
            }.getOrNull()
            filterPresets.associateWith { filterPreset ->
                runCatching {
                    renderWithFallback(
                        sourceRgba = sourceRgba,
                        filterPreset = filterPreset,
                        profile = profile,
                    )
                }.getOrElse {
                    sourceRgba.toBitmap()
                }
            }
        } finally {
            sourceRgba.release()
        }
    }

    private fun analyze(
        sourceRgba: Mat,
        sourceLongestEdge: Int,
        sourceAspectRatio: Double,
    ): PageImageProfile {
        val analysisRgba = sourceRgba.forAnalysis()
        val ownsAnalysisRgba = analysisRgba !== sourceRgba
        val bgr = Mat()
        val gray = Mat()
        val hsv = Mat()
        val edges = Mat()
        val laplacian = Mat()
        val backgroundSeed = Mat()
        val background = Mat()
        val shadowMask = Mat()
        val highlightMask = Mat()
        val textMask = Mat()
        val colorMask = Mat()
        val hsvChannels = mutableListOf<Mat>()
        val backgroundKernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(7.0, 7.0))
        val luminanceMean = MatOfDouble()
        val luminanceStdDev = MatOfDouble()
        val backgroundMean = MatOfDouble()
        val backgroundStdDev = MatOfDouble()
        val laplacianMean = MatOfDouble()
        val laplacianStdDev = MatOfDouble()

        try {
            Imgproc.cvtColor(analysisRgba, bgr, Imgproc.COLOR_RGBA2BGR)
            Imgproc.cvtColor(bgr, gray, Imgproc.COLOR_BGR2GRAY)
            Imgproc.cvtColor(bgr, hsv, Imgproc.COLOR_BGR2HSV)
            Core.split(hsv, hsvChannels)

            Core.meanStdDev(gray, luminanceMean, luminanceStdDev)

            val saturation = hsvChannels.getOrNull(1) ?: error("Could not analyze image saturation.")
            val saturationMean = Core.mean(saturation).`val`[0]

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

            return PageImageProfile(
                brightness = luminanceMean.toArray().firstOrNull() ?: 0.0,
                contrast = luminanceStdDev.toArray().firstOrNull() ?: 0.0,
                shadowRatio = Core.countNonZero(shadowMask).toDouble() / pixelCount,
                highlightRatio = Core.countNonZero(highlightMask).toDouble() / pixelCount,
                saturation = saturationMean,
                edgeDensity = Core.countNonZero(edges).toDouble() / pixelCount,
                sharpness = laplacianStdDev.toArray().firstOrNull() ?: 0.0,
                longestEdge = sourceLongestEdge,
                backgroundUnevenness = resolvedBackgroundStdDev,
                textDensity = Core.countNonZero(textMask).toDouble() / pixelCount,
                colorRatio = Core.countNonZero(colorMask).toDouble() / pixelCount,
                aspectRatio = sourceAspectRatio,
            )
        } finally {
            if (ownsAnalysisRgba) {
                analysisRgba.release()
            }
            bgr.release()
            gray.release()
            hsv.release()
            edges.release()
            laplacian.release()
            backgroundSeed.release()
            background.release()
            shadowMask.release()
            highlightMask.release()
            textMask.release()
            colorMask.release()
            hsvChannels.forEach(Mat::release)
            backgroundKernel.release()
            luminanceMean.release()
            luminanceStdDev.release()
            backgroundMean.release()
            backgroundStdDev.release()
            laplacianMean.release()
            laplacianStdDev.release()
        }
    }

    private fun render(
        sourceRgba: Mat,
        filterPreset: PageFilterPreset,
        profile: PageImageProfile?,
    ): Bitmap {
        val resolvedPreset = if (filterPreset == PageFilterPreset.AUTO) {
            AdaptivePageFilterTuning.automatic(profile)
        } else {
            filterPreset
        }
        return when (resolvedPreset) {
            PageFilterPreset.ORIGINAL -> sourceRgba.toBitmap()
            PageFilterPreset.AUTO -> grayscale(sourceRgba, profile)
            PageFilterPreset.ENHANCED_COLOR -> enhancedColor(sourceRgba, profile)
            PageFilterPreset.GRAYSCALE -> grayscale(sourceRgba, profile)
            PageFilterPreset.BLACK_AND_WHITE -> blackAndWhite(sourceRgba, profile)
            PageFilterPreset.CLEAN -> clean(sourceRgba, profile)
            PageFilterPreset.SHADOW_REDUCTION -> shadowReduction(sourceRgba, profile)
            PageFilterPreset.MAGIC_COLOR -> magicColor(sourceRgba, profile)
            PageFilterPreset.RECEIPT -> receipt(sourceRgba, profile)
            PageFilterPreset.SOFT_BLACK_AND_WHITE -> softBlackAndWhite(sourceRgba, profile)
        }
    }

    private fun renderWithFallback(
        sourceRgba: Mat,
        filterPreset: PageFilterPreset,
        profile: PageImageProfile?,
    ): Bitmap {
        val adaptiveAttempt = runCatching {
            render(sourceRgba, filterPreset, profile)
        }
        if (adaptiveAttempt.isSuccess) {
            return adaptiveAttempt.getOrThrow()
        }

        if (profile != null) {
            return runCatching {
                render(sourceRgba, filterPreset, null)
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

    private fun enhancedColor(
        sourceRgba: Mat,
        profile: PageImageProfile?,
    ): Bitmap = naturalColor(
        sourceRgba = sourceRgba,
        tuning = AdaptivePageFilterTuning.enhancedColor(profile),
    )

    private fun shadowReduction(
        sourceRgba: Mat,
        profile: PageImageProfile?,
    ): Bitmap = naturalColor(
        sourceRgba = sourceRgba,
        tuning = AdaptivePageFilterTuning.shadowReduction(profile),
    )

    private fun naturalColor(
        sourceRgba: Mat,
        tuning: AdaptivePageFilterTuning.EnhancedColorTuning,
    ): Bitmap {
        val bgr = Mat()
        val denoised = Mat()
        val enhancedBgr = Mat()
        val saturatedBgr = Mat()
        val sharpened = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(sourceRgba, bgr, Imgproc.COLOR_RGBA2BGR)
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
                tileGridSize = tuning.tileGridSize,
                backgroundBlurSigma = tuning.backgroundBlurSigma,
                shadowStrength = tuning.shadowStrength,
                backgroundTarget = tuning.backgroundTarget,
                textMaskSensitivity = tuning.textMaskSensitivity,
                contrastScale = tuning.contrastScale,
                brightnessShift = tuning.brightnessShift,
            )
            boostSaturation(
                sourceBgr = enhancedBgr,
                outputBgr = saturatedBgr,
                scale = tuning.saturationScale,
            )
            sharpenColor(
                sourceBgr = saturatedBgr,
                outputBgr = sharpened,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_BGR2RGBA)
            return resultRgba.toBitmap()
        } finally {
            bgr.release()
            denoised.release()
            enhancedBgr.release()
            saturatedBgr.release()
            sharpened.release()
            resultRgba.release()
        }
    }

    private fun grayscale(
        sourceRgba: Mat,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.grayscale(profile)
        val gray = Mat()
        val flattenedGray = Mat()
        val claheGray = Mat()
        val denoisedGray = Mat()
        val tonedGray = Mat()
        val sharpenedGray = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(sourceRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            flattenIllumination(
                sourceGray = gray,
                outputGray = flattenedGray,
                backgroundBlurSigma = tuning.backgroundBlurSigma,
                strength = tuning.shadowStrength,
                targetBackground = tuning.backgroundTarget,
                textMaskSensitivity = tuning.textMaskSensitivity,
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
            )
            Imgproc.bilateralFilter(
                claheGray,
                denoisedGray,
                tuning.bilateralDiameter,
                tuning.bilateralSigmaColor,
                tuning.bilateralSigmaSpace,
            )
            adjustTone(
                source = denoisedGray,
                output = tonedGray,
                contrastScale = tuning.contrastScale,
                brightnessShift = tuning.brightnessShift,
            )
            sharpenGray(
                sourceGray = tonedGray,
                outputGray = sharpenedGray,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(sharpenedGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            gray.release()
            flattenedGray.release()
            claheGray.release()
            denoisedGray.release()
            tonedGray.release()
            sharpenedGray.release()
            resultRgba.release()
        }
    }

    private fun blackAndWhite(
        sourceRgba: Mat,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.blackAndWhite(profile)
        val gray = Mat()
        val flattenedGray = Mat()
        val claheGray = Mat()
        val denoisedGray = Mat()
        val finalBinary = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(sourceRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            flattenIllumination(
                sourceGray = gray,
                outputGray = flattenedGray,
                backgroundBlurSigma = tuning.backgroundBlurSigma,
                strength = tuning.shadowStrength,
                targetBackground = tuning.backgroundTarget,
                textMaskSensitivity = tuning.textMaskSensitivity,
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
            )
            Imgproc.bilateralFilter(
                claheGray,
                denoisedGray,
                tuning.denoiseDiameter,
                tuning.denoiseSigmaColor,
                tuning.denoiseSigmaSpace,
            )
            Imgproc.adaptiveThreshold(
                denoisedGray,
                finalBinary,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                tuning.blockSize,
                tuning.c,
            )
            Imgproc.cvtColor(finalBinary, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            gray.release()
            flattenedGray.release()
            claheGray.release()
            denoisedGray.release()
            finalBinary.release()
            resultRgba.release()
        }
    }

    private fun clean(
        sourceRgba: Mat,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.clean(profile)
        val gray = Mat()
        val flattenedGray = Mat()
        val claheGray = Mat()
        val tonedGray = Mat()
        val textProtectedGray = Mat()
        val sharpenedGray = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(sourceRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            flattenIllumination(
                sourceGray = gray,
                outputGray = flattenedGray,
                backgroundBlurSigma = tuning.backgroundBlurSigma,
                strength = tuning.shadowStrength,
                targetBackground = tuning.backgroundTarget,
                textMaskSensitivity = tuning.textMaskSensitivity,
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
            )
            adjustTone(
                source = claheGray,
                output = tonedGray,
                contrastScale = tuning.contrastScale,
                brightnessShift = tuning.brightnessShift,
            )
            restoreTextDetails(
                referenceGray = claheGray,
                cleanedGray = tonedGray,
                outputGray = textProtectedGray,
                sensitivity = tuning.textMaskSensitivity,
            )
            sharpenGray(
                sourceGray = textProtectedGray,
                outputGray = sharpenedGray,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(sharpenedGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            gray.release()
            flattenedGray.release()
            claheGray.release()
            tonedGray.release()
            textProtectedGray.release()
            sharpenedGray.release()
            resultRgba.release()
        }
    }

    private fun magicColor(
        sourceRgba: Mat,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.magicColor(profile)
        val bgr = Mat()
        val enhancedBgr = Mat()
        val saturatedBgr = Mat()
        val sharpened = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(sourceRgba, bgr, Imgproc.COLOR_RGBA2BGR)
            enhanceLabLightness(
                sourceBgr = bgr,
                outputBgr = enhancedBgr,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
                backgroundBlurSigma = 0.0,
                shadowStrength = 0.0,
                backgroundTarget = 232.0,
                textMaskSensitivity = 12.0,
                contrastScale = tuning.contrastScale,
                brightnessShift = tuning.brightnessShift,
            )
            boostSaturation(
                sourceBgr = enhancedBgr,
                outputBgr = saturatedBgr,
                scale = tuning.saturationScale,
            )
            sharpenColor(
                sourceBgr = saturatedBgr,
                outputBgr = sharpened,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_BGR2RGBA)
            return resultRgba.toBitmap()
        } finally {
            bgr.release()
            enhancedBgr.release()
            saturatedBgr.release()
            sharpened.release()
            resultRgba.release()
        }
    }

    private fun receipt(
        sourceRgba: Mat,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.receipt(profile)
        val gray = Mat()
        val flattenedGray = Mat()
        val claheGray = Mat()
        val denoisedGray = Mat()
        val thresholded = Mat()
        val blendedGray = Mat()
        val sharpenedGray = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(sourceRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            flattenIllumination(
                sourceGray = gray,
                outputGray = flattenedGray,
                backgroundBlurSigma = tuning.backgroundBlurSigma,
                strength = tuning.shadowStrength,
                targetBackground = tuning.backgroundTarget,
                textMaskSensitivity = tuning.textMaskSensitivity,
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
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
            Core.addWeighted(
                denoisedGray,
                1.0 - tuning.binaryBlend,
                thresholded,
                tuning.binaryBlend,
                0.0,
                blendedGray,
            )
            sharpenGray(
                sourceGray = blendedGray,
                outputGray = sharpenedGray,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(sharpenedGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            gray.release()
            flattenedGray.release()
            claheGray.release()
            denoisedGray.release()
            thresholded.release()
            blendedGray.release()
            sharpenedGray.release()
            resultRgba.release()
        }
    }

    private fun softBlackAndWhite(
        sourceRgba: Mat,
        profile: PageImageProfile?,
    ): Bitmap {
        val tuning = AdaptivePageFilterTuning.softBlackAndWhite(profile)
        val gray = Mat()
        val flattenedGray = Mat()
        val claheGray = Mat()
        val denoisedGray = Mat()
        val binaryMask = Mat()
        val blendedGray = Mat()
        val softenedGray = Mat()
        val resultRgba = Mat()

        try {
            Imgproc.cvtColor(sourceRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            flattenIllumination(
                sourceGray = gray,
                outputGray = flattenedGray,
                backgroundBlurSigma = tuning.backgroundBlurSigma,
                strength = tuning.shadowStrength,
                targetBackground = tuning.backgroundTarget,
                textMaskSensitivity = tuning.textMaskSensitivity,
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
            )
            Imgproc.bilateralFilter(
                claheGray,
                denoisedGray,
                tuning.denoiseDiameter,
                tuning.denoiseSigmaColor,
                tuning.denoiseSigmaSpace,
            )
            Imgproc.adaptiveThreshold(
                denoisedGray,
                binaryMask,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                tuning.blockSize,
                tuning.c,
            )
            Core.addWeighted(
                denoisedGray,
                1.0 - tuning.binaryBlend,
                binaryMask,
                tuning.binaryBlend,
                0.0,
                blendedGray,
            )
            sharpenGray(
                sourceGray = blendedGray,
                outputGray = softenedGray,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(softenedGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toBitmap()
        } finally {
            gray.release()
            flattenedGray.release()
            claheGray.release()
            denoisedGray.release()
            binaryMask.release()
            blendedGray.release()
            softenedGray.release()
            resultRgba.release()
        }
    }

    private fun flattenIllumination(
        sourceGray: Mat,
        outputGray: Mat,
        backgroundBlurSigma: Double,
        strength: Double,
        targetBackground: Double,
        textMaskSensitivity: Double,
    ) {
        val textSuppressed = Mat()
        val background = Mat()
        val sourceFloat = Mat()
        val backgroundFloat = Mat()
        val safeBackgroundFloat = Mat()
        val flattenedFloat = Mat()
        val flattenedGray = Mat()
        val textResponse = Mat()
        val textMask = Mat()
        val textKernelSize = oddKernel(
            value = maxOf(sourceGray.rows(), sourceGray.cols()) / 220,
            min = 5,
            max = 13,
        )
        val textRemovalKernel = Imgproc.getStructuringElement(
            Imgproc.MORPH_RECT,
            Size(textKernelSize.toDouble(), textKernelSize.toDouble()),
        )
        val maskDilationKernel = Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE, Size(3.0, 3.0))

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
                1.0 - strength.coerceIn(0.0, 1.0),
                flattenedGray,
                strength.coerceIn(0.0, 1.0),
                0.0,
                outputGray,
            )

            Core.subtract(textSuppressed, sourceGray, textResponse)
            Imgproc.threshold(
                textResponse,
                textMask,
                textMaskSensitivity,
                255.0,
                Imgproc.THRESH_BINARY,
            )
            Imgproc.dilate(textMask, textMask, maskDilationKernel)
            sourceGray.copyTo(outputGray, textMask)
        } finally {
            textSuppressed.release()
            background.release()
            sourceFloat.release()
            backgroundFloat.release()
            safeBackgroundFloat.release()
            flattenedFloat.release()
            flattenedGray.release()
            textResponse.release()
            textMask.release()
            textRemovalKernel.release()
            maskDilationKernel.release()
        }
    }

    private fun applyClahe(
        sourceGray: Mat,
        outputGray: Mat,
        clipLimit: Double,
        tileGridSize: Int,
    ) {
        val resolvedTileGridSize = tileGridSize.coerceIn(4, 12).toDouble()
        val clahe = Imgproc.createCLAHE(clipLimit, Size(resolvedTileGridSize, resolvedTileGridSize))
        try {
            clahe.apply(sourceGray, outputGray)
        } finally {
            clahe.collectGarbage()
        }
    }

    private fun enhanceLabLightness(
        sourceBgr: Mat,
        outputBgr: Mat,
        clipLimit: Double,
        tileGridSize: Int,
        backgroundBlurSigma: Double,
        shadowStrength: Double,
        backgroundTarget: Double,
        textMaskSensitivity: Double,
        contrastScale: Double,
        brightnessShift: Double,
    ) {
        val lab = Mat()
        val mergedLab = Mat()
        val correctedLightness = Mat()
        val claheLightness = Mat()
        val tonedLightness = Mat()

        try {
            Imgproc.cvtColor(sourceBgr, lab, Imgproc.COLOR_BGR2Lab)
            val labChannels = mutableListOf<Mat>()
            try {
                Core.split(lab, labChannels)
                if (shadowStrength > 0.0) {
                    flattenIllumination(
                        sourceGray = labChannels[0],
                        outputGray = correctedLightness,
                        backgroundBlurSigma = backgroundBlurSigma,
                        strength = shadowStrength,
                        targetBackground = backgroundTarget,
                        textMaskSensitivity = textMaskSensitivity,
                    )
                } else {
                    labChannels[0].copyTo(correctedLightness)
                }
                applyClahe(
                    sourceGray = correctedLightness,
                    outputGray = claheLightness,
                    clipLimit = clipLimit,
                    tileGridSize = tileGridSize,
                )
                adjustTone(
                    source = claheLightness,
                    output = tonedLightness,
                    contrastScale = contrastScale,
                    brightnessShift = brightnessShift,
                )
                tonedLightness.copyTo(labChannels[0])
                Core.merge(labChannels, mergedLab)
            } finally {
                labChannels.forEach(Mat::release)
            }
            Imgproc.cvtColor(mergedLab, outputBgr, Imgproc.COLOR_Lab2BGR)
        } finally {
            lab.release()
            mergedLab.release()
            correctedLightness.release()
            claheLightness.release()
            tonedLightness.release()
        }
    }

    private fun boostSaturation(
        sourceBgr: Mat,
        outputBgr: Mat,
        scale: Double,
    ) {
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

    private fun adjustTone(
        source: Mat,
        output: Mat,
        contrastScale: Double,
        brightnessShift: Double,
    ) {
        source.convertTo(output, -1, contrastScale, brightnessShift)
    }

    private fun restoreTextDetails(
        referenceGray: Mat,
        cleanedGray: Mat,
        outputGray: Mat,
        sensitivity: Double,
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

    private fun createTextMask(
        sourceGray: Mat,
        outputMask: Mat,
        sensitivity: Double,
    ) {
        val localBackground = Mat()
        val textResponse = Mat()
        val kernelSize = oddKernel(
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

    private fun Mat.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bitmap)
        return bitmap
    }

    private fun aspectRatio(width: Int, height: Int): Double {
        val shortEdge = minOf(width, height).coerceAtLeast(1)
        return maxOf(width, height).toDouble() / shortEdge.toDouble()
    }

    private fun oddKernel(value: Int, min: Int, max: Int): Int {
        var candidate = value.coerceIn(min, max)
        if (candidate % 2 == 0) {
            candidate = if (candidate >= max) candidate - 1 else candidate + 1
        }
        return candidate.coerceAtLeast(3)
    }

    private const val ANALYSIS_MAX_DIMENSION = 720
    private const val HIGHLIGHT_THRESHOLD = 220.0
    private const val COLOR_SATURATION_THRESHOLD = 40.0
    private const val TEXT_ANALYSIS_C = 10.0
    private const val CANNY_LOW_THRESHOLD = 40.0
    private const val CANNY_HIGH_THRESHOLD = 120.0
}
