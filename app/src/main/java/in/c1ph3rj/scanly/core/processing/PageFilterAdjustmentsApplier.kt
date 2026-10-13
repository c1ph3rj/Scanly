package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.domain.model.PageFilterAdjustments
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs

/**
 * Applies user filter customizations after a [PageFilterPreset] has been rendered.
 */
object PageFilterAdjustmentsApplier {
    @Volatile
    private var initialized = false

    fun apply(
        sourceBitmap: Bitmap,
        adjustments: PageFilterAdjustments,
    ): Bitmap {
        val sanitized = adjustments.sanitized()
        // Intensity is consumed by the filter engine; only tonal tweaks are applied here.
        val hasTonalChanges = abs(sanitized.brightness) > 0.001f ||
            abs(sanitized.contrast) > 0.001f ||
            abs(sanitized.saturation) > 0.001f ||
            sanitized.sharpness > 0.001f
        if (!hasTonalChanges) {
            return sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        ensureInitialized()

        val sourceRgba = Mat()
        val working = Mat()
        val resultRgba = Mat()
        try {
            Utils.bitmapToMat(sourceBitmap, sourceRgba)
            // Work in RGB so tonal math never touches alpha (a scaled alpha produced
            // semi-transparent, invalid premultiplied pixels).
            Imgproc.cvtColor(sourceRgba, working, Imgproc.COLOR_RGBA2RGB)

            if (abs(sanitized.brightness) > 0.001f || abs(sanitized.contrast) > 0.001f) {
                applyBrightnessContrast(working, sanitized.brightness, sanitized.contrast)
            }
            if (abs(sanitized.saturation) > 0.001f) {
                applySaturation(working, sanitized.saturation)
            }
            if (sanitized.sharpness > 0.001f) {
                applySharpness(working, sanitized.sharpness)
            }

            Imgproc.cvtColor(working, resultRgba, Imgproc.COLOR_RGB2RGBA)
            return resultRgba.toBitmap(sourceBitmap.width, sourceBitmap.height)
        } finally {
            sourceRgba.release()
            working.release()
            resultRgba.release()
        }
    }

    private fun applyBrightnessContrast(
        rgb: Mat,
        brightness: Float,
        contrast: Float,
    ) {
        // contrast scales around mid-gray: 0 → 1x, +1 → 2x, -1 → flat gray.
        val alpha = (1.0 + contrast.toDouble()).coerceIn(0.0, 2.0)
        val beta = MID_GRAY * (1.0 - alpha) + brightness.toDouble() * 80.0
        val adjusted = Mat()
        try {
            rgb.convertTo(adjusted, -1, alpha, beta)
            adjusted.copyTo(rgb)
        } finally {
            adjusted.release()
        }
    }

    private fun applySaturation(
        rgb: Mat,
        saturation: Float,
    ) {
        val hsv = Mat()
        val channels = ArrayList<Mat>(3)
        try {
            Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)
            Core.split(hsv, channels)
            val scale = (1.0 + saturation.toDouble()).coerceIn(0.0, 2.0)
            Core.multiply(channels[1], Scalar(scale), channels[1])
            Core.merge(channels, hsv)
            Imgproc.cvtColor(hsv, rgb, Imgproc.COLOR_HSV2RGB)
        } finally {
            channels.forEach { it.release() }
            hsv.release()
        }
    }

    private fun applySharpness(
        rgb: Mat,
        sharpness: Float,
    ) {
        val blurred = Mat()
        val sharpened = Mat()
        try {
            val sigma = 1.0 + sharpness.toDouble()
            Imgproc.GaussianBlur(rgb, blurred, Size(0.0, 0.0), sigma)
            // amount: 0 → identity-ish, 1 → strong unsharp
            val amount = 1.0 + (sharpness.toDouble() * 1.4)
            Core.addWeighted(rgb, amount, blurred, 1.0 - amount, 0.0, sharpened)
            sharpened.copyTo(rgb)
        } finally {
            blurred.release()
            sharpened.release()
        }
    }

    private fun Mat.toBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        Utils.matToBitmap(this, bitmap)
        return bitmap
    }

    private const val MID_GRAY = 128.0

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            check(OpenCVLoader.initLocal()) { "OpenCV could not be initialized." }
            initialized = true
        }
    }
}
