package `in`.c1ph3rj.scanly.core.processing

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint
import kotlin.math.hypot
import kotlin.math.roundToInt

object PerspectiveQuadMath {
    fun sourcePoints(
        quad: DocumentCornerQuad,
        width: Int,
        height: Int,
    ): FloatArray = floatArrayOf(
        quad.topLeft.toPixelX(width), quad.topLeft.toPixelY(height),
        quad.topRight.toPixelX(width), quad.topRight.toPixelY(height),
        quad.bottomRight.toPixelX(width), quad.bottomRight.toPixelY(height),
        quad.bottomLeft.toPixelX(width), quad.bottomLeft.toPixelY(height),
    )

    fun destinationPoints(
        width: Int,
        height: Int,
    ): FloatArray = floatArrayOf(
        0f, 0f,
        width.toFloat(), 0f,
        width.toFloat(), height.toFloat(),
        0f, height.toFloat(),
    )

    fun outputSize(
        quad: DocumentCornerQuad,
        sourceWidth: Int,
        sourceHeight: Int,
        minDimension: Int = 720,
        maxDimension: Int = 2_400,
    ): PerspectiveOutputSize {
        val topWidth = distance(quad.topLeft, quad.topRight, sourceWidth, sourceHeight)
        val bottomWidth = distance(quad.bottomLeft, quad.bottomRight, sourceWidth, sourceHeight)
        val leftHeight = distance(quad.topLeft, quad.bottomLeft, sourceWidth, sourceHeight)
        val rightHeight = distance(quad.topRight, quad.bottomRight, sourceWidth, sourceHeight)

        val rawWidth = maxOf(topWidth, bottomWidth).roundToInt().coerceAtLeast(1)
        val rawHeight = maxOf(leftHeight, rightHeight).roundToInt().coerceAtLeast(1)
        val longestEdge = maxOf(rawWidth, rawHeight).coerceAtLeast(1)
        val scale = when {
            longestEdge > maxDimension -> maxDimension / longestEdge.toFloat()
            longestEdge < minDimension -> minDimension / longestEdge.toFloat()
            else -> 1f
        }

        return PerspectiveOutputSize(
            width = (rawWidth * scale).roundToInt().coerceAtLeast(1),
            height = (rawHeight * scale).roundToInt().coerceAtLeast(1),
        )
    }

    private fun distance(
        first: NormalizedPoint,
        second: NormalizedPoint,
        sourceWidth: Int,
        sourceHeight: Int,
    ): Float = hypot(
        (first.x - second.x) * sourceWidth,
        (first.y - second.y) * sourceHeight,
    )

    private fun NormalizedPoint.toPixelX(width: Int): Float = x * width

    private fun NormalizedPoint.toPixelY(height: Int): Float = y * height
}

data class PerspectiveOutputSize(
    val width: Int,
    val height: Int,
)
