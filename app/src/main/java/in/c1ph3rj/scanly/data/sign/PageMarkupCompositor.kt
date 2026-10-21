package `in`.c1ph3rj.scanly.data.sign

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import `in`.c1ph3rj.scanly.domain.model.SignMark
import `in`.c1ph3rj.scanly.domain.model.SignMarkStyle
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Burns sign marks into a page image so the result matches what the PDF stamper writes: same
 * baseline, font-size and tick rules from [SignMarkStyle].
 */
@Singleton
class PageMarkupCompositor @Inject constructor() {

    /**
     * Returns a new ARGB_8888 bitmap = [base] with [marks] drawn on top. Box fractions map to the
     * base bitmap size (page as displayed). [base] is not modified or recycled.
     * Throws IllegalStateException with a user-facing message if a signature image is missing.
     */
    fun composite(base: Bitmap, marks: List<SignMark>): Bitmap {
        val output = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        marks.forEach { mark ->
            val left = mark.box.left * output.width
            val top = mark.box.top * output.height
            val boxWidth = mark.box.width * output.width
            val boxHeight = mark.box.height * output.height
            if (boxWidth <= 0f || boxHeight <= 0f) return@forEach
            val rect = RectF(left, top, left + boxWidth, top + boxHeight)
            when (mark) {
                is SignMark.Image -> drawImage(canvas, mark, rect)
                is SignMark.Text -> drawText(canvas, mark.text, mark, rect)
                is SignMark.Date -> drawText(canvas, mark.text, mark, rect)
                is SignMark.Checkbox -> drawCheck(canvas, mark, rect)
            }
        }
        return output
    }

    private fun drawImage(canvas: Canvas, mark: SignMark.Image, rect: RectF) {
        val maxEdge = (maxOf(rect.width(), rect.height()).toInt() * 2).coerceIn(1, 2400)
        val mask = SignInkBitmaps.decodeMask(mark.imagePath, maxEdge = maxEdge)
            ?: error("A signature image is missing. Open Sign and place it again.")
        val tinted = try {
            SignInkBitmaps.tinted(mask, mark.ink)
        } finally {
            mask.recycle()
        }
        try {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            canvas.drawBitmap(tinted, null, fitInside(rect, tinted.width.toFloat() / tinted.height), paint)
        } finally {
            tinted.recycle()
        }
    }

    /** Largest rect of [aspect] (width / height) centred in [rect], like the editor's ContentScale.Fit. */
    private fun fitInside(rect: RectF, aspect: Float): RectF {
        if (aspect <= 0f || !aspect.isFinite()) return rect
        val width = minOf(rect.width(), rect.height() * aspect)
        val height = width / aspect
        return RectF(
            rect.centerX() - width / 2f,
            rect.centerY() - height / 2f,
            rect.centerX() + width / 2f,
            rect.centerY() + height / 2f,
        )
    }

    private fun drawText(canvas: Canvas, text: String, mark: SignMark, rect: RectF) {
        if (text.isEmpty()) return
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = mark.ink.argb.toInt()
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            textSize = MeasureSize
        }
        // Measure at a large size and scale down for precision, giving the width at size 1.
        val widthAtSizeOne = paint.measureText(text) / MeasureSize
        val fontSize = SignMarkStyle.fontSizeFor(rect.width(), rect.height(), widthAtSizeOne)
        paint.textSize = fontSize
        val baseline = rect.top + rect.height() * SignMarkStyle.TextBaselineFraction
        canvas.drawText(text, rect.left, baseline, paint)
    }

    private fun drawCheck(canvas: Canvas, mark: SignMark.Checkbox, rect: RectF) {
        val side = minOf(rect.width(), rect.height())
        val border = SignMarkStyle.CheckboxBorderFraction * side
        val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = border
            color = mark.ink.argb.toInt()
        }
        val inset = RectF(rect).apply { inset(border / 2f, border / 2f) }
        val radius = SignMarkStyle.CheckboxCornerFraction * side
        canvas.drawRoundRect(inset, radius, radius, boxPaint)
        if (!mark.checked) return
        val path = Path()
        SignMarkStyle.CheckPoints.forEachIndexed { index, (fx, fy) ->
            val x = rect.left + fx * rect.width()
            val y = rect.top + fy * rect.height()
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = SignMarkStyle.CheckStrokeFraction * side
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
            color = mark.ink.argb.toInt()
        }
        canvas.drawPath(path, paint)
    }

    private companion object {
        const val MeasureSize = 100f
    }
}
