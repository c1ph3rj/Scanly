package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.hypot
import kotlin.math.roundToInt

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
)

data class BookPageRefinement(
    val kind: BookPageRefinementKind,
    val quad: DocumentCornerQuad?,
    val evidence: BookPageVisualEvidence,
)

object BookPageQuadAnalyzer {
    private val interiorFractions = listOf(
        0.10f, 0.14f, 0.20f, 0.26f, 0.32f, 0.38f, 0.44f, 0.50f,
        0.56f, 0.62f, 0.68f, 0.74f, 0.80f, 0.86f, 0.90f,
    )

    fun analyze(bitmap: Bitmap, quad: DocumentCornerQuad): BookPageRefinement {
        val boundarySupport = listOf(
            lineSupport(bitmap, quad.topLeft, quad.topRight),
            lineSupport(bitmap, quad.topRight, quad.bottomRight),
            lineSupport(bitmap, quad.bottomRight, quad.bottomLeft),
            lineSupport(bitmap, quad.bottomLeft, quad.topLeft),
        ).average().toFloat()
        val interior = interiorFractions.map { fraction ->
            val top = quad.topLeft.interpolate(quad.topRight, fraction)
            val bottom = quad.bottomLeft.interpolate(quad.bottomRight, fraction)
            fraction to lineSupport(bitmap, top, bottom)
        }
        val strongest = interior.maxBy { it.second }
        val sortedSupports = interior.map { it.second }.sorted()
        val evidence = BookPageVisualEvidence(
            boundarySupport = boundarySupport,
            strongestInteriorFraction = strongest.first,
            strongestInteriorSupport = strongest.second,
            medianInteriorSupport = sortedSupports[sortedSupports.size / 2],
        )
        return refinementForEvidence(quad, evidence)
    }

    internal fun refinementForEvidence(
        quad: DocumentCornerQuad,
        evidence: BookPageVisualEvidence,
    ): BookPageRefinement {
        val seamIsContinuous = evidence.strongestInteriorSupport >= MIN_SEAM_SUPPORT &&
            evidence.strongestInteriorSupport >= evidence.medianInteriorSupport + MIN_SEAM_PROMINENCE &&
            evidence.strongestInteriorSupport >= evidence.medianInteriorSupport * MIN_SEAM_RATIO
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
        if (dominantPage != null && DocumentQuadPolicy.isCaptureReady(dominantPage)) {
            return BookPageRefinement(BookPageRefinementKind.DOMINANT_PAGE, dominantPage, evidence)
        }

        val bothPagesPlausible = DocumentQuadPolicy.isCaptureReady(leftPage) &&
            DocumentQuadPolicy.isCaptureReady(rightPage)
        return if (bothPagesPlausible) {
            BookPageRefinement(BookPageRefinementKind.AMBIGUOUS_TWO_PAGES, null, evidence)
        } else {
            BookPageRefinement(BookPageRefinementKind.NONE, quad, evidence)
        }
    }

    private fun lineSupport(bitmap: Bitmap, start: NormalizedPoint, end: NormalizedPoint): Float {
        val startX = start.x * (bitmap.width - 1)
        val startY = start.y * (bitmap.height - 1)
        val endX = end.x * (bitmap.width - 1)
        val endY = end.y * (bitmap.height - 1)
        val deltaX = endX - startX
        val deltaY = endY - startY
        val length = hypot(deltaX, deltaY)
        if (length < 2f) return 0f
        val normalX = -deltaY / length
        val normalY = deltaX / length
        var support = 0f
        var samples = 0
        for (index in 1..LINE_SAMPLES) {
            val fraction = index / (LINE_SAMPLES + 1f)
            val centerX = startX + (deltaX * fraction)
            val centerY = startY + (deltaY * fraction)
            var strongestGradient = 0f
            for (offset in -NORMAL_SEARCH_RADIUS..NORMAL_SEARCH_RADIUS) {
                val first = luminance(
                    bitmap,
                    centerX + (normalX * (offset - 1)),
                    centerY + (normalY * (offset - 1)),
                )
                val second = luminance(
                    bitmap,
                    centerX + (normalX * (offset + 1)),
                    centerY + (normalY * (offset + 1)),
                )
                if (first != null && second != null) {
                    strongestGradient = maxOf(strongestGradient, kotlin.math.abs(first - second) / 255f)
                }
            }
            support += strongestGradient
            samples += 1
        }
        return if (samples == 0) 0f else support / samples
    }

    private fun luminance(bitmap: Bitmap, x: Float, y: Float): Float? {
        val pixelX = x.roundToInt()
        val pixelY = y.roundToInt()
        if (pixelX !in 0 until bitmap.width || pixelY !in 0 until bitmap.height) return null
        val color = bitmap.getPixel(pixelX, pixelY)
        return (0.2126f * Color.red(color)) +
            (0.7152f * Color.green(color)) +
            (0.0722f * Color.blue(color))
    }

    private fun NormalizedPoint.interpolate(other: NormalizedPoint, fraction: Float): NormalizedPoint =
        NormalizedPoint(
            x = x + ((other.x - x) * fraction),
            y = y + ((other.y - y) * fraction),
        )

    private const val LINE_SAMPLES = 28
    private const val NORMAL_SEARCH_RADIUS = 5
    private const val MIN_SEAM_SUPPORT = 0.10f
    private const val MIN_SEAM_PROMINENCE = 0.035f
    private const val MIN_SEAM_RATIO = 1.40f
    private const val MAX_LEFT_SLIVER_FRACTION = 0.38f
    private const val MIN_RIGHT_SLIVER_FRACTION = 0.62f
}
