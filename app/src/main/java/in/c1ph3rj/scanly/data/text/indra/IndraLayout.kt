package `in`.c1ph3rj.scanly.data.text.indra

import kotlin.math.max
import kotlin.math.min

internal object IndraLayout {
    /** Left-right mirror for ordering RTL pages: x' = pageWidth - x. */
    fun mirror(boxes: List<IndraBox>, pageWidth: Float): List<IndraBox> =
        boxes.map { it.copy(x0 = pageWidth - it.x1, x1 = pageWidth - it.x0) }

    /**
     * Reading order by recursive XY-cut: split on the widest empty gap (a vertical gap only counts
     * when it is at least one median line height wide), otherwise fall back to top-down order.
     * Returns indices into [boxes].
     */
    fun xyCut(boxes: List<IndraBox>): List<Int> {
        if (boxes.isEmpty()) return emptyList()
        val heights = boxes.map { it.height }.sorted()
        val minVerticalGap = max(1f, heights[heights.size / 2])
        val result = ArrayList<Int>(boxes.size)
        cut(boxes, boxes.indices.toList(), minVerticalGap, result)
        return result
    }

    private class Gap(val size: Float, val before: List<Int>, val after: List<Int>)

    private fun cut(boxes: List<IndraBox>, indices: List<Int>, minVerticalGap: Float, out: MutableList<Int>) {
        if (indices.size <= 1) {
            out += indices
            return
        }
        val vertical = widestGap(indices, { boxes[it].x0 }, { boxes[it].x1 })
            ?.takeIf { it.size >= minVerticalGap }
        val horizontal = widestGap(indices, { boxes[it].y0 }, { boxes[it].y1 })
        val chosen = when {
            vertical != null && (horizontal == null || vertical.size > horizontal.size) -> vertical
            else -> horizontal ?: vertical
        }
        if (chosen == null) {
            out += indices.sortedWith(compareBy({ boxes[it].y0 }, { boxes[it].x0 }))
            return
        }
        cut(boxes, chosen.before, minVerticalGap, out)
        cut(boxes, chosen.after, minVerticalGap, out)
    }

    private fun widestGap(indices: List<Int>, start: (Int) -> Float, end: (Int) -> Float): Gap? {
        val sorted = indices.sortedBy(start)
        var reach = end(sorted[0])
        var best = 0f
        var bestAt = -1
        for (i in 1 until sorted.size) {
            val gap = start(sorted[i]) - reach
            if (gap > best) {
                best = gap
                bestAt = i
            }
            reach = max(reach, end(sorted[i]))
        }
        if (bestAt < 0) return null
        return Gap(best, sorted.subList(0, bestAt), sorted.subList(bestAt, sorted.size))
    }

    /**
     * Groups consecutive lines (already in reading order) into blocks: the next line joins the
     * block when it starts at most one line height below the previous one (a little overlap is
     * allowed), shares some horizontal extent and has a similar height.
     */
    fun groupBlocks(ordered: List<IndraBox>): List<List<Int>> {
        if (ordered.isEmpty()) return emptyList()
        val blocks = ArrayList<MutableList<Int>>()
        var current = mutableListOf(0)
        for (i in 1 until ordered.size) {
            if (sameBlock(ordered[i - 1], ordered[i])) {
                current += i
            } else {
                blocks += current
                current = mutableListOf(i)
            }
        }
        blocks += current
        return blocks
    }

    private fun sameBlock(previous: IndraBox, next: IndraBox): Boolean {
        val h = max(1f, previous.height)
        val gap = next.y0 - previous.y1
        if (gap > 1.0f * h || gap < -0.5f * h) return false
        val overlap = min(previous.x1, next.x1) - max(previous.x0, next.x0)
        if (overlap <= 0f) return false
        val ratio = max(1f, next.height) / h
        return ratio in 0.6f..1.67f
    }

    private val leaderChars = setOf('.', '·', '…', '‥', '_', '-', '–', '—', '=')

    /** Dotted/ruled leader lines (table-of-contents fill, signature rules) carry no text. */
    fun isLeaderLine(text: String, box: IndraBox): Boolean {
        val trimmed = text.filterNot { it.isWhitespace() }
        if (trimmed.isEmpty() || !trimmed.all { it in leaderChars }) return false
        val wide = box.width >= 3f * max(1f, box.height)
        return trimmed.length >= 3 || wide || !trimmed.any { it == '-' || it == '–' || it == '—' }
    }
}
