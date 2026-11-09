package `in`.c1ph3rj.scanly.core.ml

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Small RGB raster used for frame-level checks. Pixels are packed `0xRRGGBB` (alpha ignored)
 * so the policy stays free of Android types and runs in plain JVM tests.
 */
class PixelGrid(
    val width: Int,
    val height: Int,
    val pixels: IntArray,
) {
    init {
        require(width > 0 && height > 0 && pixels.size >= width * height) {
            "Pixel grid needs width * height pixels."
        }
    }

    fun rgb(x: Int, y: Int): Int = pixels[y * width + x]

    /** Box-averages [factor] x [factor] blocks so thin text strokes survive as grey cells. */
    fun downsample(factor: Int): PixelGrid {
        if (factor <= 1) return this
        val outWidth = (width / factor).coerceAtLeast(1)
        val outHeight = (height / factor).coerceAtLeast(1)
        val out = IntArray(outWidth * outHeight)
        for (outY in 0 until outHeight) {
            for (outX in 0 until outWidth) {
                var red = 0
                var green = 0
                var blue = 0
                var count = 0
                for (y in outY * factor until minOf(height, (outY + 1) * factor)) {
                    for (x in outX * factor until minOf(width, (outX + 1) * factor)) {
                        val color = rgb(x, y)
                        red += red(color)
                        green += green(color)
                        blue += blue(color)
                        count++
                    }
                }
                out[outY * outWidth + outX] = packRgb(red / count, green / count, blue / count)
            }
        }
        return PixelGrid(outWidth, outHeight, out)
    }

    internal companion object {
        fun red(color: Int): Int = (color shr 16) and 0xFF
        fun green(color: Int): Int = (color shr 8) and 0xFF
        fun blue(color: Int): Int = color and 0xFF
        fun packRgb(red: Int, green: Int, blue: Int): Int = (red shl 16) or (green shl 8) or blue
    }
}

enum class FlatPageDecision {
    /** Background is visible around the page (a photo), or a coloured backdrop: trust the detector. */
    KEEP_DETECTED_QUAD,
    /** White plain frame edge and nothing outside the quad: a document on a white scanner lid. */
    KEEP_QUAD_ON_PLAIN_BACKDROP,
    /** The frame edge is printed page: paper margins plus a flat coloured band. */
    FULL_FRAME_NO_BACKGROUND,
    /** Paper frame edge, but the quad cuts through page content (stamp, box, letterhead text). */
    FULL_FRAME_CONTENT_OUTSIDE_QUAD,
}

data class FrameSide(
    val medianRgb: Int,
    /** Share of ring cells within the plain tolerance of [medianRgb]. */
    val plainFraction: Float,
    val plain: Boolean,
    val paper: Boolean,
)

data class FrameBorderEvidence(
    /** Top, bottom, left, right. */
    val sides: List<FrameSide>,
) {
    /** No side shows desk texture or lighting falloff. */
    val allSidesPlain: Boolean get() = sides.all { it.plain }
    val paperSides: Int get() = sides.count { it.plain && it.paper }
}

/**
 * Decides whether a still image (gallery import, AI Detect) already fills the frame with the
 * page, so the detector's quad should be replaced by the full frame.
 *
 * Corner models are trained on photos of paper on a surface. Given a flat page (PDF render,
 * screenshot, clean scan) they latch onto the strongest inner rectangle: a stamp, the bottom
 * edge of a letterhead band, a form box. Photos show background at the frame edge (shaded,
 * textured, rarely paper-white) while flat pages show their own paper margin there. So:
 * 1. Any non-plain side of the outer ring → a photo; keep the quad (unchanged behaviour).
 * 2. Fewer than two paper-white sides → a coloured backdrop (dark scanner lid, plain desk);
 *    keep the quad.
 * 3. Ink outside the quad (colours the frame edge does not show) → the quad cut the page;
 *    full frame.
 * 4. Paper sides plus a flat non-paper side → a printed band at the page edge; full frame.
 * 5. Paper all round and nothing outside the quad → document on a white backdrop, or a crop
 *    that only trims blank margin; keep the quad.
 */
object FlatPageQuadPolicy {
    /** Long side of the analysis grid; ~17x reduction of a 2800 px processing bitmap. */
    const val ANALYSIS_LONG_SIDE = 160
    private const val RING_FRACTION = 0.015f
    private const val MIN_RING_CELLS = 2
    /** Max channel difference from the side median that still counts as the same flat colour. */
    private const val PLAIN_TOLERANCE = 10
    /** A side may carry the end of a band (a letterhead crossing the left edge) and stay plain. */
    private const val MIN_PLAIN_FRACTION = 0.75f
    private const val PAPER_MIN_LUMA = 180f
    private const val PAPER_MAX_CHROMA = 30
    private const val MIN_PAPER_SIDES = 2
    /** Exterior cells closer than this (fraction of the long side) to the quad are ignored. */
    private const val QUAD_EDGE_MARGIN_FRACTION = 0.03f
    /** Minimum channel difference from every frame-side colour for a cell to count as ink. */
    private const val INK_DELTA = 28
    private const val MIN_INK_FRACTION = 0.03f
    private const val MIN_INK_CELLS = 6

