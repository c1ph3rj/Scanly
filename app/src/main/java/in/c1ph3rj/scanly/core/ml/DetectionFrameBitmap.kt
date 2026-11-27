package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import android.graphics.Matrix
import java.nio.ByteBuffer
import kotlin.math.roundToInt

fun DetectionFrame.toOrientedBitmap(): Bitmap {
    require(width > 0 && height > 0 && bytes.size >= width * height * RGBA_PIXEL_STRIDE) {
        "Detection frame does not contain a complete RGBA image."
    }
    // CameraX supplies opaque RGBA, which matches Android's native ARGB_8888 byte layout.
    val source = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888, false)
    val normalizedRotation = ((rotationDegrees % 360) + 360) % 360
    var result: Bitmap? = null
    try {
        source.copyPixelsFromBuffer(ByteBuffer.wrap(bytes))
        result = if (normalizedRotation == 0) source else Bitmap.createBitmap(
            source,
            0,
            0,
            width,
            height,
            Matrix().apply { postRotate(normalizedRotation.toFloat()) },
            true,
        )
        return result
    } finally {
        if (result !== source) source.recycle()
    }
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

/** Full-resolution luminance grid for the edge analysers; one `getPixels` call per bitmap. */
fun Bitmap.toLumaGrid(): LumaGrid {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return LumaGrid.fromRgb(width, height, pixels)
}

/** Width / height of the bitmap: the aspect its normalized quad coordinates are relative to. */
val Bitmap.frameAspectRatio: Float get() = if (height > 0) width.toFloat() / height else 1f

private const val RGBA_PIXEL_STRIDE = 4
private const val GRID_SUPERSAMPLE = 4
