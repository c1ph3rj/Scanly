package `in`.c1ph3rj.scanly.core.processing

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad

/**
 * Perspective-corrects a source bitmap into a rectangular document view using a
 * normalized four-point crop quad (same geometry as capture reprocess).
 */
object PerspectiveBitmapTransform {
    fun correct(
        sourceBitmap: Bitmap,
        quad: DocumentCornerQuad,
    ): Bitmap {
        val outputSize = PerspectiveQuadMath.outputSize(
            quad = quad,
            sourceWidth = sourceBitmap.width,
            sourceHeight = sourceBitmap.height,
        )
        val destinationBitmap = Bitmap.createBitmap(
            outputSize.width,
            outputSize.height,
            Bitmap.Config.ARGB_8888,
        )
        val matrix = Matrix()
        matrix.setPolyToPoly(
            PerspectiveQuadMath.sourcePoints(quad, sourceBitmap.width, sourceBitmap.height),
            0,
            PerspectiveQuadMath.destinationPoints(outputSize.width - 1, outputSize.height - 1),
            0,
            4,
        )

        val canvas = Canvas(destinationBitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(
            sourceBitmap,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG),
        )
        return destinationBitmap
    }
}
