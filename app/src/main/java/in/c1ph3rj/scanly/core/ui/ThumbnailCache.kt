package `in`.c1ph3rj.scanly.core.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ThumbnailCacheEntryPoint {
    fun thumbnailCache(): ThumbnailCache
}

/**
 * In-process LRU bitmap cache for document/group thumbnails.
 *
 * Sized at 1/8 of max heap (in kilobytes). [LruCache.sizeOf] counts bytes so the
 * cache evicts based on memory pressure, not entry count.
 *
 * Each call to [decode] returns a downsampled bitmap keyed on the file path. On a
 * cache hit the IO round-trip is completely skipped, making list scroll buttery smooth.
 */
@Singleton
class ThumbnailCache @Inject constructor() {

    private val maxSizeKb: Int =
        (Runtime.getRuntime().maxMemory() / 1024L / 8L).toInt().coerceAtLeast(4 * 1024)

    private val cache: LruCache<String, Bitmap> = object : LruCache<String, Bitmap>(maxSizeKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    /** Returns a bitmap already in the LRU cache without touching disk. */
    fun getIfCached(
        path: String,
        targetPx: Int,
        contentRevision: Long = 0L,
    ): Bitmap? = cache.get(cacheKey(path, targetPx, contentRevision))

    /**
     * Returns a cached or freshly decoded bitmap scaled so that neither dimension exceeds
     * [targetPx]. Uses [Bitmap.Config.ARGB_8888] for larger decodes to avoid banding.
     */
    fun decode(
        path: String,
        targetPx: Int,
        contentRevision: Long = 0L,
    ): Bitmap? {
        val key = cacheKey(path, targetPx, contentRevision)
        cache.get(key)?.let { return it }
        val sampled = decodeSampled(path, targetPx) ?: return null
        cache.put(key, sampled)
        return sampled
    }

    /** Removes all cached decode sizes for the current on-disk revision of [path]. */
    fun invalidate(path: String) {
        val prefix = "$path#"
        cache.snapshot().keys.filter { it.startsWith(prefix) }.forEach(cache::remove)
    }

    fun clearAll() {
        cache.evictAll()
    }

    private fun cacheKey(path: String, targetPx: Int, contentRevision: Long): String {
        val revision = contentRevision.takeIf { it > 0L } ?: fileRevision(path)
        return "$path#$revision@$targetPx"
    }

    private fun fileRevision(path: String): Long =
        File(path).takeIf { it.exists() }?.lastModified() ?: 0L

    private fun decodeSampled(path: String, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, targetPx)
                inPreferredConfig = if (PreviewImageSizer.useHighColorDepth(targetPx)) {
                    Bitmap.Config.ARGB_8888
                } else {
                    Bitmap.Config.RGB_565
                }
            },
        )
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sampleSize = 1
        var w = width
        var h = height
        while (w > maxDimension || h > maxDimension) {
            w /= 2; h /= 2; sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }
}
