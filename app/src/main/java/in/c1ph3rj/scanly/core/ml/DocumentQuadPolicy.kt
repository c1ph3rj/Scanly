package `in`.c1ph3rj.scanly.core.ml

import kotlin.math.hypot

object DocumentQuadPolicy {
    private const val MIN_AREA = 0.12f
    private const val MAX_AREA = 0.92f
    private const val MIN_ASPECT_RATIO = 0.35f
    private const val MAX_ASPECT_RATIO = 1.90f
    private const val MIN_FRAME_MARGIN = 0.0125f
    private const val MIN_GEOMETRY_QUALITY = 0.58f

    fun isCaptureReady(quad: DocumentCornerQuad): Boolean {
        val area = quad.area()
        val aspectRatio = quad.estimatedAspectRatio()
        return quad.isValid() &&
            quad.isConvex() &&
            quad.hasFrameMargin() &&
            area in MIN_AREA..MAX_AREA &&
            aspectRatio in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO &&
            qualityScore(quad) >= MIN_GEOMETRY_QUALITY
    }

    /**
     * Rotation-independent quality estimate. A good document quad has similarly
     * sized opposite edges and diagonals, while still allowing normal perspective.
     */
    fun qualityScore(quad: DocumentCornerQuad): Float {
        if (!quad.isValid() || !quad.isConvex()) return 0f
        val top = distance(quad.topLeft, quad.topRight)
        val right = distance(quad.topRight, quad.bottomRight)
        val bottom = distance(quad.bottomLeft, quad.bottomRight)
        val left = distance(quad.topLeft, quad.bottomLeft)
        val firstDiagonal = distance(quad.topLeft, quad.bottomRight)
        val secondDiagonal = distance(quad.topRight, quad.bottomLeft)
        val oppositeEdgeBalance = (balance(top, bottom) + balance(left, right)) / 2f
        val diagonalBalance = balance(firstDiagonal, secondDiagonal)
        val minimumCornerQuality = minimumCornerSine(quad)
        return (
            (oppositeEdgeBalance * 0.50f) +
                (diagonalBalance * 0.25f) +
                (minimumCornerQuality * 0.25f)
            ).coerceIn(0f, 1f)
    }

    fun selectionScore(result: CornerDetectionResult): Float {
        val quad = result.quad ?: return 0f
        return ((qualityScore(quad) * 0.78f) + (result.confidence.coerceIn(0f, 1f) * 0.22f))
            .coerceIn(0f, 1f)
    }

    private fun DocumentCornerQuad.hasFrameMargin(): Boolean = orderedPoints().all { (_, point) ->
        point.x in MIN_FRAME_MARGIN..(1f - MIN_FRAME_MARGIN) &&
            point.y in MIN_FRAME_MARGIN..(1f - MIN_FRAME_MARGIN)
    }

    private fun minimumCornerSine(quad: DocumentCornerQuad): Float {
        val points = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        return points.indices.minOf { index ->
            val previous = points[(index + points.size - 1) % points.size]
            val current = points[index]
            val next = points[(index + 1) % points.size]
            val firstX = previous.x - current.x
            val firstY = previous.y - current.y
            val secondX = next.x - current.x
            val secondY = next.y - current.y
            val denominator = hypot(firstX, firstY) * hypot(secondX, secondY)
            if (denominator <= 0f) 0f else kotlin.math.abs((firstX * secondY) - (firstY * secondX)) / denominator
        }.coerceIn(0f, 1f)
    }

    private fun balance(first: Float, second: Float): Float {
        val largest = maxOf(first, second)
        return if (largest <= 0f) 0f else minOf(first, second) / largest
    }

    private fun distance(first: NormalizedPoint, second: NormalizedPoint): Float =
        hypot(first.x - second.x, first.y - second.y)
}
