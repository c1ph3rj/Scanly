package `in`.c1ph3rj.scanly.core.ml

import kotlin.math.hypot

object DocumentQuadPolicy {
    private const val MIN_AREA = 0.12f
    /** Loose near-full-frame boxes usually overshoot the page onto desks/keyboards. */
    private const val MAX_AREA = 0.82f
    /**
     * Live width/height band, measured in pixel-proportional space. The old normalized band
     * 0.35..1.90 meant true 0.26..1.43 in a 3:4 frame and 0.47..2.53 in a 4:3 frame; this is
     * their union, so landscape ID cards (~1.59) now pass in portrait frames too.
     */
    private const val MIN_ASPECT_RATIO = 0.25f
    private const val MAX_ASPECT_RATIO = 2.6f
    private const val MIN_FRAME_MARGIN = 0.0125f
    private const val MIN_GEOMETRY_QUALITY = 0.58f
    /** Soft preference: mid-size pages score higher than huge loose overlays. */
    private const val PREFERRED_AREA_SOFT_MAX = 0.58f
    private const val AREA_PENALTY_START = 0.62f

    // Still-image / import processing: accept near-full-frame gallery scans, ID cards,
    // and other wide documents that live capture would reject. No frame margin: the decoders
    // clamp corners to [0, 1], so a page touching the image border has corners exactly on it.
    private const val STILL_MIN_AREA = 0.03f
    private const val STILL_MAX_AREA = 0.999f
    private const val STILL_MIN_GEOMETRY_QUALITY = 0.28f
    /**
     * Orientation-agnostic long/short limit in pixel space. The old normalized limit 3.2 meant
     * up to ~4.27 in 3:4 / 4:3 frames (cards under perspective, receipts); 4.3 keeps all of it.
     */
    private const val STILL_MAX_LONG_SHORT_RATIO = 4.3f

    /**
     * @param frameAspectRatio width / height of the image the quad is normalized against
     *   (e.g. 0.75 for a 480x640 portrait analysis frame). Defaults to 1 (square space).
     */
    fun isCaptureReady(quad: DocumentCornerQuad, frameAspectRatio: Float = 1f): Boolean {
        val area = quad.area()
        val aspectRatio = quad.estimatedAspectRatio(frameAspectRatio)
        return quad.isValid() &&
            quad.isConvex() &&
            hasFrameMargin(quad, MIN_FRAME_MARGIN) &&
            area in MIN_AREA..MAX_AREA &&
            aspectRatio in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO &&
            qualityScore(quad, frameAspectRatio) >= MIN_GEOMETRY_QUALITY
    }

    /**
     * Offline still-image readiness used for capture finalize and gallery import.
     * Mirrors what Model Benchmark shows (raw model quads) more closely than live
     * capture rules — wide ID/RC cards and border-touching photos stay usable.
     */
    fun isStillProcessReady(quad: DocumentCornerQuad, frameAspectRatio: Float = 1f): Boolean {
        val area = quad.area()
        // Normalize so landscape cards (~1.6) and tall pages (~0.6) share one band.
        val longShortRatio = normalizedAspectRatio(quad.estimatedAspectRatio(frameAspectRatio))
        return quad.isValid() &&
            quad.isConvex() &&
            area in STILL_MIN_AREA..STILL_MAX_AREA &&
            longShortRatio in 1f..STILL_MAX_LONG_SHORT_RATIO &&
            qualityScore(quad, frameAspectRatio) >= STILL_MIN_GEOMETRY_QUALITY
    }

    fun isReady(
        quad: DocumentCornerQuad,
        readiness: QuadReadiness,
        frameAspectRatio: Float = 1f,
    ): Boolean = when (readiness) {
        QuadReadiness.LIVE_CAPTURE -> isCaptureReady(quad, frameAspectRatio)
        QuadReadiness.STILL_PROCESS -> isStillProcessReady(quad, frameAspectRatio)
    }

