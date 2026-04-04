package `in`.c1ph3rj.scanly.core.ml

import kotlin.math.abs
import kotlin.math.hypot

data class DocumentCornerQuad(
    val topLeft: NormalizedPoint,
    val topRight: NormalizedPoint,
    val bottomRight: NormalizedPoint,
    val bottomLeft: NormalizedPoint,
) {
    fun orderedPoints(): List<Pair<DocumentCornerLabel, NormalizedPoint>> = listOf(
        DocumentCornerLabel.TL to topLeft,
        DocumentCornerLabel.TR to topRight,
        DocumentCornerLabel.BR to bottomRight,
        DocumentCornerLabel.BL to bottomLeft,
    )

    fun isNormalized(): Boolean = orderedPoints().all { (_, point) -> point.isNormalized() }

    fun area(): Float = abs(signedArea())

    fun hasExpectedScreenClockwiseOrder(): Boolean = signedArea() > 0f

    fun hasDistinctPoints(minDistance: Float = MIN_POINT_DISTANCE): Boolean {
        val points = orderedPoints().map { it.second }
        val minDistanceSquared = minDistance * minDistance
        return points.indices.all { left ->
            (left + 1 until points.size).all { right ->
                squaredDistance(points[left], points[right]) > minDistanceSquared
            }
        }
    }

    fun isValid(
        minArea: Float = MIN_AREA,
        minDistance: Float = MIN_POINT_DISTANCE,
    ): Boolean {
        return isNormalized() &&
            hasExpectedScreenClockwiseOrder() &&
            hasDistinctPoints(minDistance = minDistance) &&
            area() >= minArea
    }

    fun meanCornerDistance(other: DocumentCornerQuad): Float = orderedPoints()
        .map { it.second }
        .zip(other.orderedPoints().map { it.second })
        .map { (first, second) ->
            hypot(first.x - second.x, first.y - second.y)
        }
        .average()
        .toFloat()

    fun estimatedAspectRatio(): Float {
        val topWidth = distance(topLeft, topRight)
        val bottomWidth = distance(bottomLeft, bottomRight)
        val leftHeight = distance(topLeft, bottomLeft)
        val rightHeight = distance(topRight, bottomRight)
        val averageWidth = (topWidth + bottomWidth) / 2f
        val averageHeight = (leftHeight + rightHeight) / 2f
        if (averageHeight == 0f) {
            return 0f
        }
        return averageWidth / averageHeight
    }

    private fun signedArea(): Float {
        val points = orderedPoints().map { it.second }
        var sum = 0f
        for (index in points.indices) {
            val current = points[index]
            val next = points[(index + 1) % points.size]
            sum += (current.x * next.y) - (current.y * next.x)
        }
        return sum / 2f
    }

    private fun squaredDistance(first: NormalizedPoint, second: NormalizedPoint): Float {
        val deltaX = first.x - second.x
        val deltaY = first.y - second.y
        return (deltaX * deltaX) + (deltaY * deltaY)
    }

    private fun distance(first: NormalizedPoint, second: NormalizedPoint): Float =
        hypot(first.x - second.x, first.y - second.y)

    companion object {
        const val EXPECTED_POINT_COUNT = 4
        const val MIN_AREA = 0.01f
        const val MIN_POINT_DISTANCE = 0.02f
    }
}