    fun decide(quad: DocumentCornerQuad, grid: PixelGrid): FlatPageDecision {
        val border = borderEvidence(grid)
        return when {
            !border.allSidesPlain -> FlatPageDecision.KEEP_DETECTED_QUAD
            border.paperSides < MIN_PAPER_SIDES -> FlatPageDecision.KEEP_DETECTED_QUAD
            hasContentOutsideQuad(quad, grid, border) -> FlatPageDecision.FULL_FRAME_CONTENT_OUTSIDE_QUAD
            border.paperSides < border.sides.size -> FlatPageDecision.FULL_FRAME_NO_BACKGROUND
            else -> FlatPageDecision.KEEP_QUAD_ON_PLAIN_BACKDROP
        }
    }

    /** The detector quad, or [DocumentCornerQuad.FullFrame] when the page already fills the image. */
    fun resolve(quad: DocumentCornerQuad, grid: PixelGrid): DocumentCornerQuad =
        when (decide(quad, grid)) {
            FlatPageDecision.KEEP_DETECTED_QUAD,
            FlatPageDecision.KEEP_QUAD_ON_PLAIN_BACKDROP -> quad
            FlatPageDecision.FULL_FRAME_NO_BACKGROUND,
            FlatPageDecision.FULL_FRAME_CONTENT_OUTSIDE_QUAD -> DocumentCornerQuad.FullFrame
        }

    fun borderEvidence(grid: PixelGrid): FrameBorderEvidence {
        val ringX = ringCells(grid.width)
        val ringY = ringCells(grid.height)
        return FrameBorderEvidence(
            listOf(
                side(grid, 0 until grid.width, 0 until ringY),
                side(grid, 0 until grid.width, grid.height - ringY until grid.height),
                side(grid, 0 until ringX, 0 until grid.height),
                side(grid, grid.width - ringX until grid.width, 0 until grid.height),
            ),
        )
    }

    private fun side(grid: PixelGrid, xs: IntRange, ys: IntRange): FrameSide {
        val colors = IntArray(xs.count() * ys.count())
        var index = 0
        for (y in ys) {
            for (x in xs) {
                colors[index++] = grid.rgb(x, y)
            }
        }
        val median = PixelGrid.packRgb(
            channelMedian(colors, PixelGrid::red),
            channelMedian(colors, PixelGrid::green),
            channelMedian(colors, PixelGrid::blue),
        )
        val plainFraction = colors.count { maxChannelDelta(it, median) <= PLAIN_TOLERANCE }.toFloat() / colors.size
        return FrameSide(
            medianRgb = median,
            plainFraction = plainFraction,
            plain = plainFraction >= MIN_PLAIN_FRACTION,
            paper = isPaper(median),
        )
    }

    private fun hasContentOutsideQuad(
        quad: DocumentCornerQuad,
        grid: PixelGrid,
        border: FrameBorderEvidence,
    ): Boolean {
        val corners = quad.orderedPoints().map { (_, point) ->
            Pair(point.x * grid.width, point.y * grid.height)
        }
        val margin = max(grid.width, grid.height) * QUAD_EDGE_MARGIN_FRACTION
        val frameColors = border.sides.map { it.medianRgb }.distinct()
        var exterior = 0
        var ink = 0
        for (y in 0 until grid.height) {
            for (x in 0 until grid.width) {
                if (!isOutside(corners, x + 0.5f, y + 0.5f, margin)) continue
                exterior++
                // Ink is any colour the frame edge does not show: text, stamps, rules.
                val color = grid.rgb(x, y)
                if (frameColors.all { maxChannelDelta(color, it) >= INK_DELTA }) ink++
            }
        }
        return ink >= MIN_INK_CELLS && ink >= exterior * MIN_INK_FRACTION
    }

    /** True when the point lies more than [margin] outside the (screen-clockwise) quad. */
    private fun isOutside(
        corners: List<Pair<Float, Float>>,
        x: Float,
        y: Float,
        margin: Float,
    ): Boolean = corners.indices.any { index ->
        val (startX, startY) = corners[index]
        val (endX, endY) = corners[(index + 1) % corners.size]
        val edgeX = endX - startX
        val edgeY = endY - startY
        val length = sqrt(edgeX * edgeX + edgeY * edgeY)
        // With y pointing down, clockwise order puts the interior on the positive side.
        length > 0f && ((edgeX * (y - startY)) - (edgeY * (x - startX))) / length < -margin
    }

    private fun ringCells(dimension: Int): Int =
        (dimension * RING_FRACTION).roundToInt().coerceAtLeast(MIN_RING_CELLS).coerceAtMost(dimension)

    private fun isPaper(color: Int): Boolean {
        val red = PixelGrid.red(color)
        val green = PixelGrid.green(color)
        val blue = PixelGrid.blue(color)
        val luma = 0.2126f * red + 0.7152f * green + 0.0722f * blue
        return luma >= PAPER_MIN_LUMA && maxOf(red, green, blue) - minOf(red, green, blue) <= PAPER_MAX_CHROMA
    }

    private fun channelMedian(colors: IntArray, channel: (Int) -> Int): Int {
        val histogram = IntArray(256)
        colors.forEach { histogram[channel(it)]++ }
        var seen = 0
        for (value in histogram.indices) {
            seen += histogram[value]
            if (seen * 2 >= colors.size) return value
        }
        return 255
    }

    private fun maxChannelDelta(first: Int, second: Int): Int = maxOf(
        abs(PixelGrid.red(first) - PixelGrid.red(second)),
        abs(PixelGrid.green(first) - PixelGrid.green(second)),
        abs(PixelGrid.blue(first) - PixelGrid.blue(second)),
    )
}
