package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProximityTest {
    private val twoParts = intArrayOf(0, 1)

    @Test
    fun adjacentWordsScoreHigherThanDistantOnes() {
        // "rent agreement" vs "rent" … 600 bytes … "agreement".
        val near = Proximity.bonus("0 0 10 4 0 1 15 9", twoParts, 2)
        val far = Proximity.bonus("0 0 10 4 0 1 600 9", twoParts, 2)
        assertTrue(near > far)
        assertTrue(near > 0.7)
    }

    @Test
    fun picksTheTightestWindowAmongRepeats() {
        val spread = Proximity.bonus("0 0 0 4 0 1 900 9 0 0 880 4", twoParts, 2)
        val tight = Proximity.bonus("0 0 880 4 0 1 900 9", twoParts, 2)
        assertEquals(tight, spread, 1e-9)
    }

    @Test
    fun phraseWordsBelongToOnePart() {
        // Query "\"due date\" rent": terms 0 and 1 are the phrase, 2 is rent.
        val bonus = Proximity.bonus("0 0 0 3 0 1 4 4 0 2 10 4", intArrayOf(0, 0, 1), 2)
        assertTrue(bonus > 0.8)
    }

    @Test
    fun singlePartOrMissingPartGivesNoBonus() {
        assertEquals(0.0, Proximity.bonus("0 0 0 4", intArrayOf(0), 1), 0.0)
        assertEquals(0.0, Proximity.bonus("0 0 0 4 0 0 9 4", twoParts, 2), 0.0)
    }

    @Test
    fun partsInDifferentColumnsDoNotCount() {
        assertEquals(0.0, Proximity.bonus("0 0 0 4 1 1 5 4", twoParts, 2), 0.0)
    }
}