    /**
     * Rotation-independent quality estimate. A good document quad has similarly
     * sized opposite edges and diagonals, while still allowing normal perspective.
     * Lengths and angles are measured with x scaled by [frameAspectRatio] so a rotated
     * rectangle in a non-square frame is not mistaken for a parallelogram.
     */
    fun qualityScore(quad: DocumentCornerQuad, frameAspectRatio: Float = 1f): Float {
        if (!quad.isValid() || !quad.isConvex()) return 0f
        val xScale = sanitizeFrameAspectRatio(frameAspectRatio)
        val top = distance(quad.topLeft, quad.topRight, xScale)
        val right = distance(quad.topRight, quad.bottomRight, xScale)
        val bottom = distance(quad.bottomLeft, quad.bottomRight, xScale)
        val left = distance(quad.topLeft, quad.bottomLeft, xScale)
        val firstDiagonal = distance(quad.topLeft, quad.bottomRight, xScale)
        val secondDiagonal = distance(quad.topRight, quad.bottomLeft, xScale)
        val oppositeEdgeBalance = (balance(top, bottom) + balance(left, right)) / 2f
        val diagonalBalance = balance(firstDiagonal, secondDiagonal)
        val minimumCornerQuality = minimumCornerSine(quad, xScale)
        return (
            (oppositeEdgeBalance * 0.50f) +
                (diagonalBalance * 0.25f) +
                (minimumCornerQuality * 0.25f)
            ).coerceIn(0f, 1f)
    }

    fun selectionScore(result: CornerDetectionResult): Float {
        val quad = result.quad ?: return 0f
        val quality = qualityScore(quad, result.frameAspectRatio)
        val confidence = result.confidence.coerceIn(0f, 1f)
        // Prefer quads that still look like a page, not a loose grab of the whole desk.
        val areaFit = areaFitScore(quad.area())
        return (
            (quality * 0.68f) +
                (confidence * 0.18f) +
                (areaFit * 0.14f)
            ).coerceIn(0f, 1f)
    }

    /**
     * 1 for typical page sizes; soft falloff as the box fills most of the frame
     * (common failure mode when the detector includes keyboard/background).
     */
    internal fun areaFitScore(area: Float): Float = when {
        area <= PREFERRED_AREA_SOFT_MAX -> 1f
        area <= AREA_PENALTY_START -> 1f - ((area - PREFERRED_AREA_SOFT_MAX) /
            (AREA_PENALTY_START - PREFERRED_AREA_SOFT_MAX)) * 0.08f
        area <= MAX_AREA -> {
            val t = ((area - AREA_PENALTY_START) / (MAX_AREA - AREA_PENALTY_START)).coerceIn(0f, 1f)
            0.92f - t * 0.42f
        }
        else -> 0.35f
    }

    private fun hasFrameMargin(quad: DocumentCornerQuad, margin: Float): Boolean =
        quad.orderedPoints().all { (_, point) ->
            point.x in margin..(1f - margin) &&
                point.y in margin..(1f - margin)
        }

    private fun minimumCornerSine(quad: DocumentCornerQuad, xScale: Float): Float {
        val points = listOf(quad.topLeft, quad.topRight, quad.bottomRight, quad.bottomLeft)
        return points.indices.minOf { index ->
            val previous = points[(index + points.size - 1) % points.size]
            val current = points[index]
            val next = points[(index + 1) % points.size]
            val firstX = (previous.x - current.x) * xScale
            val firstY = previous.y - current.y
            val secondX = (next.x - current.x) * xScale
            val secondY = next.y - current.y
            val denominator = hypot(firstX, firstY) * hypot(secondX, secondY)
            if (denominator <= 0f) 0f else kotlin.math.abs((firstX * secondY) - (firstY * secondX)) / denominator
        }.coerceIn(0f, 1f)
    }

    private fun balance(first: Float, second: Float): Float {
        val largest = maxOf(first, second)
        return if (largest <= 0f) 0f else minOf(first, second) / largest
    }

    private fun distance(first: NormalizedPoint, second: NormalizedPoint, xScale: Float): Float =
        hypot((first.x - second.x) * xScale, first.y - second.y)

    /** Treat portrait/landscape the same (width/height or height/width). */
    private fun normalizedAspectRatio(ratio: Float): Float {
        if (ratio <= 0f) return 0f
        return if (ratio >= 1f) ratio else 1f / ratio
    }
}

/** How strict document-quad acceptance is for a given detection path. */
enum class QuadReadiness {
    /** Live camera overlay / auto-capture — tighter geometry and frame margins. */
    LIVE_CAPTURE,
    /** Capture finalize + gallery import — keep usable near-full-frame warps. */
    STILL_PROCESS,
}
