package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Pulls an oversize document quad inward toward real edges.
 *
 * Corner models often overshoot paper bounds into keyboards / desks. When the
 * model edges do not sit on strong gradients, each side is snapped toward the
 * strongest nearby inward edge so the overlay hugs the page instead of the
 * surrounding surface.
 */
object DocumentQuadTightener {
    private const val MAX_INSET = 0.085f
    private const val EDGE_SAMPLES = 18
    private const val SEARCH_STEPS = 14
    private const val MIN_GRADIENT_GAIN = 0.025f
    /** Skip tightening when the model already sits on strong edges. */
    private const val STRONG_BOUNDARY_SUPPORT = 0.155f
    /** Never pull more than this fraction of the distance to the centroid. */
    private const val MAX_CORNER_PULL_FRACTION = 0.28f

    fun refine(
        bitmap: Bitmap,
        quad: DocumentCornerQuad,
        boundarySupport: Float = 0f,
    ): DocumentCornerQuad {
        if (!quad.isValid() || !quad.isConvex()) return quad
        if (boundarySupport >= STRONG_BOUNDARY_SUPPORT) return quad

        val centroid = centroid(quad)
        val topInset = edgeInset(bitmap, quad.topLeft, quad.topRight, centroid)
        val rightInset = edgeInset(bitmap, quad.topRight, quad.bottomRight, centroid)
        val bottomInset = edgeInset(bitmap, quad.bottomRight, quad.bottomLeft, centroid)
        val leftInset = edgeInset(bitmap, quad.bottomLeft, quad.topLeft, centroid)

        if (topInset + rightInset + bottomInset + leftInset < 0.004f) {
            return quad
        }

        val tightened = DocumentCornerQuad(
            topLeft = pullCorner(quad.topLeft, centroid, (topInset + leftInset) * 0.5f),
            topRight = pullCorner(quad.topRight, centroid, (topInset + rightInset) * 0.5f),
            bottomRight = pullCorner(quad.bottomRight, centroid, (bottomInset + rightInset) * 0.5f),
            bottomLeft = pullCorner(quad.bottomLeft, centroid, (bottomInset + leftInset) * 0.5f),
        )
        return if (tightened.isValid() && tightened.isConvex()) tightened else quad
    }

    private fun edgeInset(
        bitmap: Bitmap,
        start: NormalizedPoint,
        end: NormalizedPoint,
        centroid: NormalizedPoint,
    ): Float {
        val edgeX = end.x - start.x
        val edgeY = end.y - start.y
        val length = hypot(edgeX, edgeY)
        if (length < 1e-4f) return 0f

        var normalX = -edgeY / length
        var normalY = edgeX / length
        val midX = (start.x + end.x) * 0.5f
        val midY = (start.y + end.y) * 0.5f
        // Ensure normal points toward the document interior.
        if (normalX * (centroid.x - midX) + normalY * (centroid.y - midY) < 0f) {
            normalX = -normalX
            normalY = -normalY
        }

        val offsets = ArrayList<Float>(EDGE_SAMPLES)
        for (index in 1..EDGE_SAMPLES) {
            val t = index / (EDGE_SAMPLES + 1f)
            val sampleX = start.x + edgeX * t
            val sampleY = start.y + edgeY * t
            val baseline = localGradient(bitmap, sampleX, sampleY)
            var bestOffset = 0f
            var bestGradient = baseline
            for (step in 1..SEARCH_STEPS) {
                val offset = MAX_INSET * step / SEARCH_STEPS
                val gradient = localGradient(
                    bitmap,
                    sampleX + normalX * offset,
                    sampleY + normalY * offset,
                )
                if (gradient > bestGradient + MIN_GRADIENT_GAIN) {
                    bestGradient = gradient
                    bestOffset = offset
                }
            }
            offsets += bestOffset
        }
        offsets.sort()
        return offsets[offsets.size / 2]
    }

    private fun pullCorner(
        point: NormalizedPoint,
        centroid: NormalizedPoint,
        amount: Float,
    ): NormalizedPoint {
        if (amount <= 0f) return point
        val dx = centroid.x - point.x
        val dy = centroid.y - point.y
        val distance = hypot(dx, dy).coerceAtLeast(1e-4f)
        val capped = amount.coerceAtMost(distance * MAX_CORNER_PULL_FRACTION)
        return NormalizedPoint(
            x = (point.x + dx / distance * capped).coerceIn(0f, 1f),
            y = (point.y + dy / distance * capped).coerceIn(0f, 1f),
        )
    }

    private fun centroid(quad: DocumentCornerQuad): NormalizedPoint = NormalizedPoint(
        x = (quad.topLeft.x + quad.topRight.x + quad.bottomRight.x + quad.bottomLeft.x) * 0.25f,
        y = (quad.topLeft.y + quad.topRight.y + quad.bottomRight.y + quad.bottomLeft.y) * 0.25f,
    )

    private fun localGradient(bitmap: Bitmap, x: Float, y: Float): Float {
        val left = luminance(bitmap, x - 1.5f, y)
        val right = luminance(bitmap, x + 1.5f, y)
        val up = luminance(bitmap, x, y - 1.5f)
        val down = luminance(bitmap, x, y + 1.5f)
        if (left == null || right == null || up == null || down == null) return 0f
        val gx = (right - left) / 255f
        val gy = (down - up) / 255f
        return hypot(gx, gy)
    }

    private fun luminance(bitmap: Bitmap, x: Float, y: Float): Float? {
        val px = (x * (bitmap.width - 1)).roundToInt()
        val py = (y * (bitmap.height - 1)).roundToInt()
        if (px !in 0 until bitmap.width || py !in 0 until bitmap.height) return null
        val color = bitmap.getPixel(px, py)
        return (0.2126f * Color.red(color)) +
            (0.7152f * Color.green(color)) +
            (0.0722f * Color.blue(color))
    }
}
