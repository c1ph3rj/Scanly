package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import `in`.c1ph3rj.scanly.core.processing.filter.PageBackgroundModel
import `in`.c1ph3rj.scanly.core.processing.filter.PageFilterOperators
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

/**
 * Turns photos or scans of handwritten signatures into transparent ink masks.
 *
 * Every output is black ink whose alpha is the ink coverage, so it tints cleanly on any paper.
 * Inputs are never mutated or recycled.
 */
@Singleton
class SignatureInkExtractor @Inject constructor() {

    /**
     * Extracts dark ink from [source] (any config) into an ARGB_8888 bitmap whose RGB is pure black
     * and whose alpha is the ink coverage, cropped to the ink bounds plus a small padding and scaled
     * so the long edge is at most [maxOutputEdge]. [sensitivity] 0..1 (default 0.5): higher keeps
     * fainter strokes. Returns null when no ink is found. Never mutates or recycles [source].
     */
    fun extract(source: Bitmap, sensitivity: Float = 0.5f, maxOutputEdge: Int = 1200): Bitmap? {
        OpenCvPageFilterProcessor.ensureInitialized()
        val flat = scaledArgbCopy(source, WORK_EDGE, flattenOnWhite = true)
        val width = flat.width
        val height = flat.height
        val rgba = Mat()
        val rgb = Mat()
        val gray = Mat()
        val coverage = Mat()
        var background: PageBackgroundModel? = null
        try {
            Utils.bitmapToMat(flat, rgba)
            Imgproc.cvtColor(rgba, rgb, Imgproc.COLOR_RGBA2RGB)
            val model = PageBackgroundModel.estimate(rgb, STROKE_FRACTION)
            background = model
            Imgproc.cvtColor(rgb, gray, Imgproc.COLOR_RGB2GRAY)
            flattenShading(gray, model)
            // Dark ink maps to 0 after binarize; despeckle treats dark pixels as ink.
            PageFilterOperators.binarize(gray, gray, inkThreshold(sensitivity), RAMP)
            PageFilterOperators.despeckle(gray, speckleArea(width, height))
            // Invert so ink carries high coverage.
            Core.bitwise_not(gray, coverage)
            val bytes = ByteArray(width * height)
            coverage.get(0, 0, bytes)
            val alpha = IntArray(bytes.size) { bytes[it].toInt() and 0xFF }
            return alphaToMask(alpha, width, height, maxOutputEdge)
        } finally {
            background?.release()
            flat.recycle()
            rgba.release()
            rgb.release()
            gray.release()
            coverage.release()
        }
    }

    /**
     * For images that already have transparency (e.g. a PNG signature): alpha = srcAlpha * darkness
     * (darkness = 1 - luminance, so coloured/black ink on transparent stays, white halos vanish),
     * RGB black, cropped + scaled the same way. Returns null if nothing visible.
     */
    fun maskFromTransparent(source: Bitmap, maxOutputEdge: Int = 1200): Bitmap? {
        val work = scaledArgbCopy(source, WORK_EDGE, flattenOnWhite = false)
        try {
            val width = work.width
            val height = work.height
            val pixels = IntArray(width * height)
            work.getPixels(pixels, 0, width, 0, 0, width, height)
            val alpha = IntArray(pixels.size) { i ->
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val luminance = (LUMA_R * r + LUMA_G * g + LUMA_B * b) shr 8
                ((p ushr 24) * (255 - luminance)) / 255
            }
            return alphaToMask(alpha, width, height, maxOutputEdge)
        } finally {
            work.recycle()
        }
    }

    /** True when at least ~2% of sampled pixels are meaningfully transparent (alpha < 200). */
    fun hasMeaningfulAlpha(source: Bitmap): Boolean {
        val sample = scaledArgbCopy(source, SAMPLE_EDGE, flattenOnWhite = false)
        try {
            val width = sample.width
            val height = sample.height
            val pixels = IntArray(width * height)
            sample.getPixels(pixels, 0, width, 0, 0, width, height)
            var total = 0
            var transparent = 0
            for (y in 0 until height step GRID_STEP) {
                for (x in 0 until width step GRID_STEP) {
                    total++
                    if ((pixels[y * width + x] ushr 24) < MEANINGFUL_ALPHA) transparent++
                }
            }
            return total > 0 && transparent.toDouble() / total >= MIN_TRANSPARENT_RATIO
        } finally {
            sample.recycle()
        }
    }

    /** Grayscale-normalizes [gray] in place so paper and shading become white. */
    private fun flattenShading(gray: Mat, model: PageBackgroundModel) {
        val background = model.grayBackground()
        try {
            PageBackgroundModel.normalizeChannel(
                source = gray,
                backgroundSmall = background,
                reference = model.paperReferenceGray,
                target = PAPER_TARGET,
                maxGain = MAX_GAIN,
                denoiseSigma = DENOISE_SIGMA,
                output = gray,
            )
        } finally {
            background.release()
        }
    }

