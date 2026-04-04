package `in`.c1ph3rj.scanly.core.editing

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint

enum class CropHandle {
    TOP_LEFT,
    TOP_RIGHT,
    BOTTOM_RIGHT,
    BOTTOM_LEFT,
}

object CropQuadEditor {
    fun defaultQuad(inset: Float = 0.08f): DocumentCornerQuad = DocumentCornerQuad(
        topLeft = NormalizedPoint(inset, inset),
        topRight = NormalizedPoint(1f - inset, inset),
        bottomRight = NormalizedPoint(1f - inset, 1f - inset),
        bottomLeft = NormalizedPoint(inset, 1f - inset),
    )

    fun moveHandle(
        quad: DocumentCornerQuad,
        handle: CropHandle,
        target: NormalizedPoint,
    ): DocumentCornerQuad {
        val clampedTarget = target.clampToBounds()
        val directCandidate = quad.withHandle(handle, clampedTarget)
        if (directCandidate.isValid(minArea = 0.005f, minDistance = 0.015f)) {
            return directCandidate
        }

        val originPoint = quad.handlePoint(handle)
        var low = 0f
        var high = 1f
        var bestQuad = quad

        repeat(10) {
            val progress = (low + high) / 2f
            val candidatePoint = originPoint.interpolateTo(clampedTarget, progress)
            val candidateQuad = quad.withHandle(handle, candidatePoint)
            if (candidateQuad.isValid(minArea = 0.005f, minDistance = 0.015f)) {
                bestQuad = candidateQuad
                low = progress
            } else {
                high = progress
            }
        }

        return bestQuad
    }

    fun rotateClockwise(quad: DocumentCornerQuad): DocumentCornerQuad = DocumentCornerQuad(
        topLeft = rotateClockwise(quad.bottomLeft),
        topRight = rotateClockwise(quad.topLeft),
        bottomRight = rotateClockwise(quad.topRight),
        bottomLeft = rotateClockwise(quad.bottomRight),
    )

    fun rotateCounterClockwise(quad: DocumentCornerQuad): DocumentCornerQuad = DocumentCornerQuad(
        topLeft = rotateCounterClockwise(quad.topRight),
        topRight = rotateCounterClockwise(quad.bottomRight),
        bottomRight = rotateCounterClockwise(quad.bottomLeft),
        bottomLeft = rotateCounterClockwise(quad.topLeft),
    )

    private fun rotateClockwise(point: NormalizedPoint): NormalizedPoint = NormalizedPoint(
        x = 1f - point.y,
        y = point.x,
    )

    private fun rotateCounterClockwise(point: NormalizedPoint): NormalizedPoint = NormalizedPoint(
        x = point.y,
        y = 1f - point.x,
    )

    private fun DocumentCornerQuad.withHandle(
        handle: CropHandle,
        point: NormalizedPoint,
    ): DocumentCornerQuad = when (handle) {
        CropHandle.TOP_LEFT -> copy(topLeft = point)
        CropHandle.TOP_RIGHT -> copy(topRight = point)
        CropHandle.BOTTOM_RIGHT -> copy(bottomRight = point)
        CropHandle.BOTTOM_LEFT -> copy(bottomLeft = point)
    }

    private fun DocumentCornerQuad.handlePoint(handle: CropHandle): NormalizedPoint = when (handle) {
        CropHandle.TOP_LEFT -> topLeft
        CropHandle.TOP_RIGHT -> topRight
        CropHandle.BOTTOM_RIGHT -> bottomRight
        CropHandle.BOTTOM_LEFT -> bottomLeft
    }

    private fun NormalizedPoint.interpolateTo(
        target: NormalizedPoint,
        progress: Float,
    ): NormalizedPoint = NormalizedPoint(
        x = x + ((target.x - x) * progress),
        y = y + ((target.y - y) * progress),
    ).clampToBounds()

    private fun NormalizedPoint.clampToBounds(): NormalizedPoint = NormalizedPoint(
        x = x.coerceIn(0f, 1f),
        y = y.coerceIn(0f, 1f),
    )
}
