package `in`.c1ph3rj.scanly.data.text.indra

import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Turns the detector's `[H/2, W/2, 3]` map (core logit, top distance, bottom distance) into line boxes. */
internal object IndraDetDecoder {
    const val DET_LONG_SIDE = 1024
    private const val MAX_ADAPTIVE_LONG_SIDE = 2048
    private const val ADAPTIVE_MIN_LINES = 8
    private const val ADAPTIVE_MIN_H = 9f
    private const val ADAPTIVE_TARGET_H = 12f

    /**
     * [out] is the flattened map, row-major with 3 interleaved channels, [mapH] x [mapW] cells at
     * [stride] input pixels per cell. Components use 4-connectivity; areas below 2 are dropped.
     */
    fun decode(out: FloatArray, mapH: Int, mapW: Int, stride: Int = 2): List<IndraBox> {
        val total = mapH * mapW
        val visited = BooleanArray(total)
        val stack = IntArray(total)
        val boxes = ArrayList<IndraBox>()
        for (start in 0 until total) {
            if (visited[start] || out[start * 3] <= 0f) continue
            var top = 0
            stack[top++] = start
            visited[start] = true
            var area = 0
            var minCol = Int.MAX_VALUE
            var maxCol = Int.MIN_VALUE
            var sumTop = 0.0
            var sumBottom = 0.0
            var sumY = 0.0
            var sumProb = 0.0
            while (top > 0) {
                val cell = stack[--top]
                val row = cell / mapW
                val col = cell % mapW
                area++
                minCol = min(minCol, col)
                maxCol = max(maxCol, col)
                sumY += (row + 0.5) * stride
                sumTop += out[cell * 3 + 1]
                sumBottom += out[cell * 3 + 2]
                sumProb += 1.0 / (1.0 + exp(-out[cell * 3].toDouble()))
                if (col > 0) top = push(cell - 1, out, visited, stack, top)
                if (col < mapW - 1) top = push(cell + 1, out, visited, stack, top)
                if (row > 0) top = push(cell - mapW, out, visited, stack, top)
                if (row < mapH - 1) top = push(cell + mapW, out, visited, stack, top)
            }
            if (area < 2) continue
            val y0 = ((sumY - sumTop) / area).toFloat()
            val y1 = ((sumY + sumBottom) / area).toFloat()
            val lineHeight = max(1f, y1 - y0)
            val extra = min(0.15f * lineHeight, 0.5f * lineHeight)
            boxes += IndraBox(
                x0 = minCol * stride - extra,
                y0 = y0,
                x1 = (maxCol + 1) * stride + extra,
                y1 = y1,
                score = (sumProb / area).toFloat(),
            )
        }
        return boxes
    }

    private fun push(cell: Int, out: FloatArray, visited: BooleanArray, stack: IntArray, top: Int): Int {
        if (visited[cell] || out[cell * 3] <= 0f) return top
        visited[cell] = true
        stack[top] = cell
        return top + 1
    }

    /** Long side for a second, larger pass when lines are tiny, or null when the first pass is fine. */
    fun adaptiveLongSide(boxes: List<IndraBox>): Int? {
        if (boxes.size < ADAPTIVE_MIN_LINES) return null
        val heights = boxes.map { it.height }.sorted()
        val median = if (heights.size % 2 == 1) {
            heights[heights.size / 2]
        } else {
            (heights[heights.size / 2 - 1] + heights[heights.size / 2]) / 2f
        }
        if (median >= ADAPTIVE_MIN_H) return null
        val target = min(MAX_ADAPTIVE_LONG_SIDE.toFloat(), DET_LONG_SIDE * ADAPTIVE_TARGET_H / max(median, 1f))
        val side = (floor(target / 32f) * 32).toInt()
        return if (side > DET_LONG_SIDE) side else null
    }
}
