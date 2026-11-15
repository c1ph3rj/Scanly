package `in`.c1ph3rj.scanly.core.ml

import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Luminance raster (0..255 per cell) used by the edge analysers. Free of Android types so the
 * gradient logic runs in plain JVM tests; build one from a bitmap with `Bitmap.toLumaGrid()`.
 *
 * Normalized quad coordinates map to pixels as `x * (width - 1)`, `y * (height - 1)`.
 */
class LumaGrid(
    val width: Int,
    val height: Int,
    val luma: FloatArray,
) {
    init {
        require(width > 0 && height > 0 && luma.size >= width * height) {
            "Luma grid needs width * height values."
        }
    }

    /** Width / height of the image, the aspect the normalized quad coordinates are relative to. */
    val aspectRatio: Float get() = width.toFloat() / height

    operator fun get(x: Int, y: Int): Float = luma[y * width + x]

    fun toPixelX(normalizedX: Float): Float = normalizedX * (width - 1)

    fun toPixelY(normalizedY: Float): Float = normalizedY * (height - 1)

    fun toNormalizedX(pixelX: Float): Float = if (width > 1) pixelX / (width - 1) else 0f

    fun toNormalizedY(pixelY: Float): Float = if (height > 1) pixelY / (height - 1) else 0f

    /** Nearest-cell value, or null outside the grid. */
    fun nearest(x: Float, y: Float): Float? {
        val pixelX = x.roundToInt()
        val pixelY = y.roundToInt()
        if (pixelX !in 0 until width || pixelY !in 0 until height) return null
        return this[pixelX, pixelY]
    }

    /** Bilinear value at a sub-pixel position, or null outside the grid. */
    fun sample(x: Float, y: Float): Float? {
        if (x.isNaN() || y.isNaN() || x < 0f || y < 0f || x > width - 1 || y > height - 1) return null
        val left = floor(x).toInt().coerceAtMost(width - 1)
        val top = floor(y).toInt().coerceAtMost(height - 1)
        val right = (left + 1).coerceAtMost(width - 1)
        val bottom = (top + 1).coerceAtMost(height - 1)
        val fx = x - left
        val fy = y - top
        val upper = this[left, top] + (this[right, top] - this[left, top]) * fx
        val lower = this[left, bottom] + (this[right, bottom] - this[left, bottom]) * fx
        return upper + (lower - upper) * fy
    }

    companion object {
        /** Builds a grid from packed `0xAARRGGBB` / `0xRRGGBB` pixels (alpha ignored). */
        fun fromRgb(width: Int, height: Int, pixels: IntArray): LumaGrid {
            require(pixels.size >= width * height) { "Pixel array is smaller than width * height." }
            val values = FloatArray(width * height)
            for (index in values.indices) {
                val color = pixels[index]
                values[index] = (0.2126f * ((color shr 16) and 0xFF)) +
                    (0.7152f * ((color shr 8) and 0xFF)) +
                    (0.0722f * (color and 0xFF))
            }
            return LumaGrid(width, height, values)
        }
    }
}

/** Width/height used for aspect-corrected geometry; non-positive or non-finite values mean 1. */
internal fun sanitizeFrameAspectRatio(frameAspectRatio: Float): Float =
    if (frameAspectRatio.isFinite() && frameAspectRatio > 0f) frameAspectRatio else 1f
