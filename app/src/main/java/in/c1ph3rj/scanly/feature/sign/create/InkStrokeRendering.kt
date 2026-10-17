package `in`.c1ph3rj.scanly.feature.sign.create

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import `in`.c1ph3rj.scanly.core.editing.InkStroke
import `in`.c1ph3rj.scanly.core.editing.InkStrokeBuilder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Padding around the ink in the exported mask, as a fraction of the long side. */
private const val MaskPaddingFraction = 0.06f

/** Minimum padding around the ink in the exported mask, in pixels. */
private const val MaskMinPaddingPx = 8f

/** The mask never scales a drawing up by more than this factor. */
private const val MaskMaxUpscale = 2f

/**
 * Draws [strokes] on the current canvas. Each stroke is built into smoothed samples and drawn as
 * round-capped segments whose width follows the sample widths. Single-sample strokes become dots.
 */
fun DrawScope.drawInkStrokes(
    strokes: List<InkStroke>,
    style: InkStrokeBuilder.Style,
    color: Color,
) {
    for (stroke in strokes) {
        val samples = InkStrokeBuilder.samples(stroke, style)
        if (samples.isEmpty()) continue
        if (samples.size == 1) {
            val dot = samples[0]
            drawCircle(color = color, radius = dot.width / 2f, center = Offset(dot.x, dot.y))
            continue
        }
        for (index in 1 until samples.size) {
            val from = samples[index - 1]
            val to = samples[index]
            drawLine(
                color = color,
                start = Offset(from.x, from.y),
                end = Offset(to.x, to.y),
                strokeWidth = (from.width + to.width) / 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

/**
 * Renders [strokes] into a transparent ARGB_8888 ink mask: black ink, alpha = coverage, cropped
 * to the ink with a small margin. The long edge is at most [maxEdge] pixels and the drawing is
 * never scaled up beyond [MaskMaxUpscale]. Returns null when there is no ink to draw.
 */
fun renderInkMask(
    strokes: List<InkStroke>,
    style: InkStrokeBuilder.Style,
    maxEdge: Int = 1200,
): Bitmap? {
    val bounds = InkStrokeBuilder.bounds(strokes, style) ?: return null
    val pad = max(MaskMinPaddingPx, max(bounds.width, bounds.height) * MaskPaddingFraction)
    val contentWidth = bounds.width + pad * 2f
    val contentHeight = bounds.height + pad * 2f
    val contentLongSide = max(contentWidth, contentHeight)
    if (contentLongSide <= 0f) return null

    val scale = min(maxEdge.toFloat() / contentLongSide, MaskMaxUpscale)
    val bitmapWidth = ceil(contentWidth * scale).toInt().coerceAtLeast(1)
    val bitmapHeight = ceil(contentHeight * scale).toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(bitmapWidth, bitmapHeight, Bitmap.Config.ARGB_8888)

    val canvas = Canvas(bitmap)
    canvas.scale(scale, scale)
    canvas.translate(pad - bounds.left, pad - bounds.top)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (stroke in strokes) {
        val samples = InkStrokeBuilder.samples(stroke, style)
        if (samples.isEmpty()) continue
        if (samples.size == 1) {
            val dot = samples[0]
            paint.style = Paint.Style.FILL
            canvas.drawCircle(dot.x, dot.y, dot.width / 2f, paint)
            continue
        }
        paint.style = Paint.Style.STROKE
        for (index in 1 until samples.size) {
            val from = samples[index - 1]
            val to = samples[index]
            paint.strokeWidth = (from.width + to.width) / 2f
            canvas.drawLine(from.x, from.y, to.x, to.y, paint)
        }
    }
    return bitmap
}
