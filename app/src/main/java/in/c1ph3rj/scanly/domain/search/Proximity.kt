package `in`.c1ph3rj.scanly.domain.search

/**
 * How close together a page has every part of the query, from FTS4 `offsets()` (four integers
 * per match: column, term number, byte offset, byte size). "rent agreement" scores higher on a
 * page saying "rent agreement" than on one with "rent" at the top and "agreement" at the end.
 */
object Proximity {
    /** Window size, in bytes, at which the bonus halves; about a dozen short words. */
    private const val HALF_SPAN_BYTES = 64.0

    /**
     * 0 when the query has fewer than two parts or no column holds them all, else a bonus in
     * (0, 1] that shrinks as the smallest window holding every part grows.
     */
    fun bonus(offsets: String, termGroups: IntArray, groupCount: Int): Double {
        if (groupCount < 2) return 0.0
        val numbers = offsets.trim().split(' ').mapNotNull { it.toIntOrNull() }
        val matchesByColumn = numbers.chunked(4)
            .filter { it.size == 4 }
            .mapNotNull { (column, term, offset, size) ->
                termGroups.getOrNull(term)?.let { group -> column to Match(group, offset, offset + size) }
            }
            .groupBy({ it.first }, { it.second })
        val span = matchesByColumn.values.mapNotNull { smallestWindow(it, groupCount) }.minOrNull() ?: return 0.0
        return HALF_SPAN_BYTES / (HALF_SPAN_BYTES + span)
    }

    private data class Match(val group: Int, val start: Int, val end: Int)

    /** Classic minimum window over matches sorted by position. */
    private fun smallestWindow(matches: List<Match>, groupCount: Int): Int? {
        val sorted = matches.sortedBy { it.start }
        val counts = IntArray(groupCount)
        var covered = 0
        var left = 0
        var best: Int? = null
        sorted.forEachIndexed { right, match ->
            if (match.group !in 0 until groupCount) return@forEachIndexed
            if (counts[match.group]++ == 0) covered++
            while (covered == groupCount) {
                val span = match.end - sorted[left].start
                best = minOf(best ?: span, span)
                val leftGroup = sorted[left].group
                if (leftGroup in 0 until groupCount && --counts[leftGroup] == 0) covered--
                left++
            }
        }
        return best
    }
}
