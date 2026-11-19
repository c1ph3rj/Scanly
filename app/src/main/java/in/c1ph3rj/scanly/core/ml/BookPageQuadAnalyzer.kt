package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.hypot

enum class BookPageRefinementKind {
    NONE,
    DOMINANT_PAGE,
    AMBIGUOUS_TWO_PAGES,
}

data class BookPageVisualEvidence(
    val boundarySupport: Float,
    val strongestInteriorFraction: Float,
    val strongestInteriorSupport: Float,
    val medianInteriorSupport: Float,
    /**
     * Share (0..1) of the strongest interior line's samples that see a strong gradient. A real
     * gutter runs the full page height; a photo border or table rule covers only part of it.
     * [UNKNOWN_CONTINUITY] skips the continuity test.
     */
    val strongestInteriorContinuity: Float = UNKNOWN_CONTINUITY,
) {
    companion object {
        const val UNKNOWN_CONTINUITY = -1f
    }
}

data class BookPageRefinement(
    val kind: BookPageRefinementKind,
    val quad: DocumentCornerQuad?,
    val evidence: BookPageVisualEvidence,
)

object BookPageQuadAnalyzer {
    fun analyze(bitmap: Bitmap, quad: DocumentCornerQuad): BookPageRefinement =
        analyze(bitmap.toLumaGrid(), quad)

    /**
     * @param frameAspectRatio width / height of the image [quad] is normalized against;
     *   defaults to the grid's own aspect.
     */
    fun analyze(
        grid: LumaGrid,
        quad: DocumentCornerQuad,
        frameAspectRatio: Float = grid.aspectRatio,
    ): BookPageRefinement {
        val boundarySupport = listOf(
            lineSupport(grid, quad.topLeft, quad.topRight),
            lineSupport(grid, quad.topRight, quad.bottomRight),
            lineSupport(grid, quad.bottomRight, quad.bottomLeft),
            lineSupport(grid, quad.bottomLeft, quad.topLeft),
        ).map { it.mean }.average().toFloat()
        val interior = interiorFractions(grid, quad).map { fraction ->
            val top = quad.topLeft.interpolate(quad.topRight, fraction)
            val bottom = quad.bottomLeft.interpolate(quad.bottomRight, fraction)
            fraction to lineSupport(grid, top, bottom)
        }
        val strongest = interior[plateauCentre(interior.map { it.second.mean })]
        val sortedSupports = interior.map { it.second.mean }.sorted()
        val evidence = BookPageVisualEvidence(
            boundarySupport = boundarySupport,
            strongestInteriorFraction = strongest.first,
            strongestInteriorSupport = strongest.second.mean,
            medianInteriorSupport = sortedSupports[sortedSupports.size / 2],
            strongestInteriorContinuity = strongest.second.continuity,
        )
        return refinementForEvidence(quad, evidence, frameAspectRatio)
    }

    internal fun refinementForEvidence(
        quad: DocumentCornerQuad,
        evidence: BookPageVisualEvidence,
        frameAspectRatio: Float = 1f,
    ): BookPageRefinement {
        val continuityKnown = evidence.strongestInteriorContinuity >= 0f
        val seamIsContinuous = evidence.strongestInteriorSupport >= MIN_SEAM_SUPPORT &&
            evidence.strongestInteriorSupport >= evidence.medianInteriorSupport + MIN_SEAM_PROMINENCE &&
            evidence.strongestInteriorSupport >= evidence.medianInteriorSupport * MIN_SEAM_RATIO &&
            (!continuityKnown || evidence.strongestInteriorContinuity >= MIN_SEAM_CONTINUITY)
        if (!seamIsContinuous) {
            return BookPageRefinement(BookPageRefinementKind.NONE, quad, evidence)
        }

        val fraction = evidence.strongestInteriorFraction
        val topSeam = quad.topLeft.interpolate(quad.topRight, fraction)
        val bottomSeam = quad.bottomLeft.interpolate(quad.bottomRight, fraction)
        val leftPage = DocumentCornerQuad(quad.topLeft, topSeam, bottomSeam, quad.bottomLeft)
        val rightPage = DocumentCornerQuad(topSeam, quad.topRight, quad.bottomRight, bottomSeam)
        val dominantPage = when {
            fraction <= MAX_LEFT_SLIVER_FRACTION -> rightPage
            fraction >= MIN_RIGHT_SLIVER_FRACTION -> leftPage
            else -> null
        }
        if (dominantPage != null && DocumentQuadPolicy.isCaptureReady(dominantPage, frameAspectRatio)) {
            return BookPageRefinement(BookPageRefinementKind.DOMINANT_PAGE, dominantPage, evidence)
        }

        val bothPagesPlausible = DocumentQuadPolicy.isCaptureReady(leftPage, frameAspectRatio) &&
            DocumentQuadPolicy.isCaptureReady(rightPage, frameAspectRatio)
        return if (bothPagesPlausible) {
            BookPageRefinement(BookPageRefinementKind.AMBIGUOUS_TWO_PAGES, null, evidence)
        } else {
            BookPageRefinement(BookPageRefinementKind.NONE, quad, evidence)
        }
    }

