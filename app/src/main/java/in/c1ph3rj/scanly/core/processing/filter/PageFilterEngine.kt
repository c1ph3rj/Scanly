package `in`.c1ph3rj.scanly.core.processing.filter

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.processing.PageImageProfile
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

internal object PageFilterEngine {
    fun render(
        sourceRgba: Mat,
        filterPreset: PageFilterPreset,
        profile: PageImageProfile?,
        renderLongestEdge: Int,
    ): Bitmap {
        if (filterPreset == PageFilterPreset.ORIGINAL) {
            return sourceRgba.toBitmap()
        }

        val recipe = PageFilterRecipes.forPreset(filterPreset)
        val params = PageFilterStrengthController.resolve(
            recipe = recipe,
            profile = profile,
            renderLongestEdge = renderLongestEdge,
        )
        val resultRgba = Mat()
        try {
            when (recipe.colorMode) {
                PageFilterColorMode.Color -> renderColor(sourceRgba, resultRgba, params)
                PageFilterColorMode.Gray -> renderGray(sourceRgba, resultRgba, params)
                PageFilterColorMode.SoftBinary,
                PageFilterColorMode.HardBinary,
                -> renderBinary(sourceRgba, resultRgba, params)
            }
            PageFilterOperators.applyOutputGuard(sourceRgba, resultRgba, recipe, params)
            return resultRgba.toBitmap()
        } finally {
            resultRgba.release()
        }
    }

    private fun renderColor(
        sourceRgba: Mat,
        resultRgba: Mat,
        params: PageFilterAppliedParams,
    ) {
        val bgr = Mat()
        val denoised = Mat()
        val enhancedBgr = Mat()
        val saturatedBgr = Mat()
        val protectMask = Mat()
        try {
            Imgproc.cvtColor(sourceRgba, bgr, Imgproc.COLOR_RGBA2BGR)
            PageFilterOperators.buildContentProtectMask(bgr, protectMask)
            PageFilterOperators.maybeBilateral(
                source = bgr,
                output = denoised,
                diameter = params.bilateralDiameter,
                sigmaColor = params.bilateralSigmaColor,
                sigmaSpace = params.bilateralSigmaSpace,
            )
            enhanceLab(
                sourceBgr = denoised,
                outputBgr = enhancedBgr,
                params = params,
                protectMask = protectMask,
            )
            PageFilterOperators.boostSaturation(
                sourceBgr = enhancedBgr,
                outputBgr = saturatedBgr,
                scale = params.saturationScale,
            )
            PageFilterOperators.sharpenColor(
                sourceBgr = saturatedBgr,
                outputBgr = enhancedBgr,
                amount = params.sharpenAmount,
                sigma = params.sharpenSigma,
                textOnly = true,
            )
            Imgproc.cvtColor(enhancedBgr, resultRgba, Imgproc.COLOR_BGR2RGBA)
        } finally {
            bgr.release()
            denoised.release()
            enhancedBgr.release()
            saturatedBgr.release()
            protectMask.release()
        }
    }

