package `in`.c1ph3rj.scanly.core.ml

import `in`.c1ph3rj.scanly.domain.model.ScanMode
import kotlin.math.hypot

object DocumentQuadPolicy {
    private const val MIN_AREA = 0.12f
    /** Loose near-full-frame boxes usually overshoot the page onto desks/keyboards. */
    private const val MAX_AREA = 0.82f
    private const val MIN_ASPECT_RATIO = 0.35f
    private const val MAX_ASPECT_RATIO = 1.90f
    private const val MIN_FRAME_MARGIN = 0.0125f
    private const val MIN_GEOMETRY_QUALITY = 0.58f
    /** Soft preference: mid-size pages score higher than huge loose overlays. */
    private const val PREFERRED_AREA_SOFT_MAX = 0.58f
    private const val AREA_PENALTY_START = 0.62f

    // Still-image / import processing: accept near-full-frame gallery scans, ID cards,
    // and other wide documents that live capture would reject.
    private const val STILL_MIN_AREA = 0.03f
    private const val STILL_MAX_AREA = 0.999f
    private const val STILL_MIN_FRAME_MARGIN = 0.0005f
    private const val STILL_MIN_GEOMETRY_QUALITY = 0.28f
    // Credit/RC cards ~1.6; under perspective can measure higher than live max 1.9.
    private const val STILL_MIN_ASPECT_RATIO = 0.22f
    private const val STILL_MAX_ASPECT_RATIO = 3.2f

    fun isCaptureReady(quad: DocumentCornerQuad): Boolean {
        val area = quad.area()
        // Live capture keeps the historical width/height range (portrait A4 ~0.7, etc.).
        val aspectRatio = quad.estimatedAspectRatio()
        return quad.isValid() &&
            quad.isConvex() &&
            hasFrameMargin(quad, MIN_FRAME_MARGIN) &&
            area in MIN_AREA..MAX_AREA &&
            aspectRatio in MIN_ASPECT_RATIO..MAX_ASPECT_RATIO &&
            qualityScore(quad) >= MIN_GEOMETRY_QUALITY
    }

    /**
     * Offline still-image readiness used for capture finalize and gallery import.
     * Mirrors what Model Benchmark shows (raw model quads) more closely than live
     * capture rules — wide ID/RC cards and tight-margin photos stay usable.
     */
    fun isStillProcessReady(quad: DocumentCornerQuad): Boolean {
        val area = quad.area()
        // Normalize so landscape cards (~1.6) and tall pages (~0.6) share one band.
        val aspectRatio = normalizedAspectRatio(quad.estimatedAspectRatio())
        return quad.isValid() &&
            quad.isConvex() &&
            hasFrameMargin(quad, STILL_MIN_FRAME_MARGIN) &&
            area in STILL_MIN_AREA..STILL_MAX_AREA &&
            aspectRatio in STILL_MIN_ASPECT_RATIO..STILL_MAX_ASPECT_RATIO &&
            qualityScore(quad) >= STILL_MIN_GEOMETRY_QUALITY
    }

    fun isReady(
        quad: DocumentCornerQuad,
        readiness: QuadReadiness,
        scanMode: ScanMode = ScanMode.DOCUMENT,
    ): Boolean = when (readiness) {
        QuadReadiness.LIVE_CAPTURE -> when (scanMode) {
            ScanMode.DOCUMENT -> isCaptureReady(quad)
            ScanMode.ID_CARD -> isIdCardCaptureReady(quad)
            ScanMode.BOOK -> isBookSpreadCaptureReady(quad)
        }
        QuadReadiness.STILL_PROCESS -> isStillProcessReady(quad)
    }

    private fun isIdCardCaptureReady(quad: DocumentCornerQuad): Boolean {
        val area = quad.area()
        val aspectRatio = normalizedAspectRatio(quad.estimatedAspectRatio())
        val center = quad.center()
        return quad.isValid() &&
            quad.isConvex() &&
            hasFrameMargin(quad, MIN_FRAME_MARGIN) &&
            area in 0.07f..MAX_AREA &&
            aspectRatio in 1.20f..2.25f &&
            kotlin.math.abs(center.x - 0.5f) <= ID_CARD_CENTER_TOLERANCE &&
            kotlin.math.abs(center.y - 0.5f) <= ID_CARD_CENTER_TOLERANCE &&
            qualityScore(quad) >= 0.52f
    }

    internal fun idCardGuideFitScore(quad: DocumentCornerQuad): Float {
        if (!quad.isValid() || !quad.isConvex()) return 0f
        val center = quad.center()
        val centerDistance = hypot(center.x - 0.5f, center.y - 0.5f)
        val centerFit = (1f - centerDistance / 0.36f).coerceIn(0f, 1f)
        val aspectRatio = normalizedAspectRatio(quad.estimatedAspectRatio())
        val aspectFit = (1f - kotlin.math.abs(aspectRatio - ID_CARD_ASPECT_RATIO) / 0.8f)
            .coerceIn(0f, 1f)
        val geometryFit = qualityScore(quad)
        return (
            centerFit * 0.42f +
                aspectFit * 0.34f +
                geometryFit * 0.24f
            ).coerceIn(0f, 1f)
    }

    private fun isBookSpreadCaptureReady(quad: DocumentCornerQuad): Boolean {
        val area = quad.area()
        val aspectRatio = normalizedAspectRatio(quad.estimatedAspectRatio())
        return quad.isValid() &&
            quad.isConvex() &&
            hasFrameMargin(quad, MIN_FRAME_MARGIN) &&
            area in 0.10f..0.90f &&
            aspectRatio in 1.05f..2.60f &&
            qualityScore(quad) >= 0.50f
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
        val quality = qualityScore(quad)
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

    private fun DocumentCornerQuad.center(): NormalizedPoint = NormalizedPoint(
        x = (topLeft.x + topRight.x + bottomRight.x + bottomLeft.x) / 4f,
        y = (topLeft.y + topRight.y + bottomRight.y + bottomLeft.y) / 4f,
    )

    /** Treat portrait/landscape the same (width/height or height/width). */
    private fun normalizedAspectRatio(ratio: Float): Float {
        if (ratio <= 0f) return 0f
        return if (ratio >= 1f) ratio else 1f / ratio
    }

    private const val ID_CARD_ASPECT_RATIO = 1.586f
    private const val ID_CARD_CENTER_TOLERANCE = 0.18f
}

/** How strict document-quad acceptance is for a given detection path. */
enum class QuadReadiness {
    /** Live camera overlay / auto-capture — tighter geometry and frame margins. */
    LIVE_CAPTURE,
    /** Capture finalize + gallery import — keep usable near-full-frame warps. */
    STILL_PROCESS,
}