    /** Crops [alpha] to its visible ink plus padding and renders it as black ink with alpha. */
    private fun alphaToMask(alpha: IntArray, width: Int, height: Int, maxOutputEdge: Int): Bitmap? {
        val box = inkBox(alpha, width, height) ?: return null
        val pad = max(MIN_PAD, (max(box.width, box.height) * PAD_FRACTION).roundToInt())
        val left = max(0, box.left - pad)
        val top = max(0, box.top - pad)
        val right = min(width, box.right + pad)
        val bottom = min(height, box.bottom + pad)
        val cropWidth = right - left
        val cropHeight = bottom - top
        val pixels = IntArray(cropWidth * cropHeight)
        for (y in 0 until cropHeight) {
            val rowStart = (top + y) * width + left
            for (x in 0 until cropWidth) {
                val coverage = alpha[rowStart + x].coerceIn(0, 255)
                pixels[y * cropWidth + x] = Color.argb(coverage, 0, 0, 0)
            }
        }
        val full = Bitmap.createBitmap(pixels, cropWidth, cropHeight, Bitmap.Config.ARGB_8888)
        return fitLongEdge(full, maxOutputEdge)
    }

    /** Bounding box of pixels above [VISIBLE_ALPHA], or null when none or too small. */
    private fun inkBox(alpha: IntArray, width: Int, height: Int): InkBox? {
        var left = width
        var top = height
        var right = -1
        var bottom = -1
        for (y in 0 until height) {
            val row = y * width
            for (x in 0 until width) {
                if (alpha[row + x] > VISIBLE_ALPHA) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    bottom = y
                }
            }
        }
        if (right < 0) return null
        val box = InkBox(left = left, top = top, right = right + 1, bottom = bottom + 1)
        return if (box.width < MIN_INK_SIDE || box.height < MIN_INK_SIDE) null else box
    }

    /** Software ARGB_8888 copy of [source] scaled so its long edge is at most [maxEdge]. Caller recycles. */
    private fun scaledArgbCopy(source: Bitmap, maxEdge: Int, flattenOnWhite: Boolean): Bitmap {
        val longEdge = max(source.width, source.height).coerceAtLeast(1)
        val scale = min(1f, maxEdge.toFloat() / longEdge)
        val width = (source.width * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * scale).roundToInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (flattenOnWhite) canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            source,
            null,
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return out
    }

    /** Scales [bitmap] down so its long edge is at most [maxEdge]; recycles the input when it scales. */
    private fun fitLongEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = max(bitmap.width, bitmap.height)
        if (longEdge <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longEdge
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width * scale).roundToInt().coerceAtLeast(1),
            (bitmap.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        bitmap.recycle()
        return scaled
    }

    private fun inkThreshold(sensitivity: Float): Double =
        (BASE_THRESHOLD + (sensitivity.coerceIn(0f, 1f) - 0.5) * SENSITIVITY_SPAN)
            .coerceIn(MIN_THRESHOLD, MAX_THRESHOLD)

    private fun speckleArea(width: Int, height: Int): Int {
        val edge = max(width, height).toLong()
        return (edge * edge / SPECKLE_DIVISOR).toInt().coerceIn(MIN_SPECKLE_AREA, MAX_SPECKLE_AREA)
    }

    private class InkBox(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val width: Int get() = right - left
        val height: Int get() = bottom - top
    }

    private companion object {
        const val WORK_EDGE = 1600
        const val SAMPLE_EDGE = 256
        const val GRID_STEP = 2
        const val MEANINGFUL_ALPHA = 200
        const val MIN_TRANSPARENT_RATIO = 0.02

        const val STROKE_FRACTION = 0.05
        const val PAPER_TARGET = 250.0
        const val MAX_GAIN = 4.0
        const val DENOISE_SIGMA = 1.0

        const val BASE_THRESHOLD = 150.0
        const val SENSITIVITY_SPAN = 120.0
        const val MIN_THRESHOLD = 60.0
        const val MAX_THRESHOLD = 235.0
        const val RAMP = 10.0

        const val SPECKLE_DIVISOR = 25_000L
        const val MIN_SPECKLE_AREA = 6
        const val MAX_SPECKLE_AREA = 400

        const val VISIBLE_ALPHA = 24
        const val MIN_INK_SIDE = 6
        const val MIN_PAD = 8
        const val PAD_FRACTION = 0.04

        const val LUMA_R = 77
        const val LUMA_G = 150
        const val LUMA_B = 29
    }
}
