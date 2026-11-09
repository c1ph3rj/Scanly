package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class Bm25Test {
    /** matchinfo 'pcnalx' for one column: [p, c, n, a, l, then (inRow, inAll, rowsWithHit) per phrase]. */
    private fun matchinfo(rows: Int, average: Int, length: Int, vararg phrases: Triple<Int, Int, Int>) =
        intArrayOf(phrases.size, 1, rows, average, length) +
            phrases.flatMap { (inRow, inAll, withHit) -> listOf(inRow, inAll, withHit) }

    @Test
    fun rareWordOutscoresCommonWord() {
        val rare = Bm25.score(matchinfo(100, 50, 50, Triple(1, 2, 2)))
        val common = Bm25.score(matchinfo(100, 50, 50, Triple(1, 150, 90)))
        assertTrue(rare.relevance > common.relevance)
    }

    @Test
    fun repeatsHelpWithDiminishingReturns() {
        val once = Bm25.score(matchinfo(100, 50, 50, Triple(1, 10, 5))).relevance
        val twice = Bm25.score(matchinfo(100, 50, 50, Triple(2, 10, 5))).relevance
        val tenTimes = Bm25.score(matchinfo(100, 50, 50, Triple(10, 10, 5))).relevance
        assertTrue(twice > once)
        assertTrue(tenTimes < once * 3)
    }

    @Test
    fun longPageDoesNotWinByLengthAlone() {
        val short = Bm25.score(matchinfo(100, 50, 20, Triple(2, 10, 5))).relevance
        val long = Bm25.score(matchinfo(100, 50, 400, Triple(2, 10, 5))).relevance
        assertTrue(short > long)
    }

    @Test
    fun countsHitsAcrossPhrasesAndAppliesColumnWeights() {
        // Two phrases, two columns: hits in column 1 count at half weight.
        val info = intArrayOf(2, 2, 10, 20, 20, 20, 20) +
            intArrayOf(1, 3, 2, 0, 0, 0) + // phrase 0: column 0 hit, column 1 none
            intArrayOf(0, 0, 0, 2, 4, 3) // phrase 1: column 1 hits only
        val full = Bm25.score(info)
        val weighted = Bm25.score(info, doubleArrayOf(1.0, 0.5))
        assertEquals(3, full.hits)
        assertTrue(weighted.relevance < full.relevance)
    }

    @Test
    fun decodesNativeOrderBlobs() {
        val buffer = ByteBuffer.allocate(8).order(ByteOrder.nativeOrder()).putInt(1).putInt(258)
        assertEquals(listOf(1, 258), Bm25.decode(buffer.array()).toList())
    }

    @Test
    fun malformedInputScoresZero() {
        assertEquals(0.0, Bm25.score(intArrayOf(1, 1)).relevance, 0.0)
        assertEquals(0.0, Bm25.score(intArrayOf(2, 1, 10, 5, 5, 1, 1, 1)).relevance, 0.0)
    }
}
