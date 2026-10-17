package `in`.c1ph3rj.scanly.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Decodes page images for on-screen use. Decodes are sub-sampled and then scaled so the long
 * edge is exactly the requested size, which keeps large scans from exhausting memory.
 */
@Singleton
class PageBitmapLoader @Inject constructor(
    private val dispatchers: ScanlyDispatchers,
) {
    /** Width / height of the image at [path], or null when it is missing or unreadable. */
    suspend fun aspectRatio(path: String): Float? = withContext(dispatchers.io) {
        val bounds = readBounds(path) ?: return@withContext null
        bounds.width.toFloat() / bounds.height
    }

    /**
     * Decodes the image at [path] as ARGB_8888 with its long edge at most [maxEdge] pixels.
     * Returns null when the file is missing or cannot be decoded. The caller owns the bitmap.
     */
    suspend fun load(path: String, maxEdge: Int): Bitmap? = withContext(dispatchers.io) {
        val target = maxEdge.coerceAtLeast(1)
        val bounds = readBounds(path) ?: return@withContext null
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSizeFor(maxOf(bounds.width, bounds.height), target)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = BitmapFactory.decodeFile(path, options) ?: return@withContext null
        scaleToLongEdge(decoded, target)
    }

    private fun readBounds(path: String): Bounds? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        if (options.outWidth <= 0 || options.outHeight <= 0) return null
        return Bounds(options.outWidth, options.outHeight)
    }

    /** Largest power of two that still leaves the decoded long edge at or above [target]. */
    private fun sampleSizeFor(longEdge: Int, target: Int): Int {
        var sample = 1
        while (longEdge / (sample * 2) >= target) {
            sample *= 2
        }
        return sample
    }

    /** Scales [source] so its long edge is exactly [target]; recycles [source] when it replaces it. */
    private fun scaleToLongEdge(source: Bitmap, target: Int): Bitmap {
        val longEdge = maxOf(source.width, source.height)
        if (longEdge <= target) return source
        val scale = target.toFloat() / longEdge
        val scaled = if (source.width >= source.height) {
            Bitmap.createScaledBitmap(
                source,
                target,
                (source.height * scale).roundToInt().coerceAtLeast(1),
                true,
            )
        } else {
            Bitmap.createScaledBitmap(
                source,
                (source.width * scale).roundToInt().coerceAtLeast(1),
                target,
                true,
            )
        }
        if (scaled !== source) source.recycle()
        return scaled
    }

    private data class Bounds(val width: Int, val height: Int)
}
