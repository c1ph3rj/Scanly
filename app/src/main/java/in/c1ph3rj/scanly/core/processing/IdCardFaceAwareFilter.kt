package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.ml.NormalizedFaceRegion
import `in`.c1ph3rj.scanly.domain.model.PageFilterPreset
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * Softly restores source portrait pixels after an ID preset is rendered.
 *
 * Text and security patterns keep the selected filter, while faces retain
 * natural skin tone and fine detail. The feathered mask deliberately extends
 * beyond ML Kit's face box to avoid visible seams around hair and shoulders.
 */
internal object IdCardFaceAwareFilter {
    fun apply(
        sourceBitmap: Bitmap,
        filteredBitmap: Bitmap,
        faceRegions: List<NormalizedFaceRegion>,
        preset: PageFilterPreset,
    ): Bitmap {
        val blendStrength = preset.faceBlendStrength()
        val usableRegions = faceRegions.filter(NormalizedFaceRegion::isUsable)
        if (blendStrength <= 0.001 || usableRegions.isEmpty()) {
            return filteredBitmap
        }

        val source = Mat()
        val filtered = Mat()
        val sourceFloat = Mat()
        val filteredFloat = Mat()
        val mask = Mat.zeros(filteredBitmap.height, filteredBitmap.width, CvType.CV_8UC1)
        val maskFloat = Mat()
        val maskChannels = ArrayList<Mat>(4)
        val maskRgba = Mat()
        val inverseMask = Mat()
        val sourceContribution = Mat()
        val filteredContribution = Mat()
        val blendedFloat = Mat()
        val blended = Mat()

        try {
            Utils.bitmapToMat(sourceBitmap, source)
            Utils.bitmapToMat(filteredBitmap, filtered)
            source.convertTo(sourceFloat, CvType.CV_32FC4)
            filtered.convertTo(filteredFloat, CvType.CV_32FC4)

            usableRegions.forEach { region ->
                val protected = region.padded(
                    horizontalFraction = HORIZONTAL_PADDING,
                    verticalFraction = VERTICAL_PADDING,
                )
                val center = Point(
                    ((protected.left + protected.right) * 0.5f * mask.cols()).toDouble(),
                    ((protected.top + protected.bottom) * 0.5f * mask.rows()).toDouble(),
                )
                val axes = Size(
                    (protected.width * mask.cols() * 0.5f).toDouble().coerceAtLeast(1.0),
                    (protected.height * mask.rows() * 0.5f).toDouble().coerceAtLeast(1.0),
                )
                Imgproc.ellipse(
                    mask,
                    center,
                    axes,
                    0.0,
                    0.0,
                    360.0,
                    Scalar.all(255.0),
                    Imgproc.FILLED,
                )
            }

            val featherSigma = maxOf(mask.cols(), mask.rows()) * FEATHER_SIGMA_FRACTION
            Imgproc.GaussianBlur(mask, mask, Size(0.0, 0.0), featherSigma)
            mask.convertTo(
                maskFloat,
                CvType.CV_32FC1,
                blendStrength / 255.0,
            )
            repeat(4) {
                maskChannels += maskFloat
            }
            Core.merge(maskChannels, maskRgba)
            Core.multiply(maskRgba, Scalar.all(-1.0), inverseMask)
            Core.add(inverseMask, Scalar.all(1.0), inverseMask)
            Core.multiply(sourceFloat, maskRgba, sourceContribution)
            Core.multiply(filteredFloat, inverseMask, filteredContribution)
            Core.add(sourceContribution, filteredContribution, blendedFloat)
            blendedFloat.convertTo(blended, CvType.CV_8UC4)

            return Bitmap.createBitmap(
                filteredBitmap.width,
                filteredBitmap.height,
                Bitmap.Config.ARGB_8888,
            ).also { output ->
                Utils.matToBitmap(blended, output)
            }
        } finally {
            source.release()
            filtered.release()
            sourceFloat.release()
            filteredFloat.release()
            mask.release()
            maskFloat.release()
            maskRgba.release()
            inverseMask.release()
            sourceContribution.release()
            filteredContribution.release()
            blendedFloat.release()
            blended.release()
        }
    }

    private fun PageFilterPreset.faceBlendStrength(): Double = when (this) {
        PageFilterPreset.ID_NATURAL -> 0.68
        PageFilterPreset.ID_CLEAR -> 0.54
        PageFilterPreset.ID_PORTRAIT -> 0.82
        PageFilterPreset.ID_TEXT -> 0.62
        else -> 0.0
    }

    private const val HORIZONTAL_PADDING = 0.22f
    private const val VERTICAL_PADDING = 0.32f
    private const val FEATHER_SIGMA_FRACTION = 0.018
}
