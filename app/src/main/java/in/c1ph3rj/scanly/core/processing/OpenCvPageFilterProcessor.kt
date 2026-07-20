package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDouble
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

object OpenCvPageFilterProcessor {

    data class AppliedFilter(
        val bitmap: Bitmap,
        /** Concrete preset that was rendered (Auto resolves to grayscale/clean/etc.). */
        val appliedPreset: PageFilterPreset,
    )

    fun apply(
        sourceBitmap: Bitmap,
        filterPreset: PageFilterPreset,
    ): Bitmap = applyWithResolvedPreset(sourceBitmap, filterPreset).bitmap

    fun applyWithResolvedPreset(
        sourceBitmap: Bitmap,
        filterPreset: PageFilterPreset,
    ): AppliedFilter {
        OpenCvMatSupport.ensureInitialized()
        if (filterPreset == PageFilterPreset.ORIGINAL) {
            return AppliedFilter(
                bitmap = sourceBitmap.copy(Bitmap.Config.ARGB_8888, false),
                appliedPreset = PageFilterPreset.ORIGINAL,
            )
        }

        val sourceRgba = sourceBitmap.toOpenCvMat()
        return try {
            val profile = runCatching {
                analyze(
                    sourceRgba = sourceRgba,
                    sourceLongestEdge = maxOf(sourceBitmap.width, sourceBitmap.height),
                    sourceAspectRatio = OpenCvMatSupport.aspectRatio(
                        sourceBitmap.width,
                        sourceBitmap.height,
                    ),
                )
            }.getOrNull()
            val resolvedPreset = resolvePreset(filterPreset, profile)
            val bitmap = renderWithFallback(
                sourceRgba = sourceRgba,
                filterPreset = resolvedPreset,
                profile = profile,
            )
            AppliedFilter(bitmap = bitmap, appliedPreset = resolvedPreset)
        } finally {
            sourceRgba.release()
        }
    }

    /** Resolves Auto to a concrete preset using the same analysis as rendering. */
    private fun resolvePreset(
        filterPreset: PageFilterPreset,
        profile: PageImageProfile?,
    ): PageFilterPreset =
        if (filterPreset == PageFilterPreset.AUTO) {
            AdaptivePageFilterTuning.automatic(profile)
        } else {
            filterPreset
        }