    /**
     * Interior line fractions from [MIN_INTERIOR_FRACTION] to [MAX_INTERIOR_FRACTION], spaced so
     * neighbouring search windows (+-[NORMAL_SEARCH_RADIUS] px) overlap on the wider of the top
     * and bottom edges: a gutter anywhere in that range falls inside some window.
     */
    internal fun interiorFractions(grid: LumaGrid, quad: DocumentCornerQuad): List<Float> {
        val topWidth = pixelDistance(grid, quad.topLeft, quad.topRight)
        val bottomWidth = pixelDistance(grid, quad.bottomLeft, quad.bottomRight)
        val widestPx = maxOf(topWidth, bottomWidth, 1f)
        val span = MAX_INTERIOR_FRACTION - MIN_INTERIOR_FRACTION
        val lineCount = ((span * widestPx) / INTERIOR_LINE_SPACING_PX).toInt() + 2
        val count = lineCount.coerceIn(MIN_INTERIOR_LINES, MAX_INTERIOR_LINES)
        return (0 until count).map { index ->
            MIN_INTERIOR_FRACTION + span * index / (count - 1)
        }
    }

    /**
     * Index of the strongest line, centred on the run of neighbours that score almost as high.
     * Overlapping windows see the same gutter from several lines; the first of them sits up to
     * a window off the seam, the middle of the run sits on it.
     */
    private fun plateauCentre(supports: List<Float>): Int {
        val peak = supports.indices.maxBy { supports[it] }
        val floor = supports[peak] * PLATEAU_SHARE
        var first = peak
        while (first > 0 && supports[first - 1] >= floor) first--
        var last = peak
        while (last < supports.lastIndex && supports[last + 1] >= floor) last++
        return (first + last) / 2
    }

    private fun lineSupport(grid: LumaGrid, start: NormalizedPoint, end: NormalizedPoint): LineSupport {
        val startX = grid.toPixelX(start.x)
        val startY = grid.toPixelY(start.y)
        val endX = grid.toPixelX(end.x)
        val endY = grid.toPixelY(end.y)
        val deltaX = endX - startX
        val deltaY = endY - startY
        val length = hypot(deltaX, deltaY)
        if (length < 2f) return LineSupport(0f, 0f)
        val normalX = -deltaY / length
        val normalY = deltaX / length
        val gradients = FloatArray(LINE_SAMPLES)
        for (index in 1..LINE_SAMPLES) {
            val fraction = index / (LINE_SAMPLES + 1f)
            val centerX = startX + (deltaX * fraction)
            val centerY = startY + (deltaY * fraction)
            var strongestGradient = 0f
            for (offset in -NORMAL_SEARCH_RADIUS..NORMAL_SEARCH_RADIUS) {
                val first = grid.nearest(
                    centerX + (normalX * (offset - 1)),
                    centerY + (normalY * (offset - 1)),
                )
                val second = grid.nearest(
                    centerX + (normalX * (offset + 1)),
                    centerY + (normalY * (offset + 1)),
                )
                if (first != null && second != null) {
                    strongestGradient = maxOf(strongestGradient, abs(first - second) / 255f)
                }
            }
            gradients[index - 1] = strongestGradient
        }
        val mean = gradients.average().toFloat()
        // A sample counts toward continuity when it is clearly part of the line, not just noise.
        val sampleThreshold = maxOf(MIN_CONTINUITY_GRADIENT, mean * CONTINUITY_MEAN_SHARE)
        val continuity = gradients.count { it >= sampleThreshold }.toFloat() / gradients.size
        return LineSupport(mean, continuity)
    }

    private fun pixelDistance(grid: LumaGrid, first: NormalizedPoint, second: NormalizedPoint): Float =
        hypot(grid.toPixelX(first.x) - grid.toPixelX(second.x), grid.toPixelY(first.y) - grid.toPixelY(second.y))

    private fun NormalizedPoint.interpolate(other: NormalizedPoint, fraction: Float): NormalizedPoint =
        NormalizedPoint(
            x = x + ((other.x - x) * fraction),
            y = y + ((other.y - y) * fraction),
        )

    private class LineSupport(val mean: Float, val continuity: Float)

    private const val LINE_SAMPLES = 28
    private const val NORMAL_SEARCH_RADIUS = 5
    private const val MIN_INTERIOR_FRACTION = 0.10f
    private const val MAX_INTERIOR_FRACTION = 0.90f
    /** 1.5x the search radius, so adjacent 2R-wide search windows overlap by a quarter. */
    private const val INTERIOR_LINE_SPACING_PX = NORMAL_SEARCH_RADIUS * 1.5f
    /** Never coarser than the historical 15 lines. */
    private const val MIN_INTERIOR_LINES = 15
    /** Bounds cost on very large bitmaps; quads up to ~1100 px wide stay gap-free. */
    private const val MAX_INTERIOR_LINES = 120
    /** Lines within 5% of the peak support see the same seam. */
    private const val PLATEAU_SHARE = 0.95f
    private const val MIN_SEAM_SUPPORT = 0.10f
    private const val MIN_SEAM_PROMINENCE = 0.035f
    private const val MIN_SEAM_RATIO = 1.40f
    /** A gutter runs (nearly) the full page height; partial rules and photo borders do not. */
    private const val MIN_SEAM_CONTINUITY = 0.75f
    /** Per-sample gradient floor for continuity: ~13 grey levels, above sensor noise. */
    private const val MIN_CONTINUITY_GRADIENT = 0.05f
    /** ...and at least half the line's mean, so one strong segment cannot carry the rest. */
    private const val CONTINUITY_MEAN_SHARE = 0.5f
    private const val MAX_LEFT_SLIVER_FRACTION = 0.38f
    private const val MIN_RIGHT_SLIVER_FRACTION = 0.62f
}
