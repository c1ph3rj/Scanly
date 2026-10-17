package `in`.c1ph3rj.scanly.data.sign

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import `in`.c1ph3rj.scanly.domain.model.SignInk

/** Shared bitmap helpers for rendering signature ink masks in a chosen [SignInk]. */
internal object SignInkBitmaps {

    /** Decodes an ink-mask PNG, downsampled so its long edge is at most about [maxEdge]. */
    fun decodeMask(path: String, maxEdge: Int = 1600): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sample
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    /** A copy of [mask] whose visible pixels are [ink]; alpha is preserved. [mask] is not recycled. */
    fun tinted(mask: Bitmap, ink: SignInk): Bitmap {
        val output = Bitmap.createBitmap(mask.width, mask.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = PorterDuffColorFilter(ink.argb.toInt(), PorterDuff.Mode.SRC_IN)
        }
        Canvas(output).drawBitmap(mask, 0f, 0f, paint)
        return output
    }
}
