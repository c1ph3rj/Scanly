package `in`.c1ph3rj.scanly.domain.search

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.ln

/**
 * Relevance of one page from FTS4 `matchinfo(table, 'pcnalx')`, which SQLite returns as
 * native-order 32-bit integers: phrase count p, column count c, row count n, average tokens per
 * column a[c], this row's tokens per column l[c], then for each phrase and column the triple
 * (hits in this row, hits in all rows, rows with a hit).
 *
 * BM25: a word found on few pages counts more than a common one, repeats count with
 * diminishing returns, and a long page doesn't win just by being long.
 */
object Bm25 {
    const val FORMAT = "pcnalx"
    private const val K1 = 1.2
    private const val B = 0.75

    data class Score(val relevance: Double, val hits: Int)

    fun decode(blob: ByteArray): IntArray {
        val ints = ByteBuffer.wrap(blob).order(ByteOrder.nativeOrder()).asIntBuffer()
        return IntArray(ints.remaining()).also(ints::get)
    }

    /** [columnWeights] scales each column's share; columns past its end weigh 1. */
    fun score(matchinfo: IntArray, columnWeights: DoubleArray = DoubleArray(0)): Score {
        if (matchinfo.size < 3) return Score(0.0, 0)
        val phrases = matchinfo[0]
        val columns = matchinfo[1]
        val rows = matchinfo[2].toDouble()
        val averageStart = 3
        val lengthStart = averageStart + columns
        val hitsStart = lengthStart + columns
        if (matchinfo.size < hitsStart + 3 * phrases * columns) return Score(0.0, 0)
        var relevance = 0.0
        var hits = 0
        for (phrase in 0 until phrases) {
            for (column in 0 until columns) {
                val base = hitsStart + 3 * (phrase * columns + column)
                val inRow = matchinfo[base]
                if (inRow == 0) continue
                hits += inRow
                val rowsWithHit = matchinfo[base + 2].toDouble()
                val idf = ln((rows - rowsWithHit + 0.5) / (rowsWithHit + 0.5) + 1.0)
                val average = matchinfo[averageStart + column].coerceAtLeast(1).toDouble()
                val length = matchinfo[lengthStart + column].toDouble()
                val tf = inRow.toDouble()
                val weight = columnWeights.getOrElse(column) { 1.0 }
                relevance += weight * idf * (tf * (K1 + 1)) / (tf + K1 * (1 - B + B * length / average))
            }
        }
        return Score(relevance, hits)
    }
}
