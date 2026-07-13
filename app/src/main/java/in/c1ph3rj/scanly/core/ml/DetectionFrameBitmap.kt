package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix

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

private const val RGBA_PIXEL_STRIDE = 4
