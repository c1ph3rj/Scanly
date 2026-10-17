package `in`.c1ph3rj.scanly.core.editing

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** One captured pointer sample, in pad pixels. */
data class InkPoint(val x: Float, val y: Float, val timeMillis: Long, val pressure: Float = 1f)

/** A finger/stylus stroke from down to up. */
data class InkStroke(val points: List<InkPoint>, val isStylus: Boolean = false)

/** A smoothed centre-line sample with its rendered width in pixels. */
data class InkSample(val x: Float, val y: Float, val width: Float)

/** Axis-aligned bounds in pixels. */
data class InkBounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/** Converts captured pointer strokes into smoothed, variable-width ink samples. */
object InkStrokeBuilder {
    data class Style(
        /** Nominal pen width in px. */
        val baseWidth: Float,
        val minWidthFactor: Float = 0.45f,
        val maxWidthFactor: Float = 1.3f,
        /** Speed (px per ms) at which the width reaches its minimum. */
        val fastSpeed: Float = 2.5f,
        /** 0..1 exponential smoothing for width changes (higher = smoother/slower response). */
        val widthSmoothing: Float = 0.7f,
        /** Max distance in px between emitted samples. */
        val sampleSpacing: Float = 1.5f,
    )

    private const val MIN_SPACING = 0.05f
    private const val DENSE_STEPS_PER_SPACING = 4f

    /** Smoothed centre-line samples for [stroke], no further apart than [Style.sampleSpacing]. */
    fun samples(stroke: InkStroke, style: Style): List<InkSample> {
        val points = distinctPoints(stroke.points)
        if (points.isEmpty()) return emptyList()
        if (points.size == 1) return listOf(InkSample(points[0].x, points[0].y, style.baseWidth))

        val spacing = style.sampleSpacing.coerceAtLeast(MIN_SPACING)
        val widths = pointWidths(stroke.isStylus, points, style)
        val dense = denseCurve(points, widths, spacing)
        return resample(dense, spacing)
    }

    /** Union of all sample extents padded by half their width, or null when there are no samples. */
    fun bounds(strokes: List<InkStroke>, style: Style): InkBounds? {
        var left = Float.POSITIVE_INFINITY
        var top = Float.POSITIVE_INFINITY
        var right = Float.NEGATIVE_INFINITY
        var bottom = Float.NEGATIVE_INFINITY
        var hasSamples = false
        for (stroke in strokes) {
            for (sample in samples(stroke, style)) {
                val half = sample.width / 2f
                left = min(left, sample.x - half)
                top = min(top, sample.y - half)
                right = max(right, sample.x + half)
                bottom = max(bottom, sample.y + half)
                hasSamples = true
            }
        }
        return if (hasSamples) InkBounds(left, top, right, bottom) else null
    }

    private fun distinctPoints(raw: List<InkPoint>): List<InkPoint> {
        val result = ArrayList<InkPoint>(raw.size)
        for (point in raw) {
            val last = result.lastOrNull()
            if (last == null || last.x != point.x || last.y != point.y) result.add(point)
        }
        return result
    }

    private fun pointWidths(isStylus: Boolean, points: List<InkPoint>, style: Style): FloatArray {
        val low = style.minWidthFactor
        val high = style.maxWidthFactor
        val targets = FloatArray(points.size)
        if (isStylus) {
            for (i in points.indices) {
                targets[i] = low + (high - low) * points[i].pressure.coerceIn(0f, 1f)
            }
        } else {
            for (i in 1 until points.size) {
                val prev = points[i - 1]
                val cur = points[i]
                val dx = cur.x - prev.x
                val dy = cur.y - prev.y
                val elapsed = max(1L, cur.timeMillis - prev.timeMillis).toFloat()
                val speed = sqrt(dx * dx + dy * dy) / elapsed
                val slowness = (speed / style.fastSpeed).coerceIn(0f, 1f)
                targets[i] = high - (high - low) * slowness
            }
            targets[0] = targets[1]
        }

        val smoothing = style.widthSmoothing.coerceIn(0f, 1f)
        val minWidth = style.baseWidth * low
        val maxWidth = style.baseWidth * high
        val widths = FloatArray(points.size)
        var factor = targets[0]
        for (i in points.indices) {
            if (i > 0) factor = factor * smoothing + targets[i] * (1f - smoothing)
            widths[i] = (style.baseWidth * factor).coerceIn(minWidth, maxWidth)
        }
        return widths
    }

    private fun denseCurve(points: List<InkPoint>, widths: FloatArray, spacing: Float): List<InkSample> {
        val last = points.size - 1
        val dense = ArrayList<InkSample>()
        for (i in 0 until last) {
            val p0 = points[max(i - 1, 0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[min(i + 2, last)]
            val chord = distance(p1.x, p1.y, p2.x, p2.y)
            val steps = max(2, ceil(chord * DENSE_STEPS_PER_SPACING / spacing).toInt())
            val w1 = widths[i]
            val w2 = widths[i + 1]
            for (s in 0 until steps) {
                val t = s.toFloat() / steps
                dense.add(
                    InkSample(
                        x = catmullRom(p0.x, p1.x, p2.x, p3.x, t),
                        y = catmullRom(p0.y, p1.y, p2.y, p3.y, t),
                        width = w1 + (w2 - w1) * t,
                    ),
                )
            }
        }
        dense.add(InkSample(points[last].x, points[last].y, widths[last]))
        return dense
    }

    private fun resample(dense: List<InkSample>, spacing: Float): List<InkSample> {
        val cumulative = DoubleArray(dense.size)
        for (i in 1 until dense.size) {
            cumulative[i] = cumulative[i - 1] + distance(dense[i - 1].x, dense[i - 1].y, dense[i].x, dense[i].y)
        }
        val total = cumulative.last()
        if (total <= 0.0) return listOf(dense.first())

        val count = max(1, ceil(total / spacing).toInt())
        val result = ArrayList<InkSample>(count + 1)
        var segment = 0
        for (j in 0 until count) {
            val target = total * j / count
            while (segment < dense.size - 2 && cumulative[segment + 1] < target) segment++
            val a = dense[segment]
            val b = dense[segment + 1]
            val segmentLength = cumulative[segment + 1] - cumulative[segment]
            val f = if (segmentLength > 0.0) {
                ((target - cumulative[segment]) / segmentLength).coerceIn(0.0, 1.0).toFloat()
            } else {
                0f
            }
            result.add(
                InkSample(
                    x = a.x + (b.x - a.x) * f,
                    y = a.y + (b.y - a.y) * f,
                    width = a.width + (b.width - a.width) * f,
                ),
            )
        }
        result.add(dense.last())
        return result
    }

    private fun catmullRom(p0: Float, p1: Float, p2: Float, p3: Float, t: Float): Float {
        val t2 = t * t
        val t3 = t2 * t
        val linear = (-p0 + p2) * t
        val quadratic = (2f * p0 - 5f * p1 + 4f * p2 - p3) * t2
        val cubic = (-p0 + 3f * p1 - 3f * p2 + p3) * t3
        return 0.5f * (2f * p1 + linear + quadratic + cubic)
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        val dx = bx - ax
        val dy = by - ay
        return sqrt(dx * dx + dy * dy)
    }
}
