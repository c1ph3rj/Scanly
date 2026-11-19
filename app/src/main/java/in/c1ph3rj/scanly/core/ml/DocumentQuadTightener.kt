package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Pulls an oversize document quad inward onto the real paper edges.
 *
 * Corner models often overshoot paper bounds into keyboards / desks. Each side is examined on
 * its own, in pixel space: at samples along the side, the step in brightness ACROSS the side
 * (along its inward normal) is measured, and the first strong step inside the current position
 * is taken as that sample's candidate paper edge. A side only moves when a clear majority of
 * its samples agree on one offset; text lines, table rules and letterheads rarely do, and a side
 * that already sits on a strong step stays put. Moved sides are shifted along their own normals
 * and the corners are recomputed as intersections of the shifted side lines.
 */
object DocumentQuadTightener {
    /** Largest inward move, as a fraction of the frame dimension across the side (old 0.085). */
    private const val MAX_INSET_FRACTION = 0.085f
    /** Never move a side more than this share of its distance to the quad centroid. */
    private const val MAX_EDGE_PULL_FRACTION = 0.28f
    private const val EDGE_SAMPLES = 24
    /** Samples stay away from the corners, where the neighbouring side's edge interferes. */
    private const val SAMPLE_START = 0.10f
    private const val SAMPLE_END = 0.90f
    /** Width (px) of the averaged bands either side of a candidate edge; thin strokes count less. */
    private const val STEP_BAND = 4
    private const val SEARCH_STEP_PX = 0.5f
    /** The step at the current position is the max within this many px (sub-pixel edge placement). */
    private const val BASELINE_RADIUS_PX = 1.5f
    /** Brightness step (0..1) that counts as a real edge: ~30 grey levels, above sensor noise. */
    private const val MIN_EDGE_STEP = 0.12f
    /** A candidate must also beat the step at the current position by this much. */
    private const val MIN_GRADIENT_GAIN = 0.025f
    /** Share of a side's samples that must agree on the offset before that side moves. */
    private const val MIN_AGREEMENT = 0.60f
    /** Offsets within this distance of the median agree (px, or share of the frame dimension). */
    private const val MIN_OFFSET_TOLERANCE_PX = 2f
    private const val OFFSET_TOLERANCE_FRACTION = 0.01f
    /** Moves below one pixel are noise. */
    private const val MIN_INSET_PX = 1f
    /** Skip tightening when the model already sits on strong edges. */
    private const val STRONG_BOUNDARY_SUPPORT = 0.155f

    fun refine(
        bitmap: Bitmap,
        quad: DocumentCornerQuad,
        boundarySupport: Float = 0f,
    ): DocumentCornerQuad {
        if (!quad.isValid() || !quad.isConvex()) return quad
        if (boundarySupport >= STRONG_BOUNDARY_SUPPORT) return quad
        return refine(bitmap.toLumaGrid(), quad, boundarySupport)
    }

    fun refine(
        grid: LumaGrid,
        quad: DocumentCornerQuad,
        boundarySupport: Float = 0f,
    ): DocumentCornerQuad {
        if (!quad.isValid() || !quad.isConvex()) return quad
        if (boundarySupport >= STRONG_BOUNDARY_SUPPORT) return quad
        if (grid.width < 2 || grid.height < 2) return quad

        val corners = quad.orderedPoints().map { (_, point) ->
            Vec(grid.toPixelX(point.x), grid.toPixelY(point.y))
        }
        val centroid = Vec(corners.sumOf { it.x.toDouble() }.toFloat() / 4f, corners.sumOf { it.y.toDouble() }.toFloat() / 4f)
        // Sides: top (TL->TR), right (TR->BR), bottom (BR->BL), left (BL->TL).
        val sides = corners.indices.map { index ->
            sideLine(corners[index], corners[(index + 1) % corners.size], centroid) ?: return quad
        }
        val insets = sides.map { side -> edgeInset(grid, side, centroid) }
        if (insets.all { it <= 0f }) return quad

        val shifted = sides.zip(insets).map { (side, inset) ->
            side.copy(origin = side.origin + side.normal * inset)
        }
        val topLeft = intersect(shifted[3], shifted[0]) ?: return quad
        val topRight = intersect(shifted[0], shifted[1]) ?: return quad
        val bottomRight = intersect(shifted[1], shifted[2]) ?: return quad
        val bottomLeft = intersect(shifted[2], shifted[3]) ?: return quad
        val tightened = DocumentCornerQuad(
            topLeft = topLeft.toNormalized(grid) ?: return quad,
            topRight = topRight.toNormalized(grid) ?: return quad,
            bottomRight = bottomRight.toNormalized(grid) ?: return quad,
            bottomLeft = bottomLeft.toNormalized(grid) ?: return quad,
        )
        return if (tightened.isValid() && tightened.isConvex()) tightened else quad
    }