    internal fun applyAll(
        sourceBitmap: Bitmap,
        filterPresets: List<PageFilterPreset> = PageFilterPreset.entries,
    ): Map<PageFilterPreset, Bitmap> {
        OpenCvMatSupport.ensureInitialized()
        val sourceRgba = sourceBitmap.toOpenCvMat()
        return try {
            val profile = runCatching {
                analyze(
                    sourceRgba = sourceRgba,
                    sourceLongestEdge = maxOf(sourceBitmap.width, sourceBitmap.height),
                    sourceAspectRatio = OpenCvMatSupport.aspectRatio(
                        sourceBitmap.width,
                        sourceBitmap.height,
                    ),
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
                    sourceRgba.toAndroidBitmap()
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
        val analysisRgba = sourceRgba.forAnalysisDownsample()
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
            val textBlockSize = OpenCvMatSupport.oddKernel(
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
        // Callers resolve Auto before render so we never hit the AUTO branch.
        val concrete = resolvePreset(filterPreset, profile)
        return when (concrete) {
            PageFilterPreset.ORIGINAL -> sourceRgba.toAndroidBitmap()
            PageFilterPreset.AUTO -> grayscale(sourceRgba, profile) // safety net
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
                contrastScale = tuning.contrastScale,
                brightnessShift = tuning.brightnessShift,
                localContrastStrength = tuning.localContrastStrength,
                whiteBalanceStrength = tuning.whiteBalanceStrength,
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
            return resultRgba.toAndroidBitmap()
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
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
                strength = tuning.localContrastStrength,
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
            return resultRgba.toAndroidBitmap()
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
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
                strength = tuning.localContrastStrength,
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
            return resultRgba.toAndroidBitmap()
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
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
                strength = tuning.localContrastStrength,
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
                sensitivity = CLEAN_TEXT_DETAIL_SENSITIVITY,
            )
            sharpenGray(
                sourceGray = textProtectedGray,
                outputGray = sharpenedGray,
                amount = tuning.sharpenAmount,
                sigma = tuning.sharpenSigma,
            )
            Imgproc.cvtColor(sharpenedGray, resultRgba, Imgproc.COLOR_GRAY2RGBA)
            return resultRgba.toAndroidBitmap()
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
                contrastScale = tuning.contrastScale,
                brightnessShift = tuning.brightnessShift,
                localContrastStrength = tuning.localContrastStrength,
                whiteBalanceStrength = tuning.whiteBalanceStrength,
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
            return resultRgba.toAndroidBitmap()
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
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
                strength = tuning.localContrastStrength,
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
            return resultRgba.toAndroidBitmap()
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
            )
            applyClahe(
                sourceGray = flattenedGray,
                outputGray = claheGray,
                clipLimit = tuning.clipLimit,
                tileGridSize = tuning.tileGridSize,
                strength = tuning.localContrastStrength,
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
            return resultRgba.toAndroidBitmap()
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
    ) {
        val textSuppressed = Mat()
        val background = Mat()
        val sourceFloat = Mat()
        val backgroundFloat = Mat()
        val safeBackgroundFloat = Mat()
        val flattenedFloat = Mat()
        val flattenedGray = Mat()
        val textKernelSize = OpenCvMatSupport.oddKernel(
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
                1.0 - strength.coerceIn(0.0, 1.0),
                flattenedGray,
                strength.coerceIn(0.0, 1.0),
                0.0,
                outputGray,
            )
        } finally {
            textSuppressed.release()
            background.release()
            sourceFloat.release()
            backgroundFloat.release()
            safeBackgroundFloat.release()
            flattenedFloat.release()
            flattenedGray.release()
            textRemovalKernel.release()
        }
    }

    private fun applyClahe(
        sourceGray: Mat,
        outputGray: Mat,
        clipLimit: Double,
        tileGridSize: Int,
        strength: Double,
    ) {
        val resolvedTileGridSize = tileGridSize.coerceIn(4, 12).toDouble()
        val clahe = Imgproc.createCLAHE(clipLimit, Size(resolvedTileGridSize, resolvedTileGridSize))
        val enhancedGray = Mat()
        try {
            clahe.apply(sourceGray, enhancedGray)
            Core.addWeighted(
                sourceGray,
                1.0 - strength.coerceIn(0.0, 1.0),
                enhancedGray,
                strength.coerceIn(0.0, 1.0),
                0.0,
                outputGray,
            )
        } finally {
            enhancedGray.release()
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
        contrastScale: Double,
        brightnessShift: Double,
        localContrastStrength: Double,
        whiteBalanceStrength: Double,
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
                    )
                } else {
                    labChannels[0].copyTo(correctedLightness)
                }
                applyClahe(
                    sourceGray = correctedLightness,
                    outputGray = claheLightness,
                    clipLimit = clipLimit,
                    tileGridSize = tileGridSize,
                    strength = localContrastStrength,
                )
                adjustTone(
                    source = claheLightness,
                    output = tonedLightness,
                    contrastScale = contrastScale,
                    brightnessShift = brightnessShift,
                )
                neutralizePaperCast(
                    lightness = labChannels[0],
                    channelA = labChannels[1],
                    channelB = labChannels[2],
                    strength = whiteBalanceStrength,
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

    private fun neutralizePaperCast(
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
            Core.absdiff(channelA, Scalar.all(LAB_NEUTRAL_CHANNEL_VALUE), aDistance)
            Core.absdiff(channelB, Scalar.all(LAB_NEUTRAL_CHANNEL_VALUE), bDistance)
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
            if (pixelCount <= 0L || paperPixelCount < (pixelCount * MIN_PAPER_MASK_RATIO).toLong()) return

            val paperA = Core.mean(channelA, paperMask).`val`[0]
            val paperB = Core.mean(channelB, paperMask).`val`[0]
            val resolvedStrength = strength.coerceIn(0.0, 1.0)
            val aShift = ((LAB_NEUTRAL_CHANNEL_VALUE - paperA) * resolvedStrength)
                .coerceIn(-MAX_WHITE_BALANCE_SHIFT, MAX_WHITE_BALANCE_SHIFT)
            val bShift = ((LAB_NEUTRAL_CHANNEL_VALUE - paperB) * resolvedStrength)
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
        val kernelSize = OpenCvMatSupport.oddKernel(
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

    private const val HIGHLIGHT_THRESHOLD = 220.0
    private const val COLOR_SATURATION_THRESHOLD = 40.0
    private const val TEXT_ANALYSIS_C = 10.0
    private const val CANNY_LOW_THRESHOLD = 40.0
    private const val CANNY_HIGH_THRESHOLD = 120.0
    private const val CLEAN_TEXT_DETAIL_SENSITIVITY = 10.0
    private const val LAB_NEUTRAL_CHANNEL_VALUE = 128.0
    private const val PAPER_LIGHTNESS_THRESHOLD = 150.0
    private const val PAPER_CHROMA_DISTANCE_THRESHOLD = 42.0
    private const val MIN_PAPER_MASK_RATIO = 0.08
    private const val MAX_WHITE_BALANCE_SHIFT = 18.0
}
