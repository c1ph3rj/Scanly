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
            if (abs(sanitized.highlights) > 0.001f || abs(sanitized.shadows) > 0.001f) {
                applyTone(working, sanitized.highlights, sanitized.shadows)
            }
            if (abs(sanitized.warmth) > 0.001f) {
                applyWarmth(working, sanitized.warmth)
            }
            if (sanitized.sharpness > 0.001f) {
                applySharpness(working, sanitized.sharpness)
            }
            if (sanitized.vignette > 0.001f) {
                applyVignette(working, sanitized.vignette)
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

    private fun applyTone(
        rgba: Mat,
        highlights: Float,
        shadows: Float,
    ) {
        val rgb = Mat()
        val adjusted = Mat()
        val withAlpha = Mat()
        val lookup = Mat(1, 256, org.opencv.core.CvType.CV_8UC1)
        try {
            val values = ByteArray(256) { index ->
                val normalized = index / 255.0
                val shadowWeight = (1.0 - normalized) * (1.0 - normalized)
                val highlightWeight = normalized * normalized
                val delta = shadows * TONE_RANGE * shadowWeight +
                    highlights * TONE_RANGE * highlightWeight
                (index + delta).coerceIn(0.0, 255.0).toInt().toByte()
            }
            lookup.put(0, 0, values)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Core.LUT(rgb, lookup, adjusted)
            Imgproc.cvtColor(adjusted, withAlpha, Imgproc.COLOR_RGB2RGBA)
            withAlpha.copyTo(rgba)
        } finally {
            rgb.release()
            adjusted.release()
            withAlpha.release()
            lookup.release()
        }
    }

    private fun applyWarmth(
        rgba: Mat,
        warmth: Float,
    ) {
        val rgb = Mat()
        val channels = ArrayList<Mat>(3)
        val withAlpha = Mat()
        try {
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            Core.split(rgb, channels)
            val shift = warmth.toDouble() * TEMPERATURE_RANGE
            Core.add(channels[0], Scalar(shift), channels[0])
            Core.add(channels[2], Scalar(-shift), channels[2])
            Core.merge(channels, rgb)
            Imgproc.cvtColor(rgb, withAlpha, Imgproc.COLOR_RGB2RGBA)
            withAlpha.copyTo(rgba)
        } finally {
            channels.forEach(Mat::release)
            rgb.release()
            withAlpha.release()
        }
    }

    private fun applyVignette(
        rgba: Mat,
        vignette: Float,
    ) {
        val rgb = Mat()
        val rgbFloat = Mat()
        val mask = Mat(
            rgba.rows(),
            rgba.cols(),
            org.opencv.core.CvType.CV_32FC1,
            Scalar.all(1.0 - vignette * MAX_VIGNETTE_DARKENING),
        )
        val maskChannels = ArrayList<Mat>(3)
        val maskRgb = Mat()
        val adjustedFloat = Mat()
        val adjusted = Mat()
        val withAlpha = Mat()
        try {
            Imgproc.ellipse(
                mask,
                org.opencv.core.Point(mask.cols() / 2.0, mask.rows() / 2.0),
                Size(mask.cols() * 0.43, mask.rows() * 0.43),
                0.0,
                0.0,
                360.0,
                Scalar.all(1.0),
                Imgproc.FILLED,
            )
            Imgproc.GaussianBlur(
                mask,
                mask,
                Size(0.0, 0.0),
                maxOf(mask.cols(), mask.rows()) * VIGNETTE_FEATHER_FRACTION,
            )
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            rgb.convertTo(rgbFloat, org.opencv.core.CvType.CV_32FC3)
            repeat(3) {
                maskChannels += mask
            }
            Core.merge(maskChannels, maskRgb)
            Core.multiply(rgbFloat, maskRgb, adjustedFloat)
            adjustedFloat.convertTo(adjusted, org.opencv.core.CvType.CV_8UC3)
            Imgproc.cvtColor(adjusted, withAlpha, Imgproc.COLOR_RGB2RGBA)
            withAlpha.copyTo(rgba)
        } finally {
            rgb.release()
            rgbFloat.release()
            mask.release()
            maskRgb.release()
            adjustedFloat.release()
            adjusted.release()
            withAlpha.release()
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

    private const val TONE_RANGE = 64.0
    private const val TEMPERATURE_RANGE = 28.0
    private const val MAX_VIGNETTE_DARKENING = 0.48
    private const val VIGNETTE_FEATHER_FRACTION = 0.16
}