    /** Agreed inward move of one side in pixels, or 0 when the samples do not agree. */
    private fun edgeInset(grid: LumaGrid, side: SideLine, centroid: Vec): Float {
        val acrossDimension = abs(side.normal.x) * (grid.width - 1) + abs(side.normal.y) * (grid.height - 1)
        val centroidDistance = (centroid - side.origin).dot(side.normal)
        val maxInset = minOf(acrossDimension * MAX_INSET_FRACTION, centroidDistance * MAX_EDGE_PULL_FRACTION)
        if (maxInset < MIN_INSET_PX) return 0f

        val offsets = ArrayList<Float>(EDGE_SAMPLES)
        for (index in 0 until EDGE_SAMPLES) {
            val t = SAMPLE_START + (SAMPLE_END - SAMPLE_START) * index / (EDGE_SAMPLES - 1)
            val point = side.origin + side.direction * (side.length * t)
            sampleOffset(grid, point, side.normal, maxInset)?.let(offsets::add)
        }
        val required = MIN_AGREEMENT * EDGE_SAMPLES
        if (offsets.size < required) return 0f
        val median = median(offsets)
        val tolerance = maxOf(MIN_OFFSET_TOLERANCE_PX, acrossDimension * OFFSET_TOLERANCE_FRACTION)
        val agreeing = offsets.filter { abs(it - median) <= tolerance }
        if (agreeing.size < required) return 0f
        val inset = median(agreeing)
        return if (inset < MIN_INSET_PX) 0f else inset.coerceAtMost(maxInset)
    }

    /**
     * Offset (px, along the inward normal) of this sample's paper edge: 0 when the current
     * position already sits on a strong step, the first strong step inward otherwise, or null
     * when there is no evidence within [maxInset].
     */
    private fun sampleOffset(grid: LumaGrid, point: Vec, normal: Vec, maxInset: Float): Float? {
        var baseline = 0f
        var offset = -BASELINE_RADIUS_PX
        while (offset <= BASELINE_RADIUS_PX) {
            step(grid, point, normal, offset)?.let { baseline = maxOf(baseline, it) }
            offset += SEARCH_STEP_PX
        }
        if (baseline >= MIN_EDGE_STEP) return 0f

        val threshold = maxOf(MIN_EDGE_STEP, baseline + MIN_GRADIENT_GAIN)
        offset = BASELINE_RADIUS_PX + SEARCH_STEP_PX
        while (offset <= maxInset) {
            val value = step(grid, point, normal, offset)
            if (value != null && value >= threshold) {
                // Climb to the local peak so the edge lands on the steepest point of the step.
                var peakOffset = offset
                var peakValue: Float = value
                while (peakOffset + SEARCH_STEP_PX <= maxInset) {
                    val next = step(grid, point, normal, peakOffset + SEARCH_STEP_PX) ?: break
                    if (next <= peakValue) break
                    peakOffset += SEARCH_STEP_PX
                    peakValue = next
                }
                return peakOffset
            }
            offset += SEARCH_STEP_PX
        }
        return null
    }

    /**
     * Brightness step (0..1) across a boundary at [offset] px along [normal]: the mean of a
     * [STEP_BAND]-px band just inside minus the band just outside. A directional derivative,
     * so strokes running along the normal barely register. Null when a band leaves the grid.
     */
    private fun step(grid: LumaGrid, point: Vec, normal: Vec, offset: Float): Float? {
        var inner = 0f
        var outer = 0f
        for (index in 0 until STEP_BAND) {
            val distance = index + 0.5f
            val inside = point + normal * (offset + distance)
            val outside = point + normal * (offset - distance)
            inner += grid.sample(inside.x, inside.y) ?: return null
            outer += grid.sample(outside.x, outside.y) ?: return null
        }
        return abs(inner - outer) / (STEP_BAND * 255f)
    }

    private fun sideLine(start: Vec, end: Vec, centroid: Vec): SideLine? {
        val delta = end - start
        val length = hypot(delta.x, delta.y)
        if (length < 1f) return null
        val direction = delta * (1f / length)
        var normal = Vec(-direction.y, direction.x)
        // Ensure the normal points toward the document interior.
        if ((centroid - start).dot(normal) < 0f) normal = normal * -1f
        return SideLine(origin = start, direction = direction, normal = normal, length = length)
    }

    private fun intersect(first: SideLine, second: SideLine): Vec? {
        val cross = first.direction.cross(second.direction)
        if (abs(cross) < 1e-6f) return null
        val t = (second.origin - first.origin).cross(second.direction) / cross
        return first.origin + first.direction * t
    }

    private fun Vec.toNormalized(grid: LumaGrid): NormalizedPoint? {
        val normalizedX = grid.toNormalizedX(x)
        val normalizedY = grid.toNormalizedY(y)
        if (!normalizedX.isFinite() || !normalizedY.isFinite()) return null
        // Shifts only move inward; absorb float error at the frame border.
        return NormalizedPoint(
            x = if (abs(normalizedX) < EDGE_EPSILON) 0f else if (abs(normalizedX - 1f) < EDGE_EPSILON) 1f else normalizedX,
            y = if (abs(normalizedY) < EDGE_EPSILON) 0f else if (abs(normalizedY - 1f) < EDGE_EPSILON) 1f else normalizedY,
        )
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) sorted[middle] else (sorted[middle - 1] + sorted[middle]) / 2f
    }

    private const val EDGE_EPSILON = 1e-5f

    private data class Vec(val x: Float, val y: Float) {
        operator fun plus(other: Vec) = Vec(x + other.x, y + other.y)
        operator fun minus(other: Vec) = Vec(x - other.x, y - other.y)
        operator fun times(scale: Float) = Vec(x * scale, y * scale)
        fun dot(other: Vec) = x * other.x + y * other.y
        fun cross(other: Vec) = x * other.y - y * other.x
    }

    private data class SideLine(val origin: Vec, val direction: Vec, val normal: Vec, val length: Float)
}