    private fun enhanceLab(
        sourceBgr: Mat,
        outputBgr: Mat,
        params: PageFilterAppliedParams,
        protectMask: Mat,
    ) {
        val lab = Mat()
        val mergedLab = Mat()
        val originalLightness = Mat()
        val flattened = Mat()
        val claheLightness = Mat()
        val tonedLightness = Mat()
        try {
            Imgproc.cvtColor(sourceBgr, lab, Imgproc.COLOR_BGR2Lab)
            val labChannels = mutableListOf<Mat>()
            try {
                Core.split(lab, labChannels)
                labChannels[0].copyTo(originalLightness)
                PageFilterOperators.flattenIllumination(
                    sourceGray = labChannels[0],
                    outputGray = flattened,
                    backgroundBlurSigma = params.backgroundBlurSigma,
                    strength = params.flattenStrength,
                    targetBackground = params.flattenTarget,
                    protectMask = protectMask,
                )
                PageFilterOperators.applyClahe(
                    sourceGray = flattened,
                    outputGray = claheLightness,
                    clipLimit = params.clipLimit,
                    tileGridSize = params.tileGridSize,
                    strength = params.claheStrength,
                )
                PageFilterOperators.applySoftTone(
                    sourceGray = claheLightness,
                    outputGray = tonedLightness,
                    lift = params.toneLift,
                    highlightRolloff = params.highlightRolloff,
                )
                PageFilterOperators.neutralizePaperCast(
                    lightness = originalLightness,
                    channelA = labChannels[1],
                    channelB = labChannels[2],
                    strength = params.whiteBalanceStrength,
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
            originalLightness.release()
            flattened.release()
            claheLightness.release()
            tonedLightness.release()
        }
    }

    private fun renderGray(
        sourceRgba: Mat,
        resultRgba: Mat,
        params: PageFilterAppliedParams,
    ) {
        val gray = Mat()
        val flattened = Mat()
        val claheGray = Mat()
        val denoised = Mat()
        val toned = Mat()
        val textProtected = Mat()
        val sharpened = Mat()
        try {
            Imgproc.cvtColor(sourceRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            PageFilterOperators.flattenIllumination(
                sourceGray = gray,
                outputGray = flattened,
                backgroundBlurSigma = params.backgroundBlurSigma,
                strength = params.flattenStrength,
                targetBackground = params.flattenTarget,
            )
            PageFilterOperators.applyClahe(
                sourceGray = flattened,
                outputGray = claheGray,
                clipLimit = params.clipLimit,
                tileGridSize = params.tileGridSize,
                strength = params.claheStrength,
            )
            PageFilterOperators.maybeBilateral(
                source = claheGray,
                output = denoised,
                diameter = params.bilateralDiameter,
                sigmaColor = params.bilateralSigmaColor,
                sigmaSpace = params.bilateralSigmaSpace,
            )
            PageFilterOperators.applySoftTone(
                sourceGray = denoised,
                outputGray = toned,
                lift = params.toneLift,
                highlightRolloff = params.highlightRolloff,
            )
            if (params.restoreText) {
                PageFilterOperators.restoreTextDetails(
                    referenceGray = claheGray,
                    cleanedGray = toned,
                    outputGray = textProtected,
                )
            } else {
                toned.copyTo(textProtected)
            }
            PageFilterOperators.sharpenGray(
                sourceGray = textProtected,
                outputGray = sharpened,
                amount = params.sharpenAmount,
                sigma = params.sharpenSigma,
                textOnly = true,
            )
            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_GRAY2RGBA)
        } finally {
            gray.release()
            flattened.release()
            claheGray.release()
            denoised.release()
            toned.release()
            textProtected.release()
            sharpened.release()
        }
    }

    private fun renderBinary(
        sourceRgba: Mat,
        resultRgba: Mat,
        params: PageFilterAppliedParams,
    ) {
        val gray = Mat()
        val flattened = Mat()
        val claheGray = Mat()
        val denoised = Mat()
        val thresholded = Mat()
        val blended = Mat()
        val sharpened = Mat()
        try {
            Imgproc.cvtColor(sourceRgba, gray, Imgproc.COLOR_RGBA2GRAY)
            PageFilterOperators.flattenIllumination(
                sourceGray = gray,
                outputGray = flattened,
                backgroundBlurSigma = params.backgroundBlurSigma,
                strength = params.flattenStrength,
                targetBackground = params.flattenTarget,
            )
            PageFilterOperators.applyClahe(
                sourceGray = flattened,
                outputGray = claheGray,
                clipLimit = params.clipLimit,
                tileGridSize = params.tileGridSize,
                strength = params.claheStrength,
            )
            PageFilterOperators.maybeBilateral(
                source = claheGray,
                output = denoised,
                diameter = params.bilateralDiameter,
                sigmaColor = params.bilateralSigmaColor,
                sigmaSpace = params.bilateralSigmaSpace,
            )
            Imgproc.adaptiveThreshold(
                denoised,
                thresholded,
                255.0,
                Imgproc.ADAPTIVE_THRESH_GAUSSIAN_C,
                Imgproc.THRESH_BINARY,
                params.blockSize,
                params.adaptiveC,
            )
            if (params.binaryBlend >= 0.995) {
                thresholded.copyTo(blended)
            } else {
                Core.addWeighted(
                    denoised,
                    1.0 - params.binaryBlend,
                    thresholded,
                    params.binaryBlend,
                    0.0,
                    blended,
                )
            }
            PageFilterOperators.sharpenGray(
                sourceGray = blended,
                outputGray = sharpened,
                amount = params.sharpenAmount,
                sigma = params.sharpenSigma,
                textOnly = true,
            )
            Imgproc.cvtColor(sharpened, resultRgba, Imgproc.COLOR_GRAY2RGBA)
        } finally {
            gray.release()
            flattened.release()
            claheGray.release()
            denoised.release()
            thresholded.release()
            blended.release()
            sharpened.release()
        }
    }

    private fun Mat.toBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(cols(), rows(), Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bitmap)
        return bitmap
    }
}
