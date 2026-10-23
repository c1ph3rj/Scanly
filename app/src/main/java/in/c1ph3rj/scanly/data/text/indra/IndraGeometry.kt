package `in`.c1ph3rj.scanly.data.text.indra

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Integer source rectangle (may extend past the page) and output width of one line crop. */
internal data class CropGeometry(val x0: Int, val y0: Int, val x1: Int, val y1: Int, val outWidth: Int) {
    val width: Int get() = x1 - x0
    val height: Int get() = y1 - y0
}

internal object IndraGeometry {
    private const val PAD_X = 0.3
    private const val PAD_Y = 0.2
    const val LINE_HEIGHT = 32
    private const val MAX_CROP_WIDTH = 4096
    private const val MIN_CROP_WIDTH = 8

    /** `crop_line` geometry: [window] 1 gives a 32 px high crop, 3 gives 96 px with the line in the middle band. */
    fun cropGeometry(box: IndraBox, window: Int): CropGeometry {
        val padY = (window * (1.0 + 2.0 * PAD_Y) - 1.0) / 2.0
        val h = max(1.0, box.height.toDouble())
        val x0 = floor(box.x0 - PAD_X * h).toInt()
        val y0 = floor(box.y0 - padY * h).toInt()
        val x1 = max(x0 + 1, ceil(box.x1 + PAD_X * h).toInt())
        val y1 = max(y0 + 1, ceil(box.y1 + padY * h).toInt())
        val outHeight = LINE_HEIGHT * window
        val outWidth = (((x1 - x0).toDouble() * outHeight) / (y1 - y0)).roundToInt()
            .coerceIn(MIN_CROP_WIDTH, MAX_CROP_WIDTH)
        return CropGeometry(x0, y0, x1, y1, outWidth)
    }

    /** numpy-style linear-interpolated percentile of the values summarized by a 256-bin [histogram]. */
    fun percentile(histogram: IntArray, quantile: Double): Double {
        val total = histogram.sum()
        if (total == 0) return 0.0
        val position = quantile / 100.0 * (total - 1)
        val low = floor(position).toLong()
        val high = ceil(position).toLong()
        val fraction = position - low
        val lowValue = valueAtRank(histogram, low)
        val highValue = if (high == low) lowValue else valueAtRank(histogram, high)
        return lowValue + (highValue - lowValue) * fraction
    }

    private fun valueAtRank(histogram: IntArray, rank: Long): Int {
        var cumulative = 0L
        for (value in histogram.indices) {
            cumulative += histogram[value]
            if (cumulative > rank) return value
        }
        return histogram.lastIndex
    }

    /** Light text on a dark ground: the bright tail is far above the median compared with the dark tail. */
    fun isDarkLine(histogram: IntArray): Boolean {
        val lo = percentile(histogram, 2.0)
        val med = percentile(histogram, 50.0)
        val hi = percentile(histogram, 98.0)
        return hi - med > 2 * (med - lo) + 8
    }

    /** Batches line indices by similar width, narrowest first (`width_batches`). */
    fun widthBatches(widths: IntArray, maxLines: Int, maxPixels: Int = 64 * 640): List<IntArray> {
        val order = widths.indices.sortedBy { widths[it] }
        val batches = ArrayList<IntArray>()
        var current = ArrayList<Int>()
        for (index in order) {
            val w = widths[index]
            if (current.isNotEmpty()) {
                val first = widths[current[0]]
                if (current.size >= maxLines || w > 1.3 * first || (current.size + 1).toLong() * w > maxPixels) {
                    batches += current.toIntArray()
                    current = ArrayList()
                }
            }
            current += index
        }
        if (current.isNotEmpty()) batches += current.toIntArray()
        return batches
    }

    fun padTo8(width: Int): Int = (width + 7) / 8 * 8
}

/**
 * Maps a point on the processed page (turned by [turns] clockwise quarter turns, then rotated by
 * [angleDegrees] about its centre with OpenCV's `getRotationMatrix2D` convention) back to the
 * original image of size [originalWidth] x [originalHeight].
 */
internal class IndraPageTransform(
    private val originalWidth: Int,
    private val originalHeight: Int,
    private val turns: Int,
    private val angleDegrees: Double,
) {
    private val turnedWidth = if (turns % 2 == 0) originalWidth else originalHeight
    private val turnedHeight = if (turns % 2 == 0) originalHeight else originalWidth

    fun toOriginal(px: Float, py: Float): Pair<Float, Float> {
        var tx = px.toDouble()
        var ty = py.toDouble()
        if (abs(angleDegrees) > 0.0) {
            val rad = angleDegrees * PI / 180.0
            val c = cos(rad)
            val s = sin(rad)
            val dx = tx - turnedWidth / 2.0
            val dy = ty - turnedHeight / 2.0
            tx = c * dx - s * dy + turnedWidth / 2.0
            ty = s * dx + c * dy + turnedHeight / 2.0
        }
        val (x, y) = when (turns) {
            1 -> ty to originalHeight - tx
            2 -> originalWidth - tx to originalHeight - ty
            3 -> originalWidth - ty to tx
            else -> tx to ty
        }
        return x.toFloat().coerceIn(0f, originalWidth.toFloat()) to y.toFloat().coerceIn(0f, originalHeight.toFloat())
    }

    /** Bounding box in the original image of the four mapped corners of [box]. */
    fun boxToOriginal(box: IndraBox): IndraBox {
        val corners = listOf(
            toOriginal(box.x0, box.y0),
            toOriginal(box.x1, box.y0),
            toOriginal(box.x1, box.y1),
            toOriginal(box.x0, box.y1),
        )
        return IndraBox(
            x0 = corners.minOf { it.first },
            y0 = corners.minOf { it.second },
            x1 = corners.maxOf { it.first },
            y1 = corners.maxOf { it.second },
            score = box.score,
        )
    }
}
