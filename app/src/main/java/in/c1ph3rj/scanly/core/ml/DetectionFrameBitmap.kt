package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import kotlin.math.roundToInt

fun DetectionFrame.toOrientedBitmap(): Bitmap {
    require(width > 0 && height > 0 && bytes.size >= width * height * RGBA_PIXEL_STRIDE) {
        "Detection frame does not contain a complete RGBA image."
    }
    val pixels = IntArray(width * height)
    var offset = 0
    for (index in pixels.indices) {
        pixels[index] = Color.argb(
            bytes[offset + 3].toInt() and 0xFF,
            bytes[offset].toInt() and 0xFF,
            bytes[offset + 1].toInt() and 0xFF,
            bytes[offset + 2].toInt() and 0xFF,
        )
        offset += RGBA_PIXEL_STRIDE
    }
    val source = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    if (normalizedRotation == 0) return source
    return Bitmap.createBitmap(
        source,
        0,
        0,
        source.width,
        source.height,
        Matrix().apply { postRotate(normalizedRotation.toFloat()) },
        true,
    ).also { source.recycle() }
}

/**
 * Reduces the bitmap to a [PixelGrid] whose long side is about [longSide] cells. Scales to
 * 4x the target first and box-averages the rest, so thin print still shows up as grey cells.
 */
fun Bitmap.toPixelGrid(longSide: Int = FlatPageQuadPolicy.ANALYSIS_LONG_SIDE): PixelGrid {
    val scale = longSide.toFloat() / maxOf(width, height)
    val gridWidth = (width * scale).roundToInt().coerceAtLeast(1)
    val gridHeight = (height * scale).roundToInt().coerceAtLeast(1)
    val factor = if (gridWidth * GRID_SUPERSAMPLE <= width) GRID_SUPERSAMPLE else 1
    val scaled = Bitmap.createScaledBitmap(this, gridWidth * factor, gridHeight * factor, true)
    try {
        val pixels = IntArray(scaled.width * scaled.height)
        scaled.getPixels(pixels, 0, scaled.width, 0, 0, scaled.width, scaled.height)
        return PixelGrid(scaled.width, scaled.height, pixels).downsample(factor)
    } finally {
        if (scaled !== this) scaled.recycle()
    }
}

private const val RGBA_PIXEL_STRIDE = 4
private const val GRID_SUPERSAMPLE = 4
