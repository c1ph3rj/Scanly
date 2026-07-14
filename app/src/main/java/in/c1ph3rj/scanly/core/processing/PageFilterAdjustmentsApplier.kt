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
        if (sanitized.isDefault) {
            return sourceBitmap.copy(Bitmap.Config.ARGB_8888, false)
        }
        ensureInitialized()

        val sourceRgba = Mat()
        val working = Mat()
        val resultRgba = Mat()
        try {
            Utils.bitmapToMat(sourceBitmap, sourceRgba)
            sourceRgba.copyTo(working)

            if (abs(sanitized.brightness) > 0.001f || abs(sanitized.contrast) > 0.001f) {
                applyBrightnessContrast(working, sanitized.brightness, sanitized.contrast)
            }
            if (abs(sanitized.saturation) > 0.001f) {
                applySaturation(working, sanitized.saturation)
            }
            if (sanitized.sharpness > 0.001f) {
                applySharpness(working, sanitized.sharpness)
            }

            working.copyTo(resultRgba)
            return resultRgba.toBitmap(sourceBitmap.width, sourceBitmap.height)
        } finally {
            sourceRgba.release()
            working.release()
            resultRgba.release()
        }
    }

    private fun applyBrightnessContrast(
        rgba: Mat,
        brightness: Float,
        contrast: Float,
    ) {
        // contrast: 0 → 1x, +1 → ~2x, -1 → ~0x around mid-gray
        val alpha = (1.0 + contrast.toDouble()).coerceIn(0.0, 2.0)
        val beta = brightness.toDouble() * 80.0
        val adjusted = Mat()
        try {
            rgba.convertTo(adjusted, -1, alpha, beta)
            adjusted.copyTo(rgba)
        } finally {
            adjusted.release()
        }
    }

    private fun applySaturation(
        rgba: Mat,
        saturation: Float,
    ) {
        val hsv = Mat()
        val channels = ArrayList<Mat>(3)
        try {
            Imgproc.cvtColor(rgba, hsv, Imgproc.COLOR_RGBA2RGB)
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_RGB2HSV)
            Core.split(hsv, channels)
            val scale = (1.0 + saturation.toDouble()).coerceIn(0.0, 2.0)
            Core.multiply(channels[1], Scalar(scale), channels[1])
            Core.merge(channels, hsv)
            Imgproc.cvtColor(hsv, hsv, Imgproc.COLOR_HSV2RGB)
            val withAlpha = Mat()
            try {
                Imgproc.cvtColor(hsv, withAlpha, Imgproc.COLOR_RGB2RGBA)
                withAlpha.copyTo(rgba)
            } finally {
                withAlpha.release()
            }
        } finally {
            channels.forEach { it.release() }
            hsv.release()
        }
    }

    private fun applySharpness(
        rgba: Mat,
        sharpness: Float,
    ) {
        val blurred = Mat()
        val sharpened = Mat()
        try {
            val sigma = 1.0 + sharpness.toDouble()
            Imgproc.GaussianBlur(rgba, blurred, Size(0.0, 0.0), sigma)
            // amount: 0 → identity-ish, 1 → strong unsharp
            val amount = 1.0 + (sharpness.toDouble() * 1.4)
            Core.addWeighted(rgba, amount, blurred, 1.0 - amount, 0.0, sharpened)
            sharpened.copyTo(rgba)
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

    private fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            check(OpenCVLoader.initLocal()) { "OpenCV could not be initialized." }
            initialized = true
        }
    }
}
