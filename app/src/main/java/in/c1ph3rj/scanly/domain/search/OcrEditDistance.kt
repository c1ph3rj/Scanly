package `in`.c1ph3rj.scanly.domain.search

import kotlin.math.min

/**
 * Edit distance that knows OCR: plain typos (insert, delete, substitute, swap two neighbours)
 * cost 1, while OCR look-alikes cost less (see [OcrConfusions]): "lnvoice" is 0.3 from
 * "invoice", "rnodel" 0.4 from "model". Anything only reachable through look-alikes stays
 * under 1, which is how OCR misreads are told apart from real typos.
 */
object OcrEditDistance {
    /** Largest cost [distance] reports precisely; anything over it comes back as [max] + 1. */
    fun distance(a: String, b: String, max: Double): Double {
        if (a == b) return 0.0
        val rows = Array(a.length + 1) { DoubleArray(b.length + 1) }
        for (i in 0..a.length) rows[i][0] = i.toDouble()
        for (j in 0..b.length) rows[0][j] = j.toDouble()
        var previousRowBest = 0.0
        for (i in 1..a.length) {
            var rowBest = rows[i][0]
            for (j in 1..b.length) {
                var best = min(
                    min(rows[i - 1][j] + 1, rows[i][j - 1] + 1),
                    rows[i - 1][j - 1] + OcrConfusions.substitutionCost(a[i - 1], b[j - 1]),
                )
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = min(best, rows[i - 2][j - 2] + 1)
                }
                // One glyph read as two, either way round: "m" ↔ "rn".
                if (i > 1 && OcrConfusions.splitGlyphs[a.substring(i - 2, i)] == b[j - 1]) {
                    best = min(best, rows[i - 2][j - 1] + OcrConfusions.SPLIT_GLYPH_COST)
                }
                if (j > 1 && OcrConfusions.splitGlyphs[b.substring(j - 2, j)] == a[i - 1]) {
                    best = min(best, rows[i - 1][j - 2] + OcrConfusions.SPLIT_GLYPH_COST)
                }
                rows[i][j] = best
                rowBest = min(rowBest, best)
            }
            // A cell builds on the two rows above it at most (split glyphs reach back two), so
            // once two rows in a row are over the limit, nothing below can come back under it.
            if (rowBest > max && previousRowBest > max) return max + 1
            previousRowBest = rowBest
        }
        return rows[a.length][b.length].let { if (it > max) max + 1 else it }
    }
}
