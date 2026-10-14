package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpellingSuggesterTest {
    private val vocabulary = listOf(
        VocabularyTerm("website", 4),
        VocabularyTerm("websites", 1),
        VocabularyTerm("lease", 9),
        VocabularyTerm("least", 2),
        VocabularyTerm("agreement", 3),
    )

    @Test
    fun suggestsClosestWordFromThePages() {
        assertEquals("website", SpellingSuggester.bestMatch("webiste", vocabulary))
    }

    @Test
    fun prefersMoreFrequentWordOnEqualDistance() {
        assertEquals("lease", SpellingSuggester.bestMatch("leasr", vocabulary))
    }

    @Test
    fun longerWordsAllowTwoEdits() {
        assertEquals("agreement", SpellingSuggester.bestMatch("agremnent", vocabulary))
    }

    @Test
    fun shortOrDistantWordsGetNoSuggestion() {
        assertNull(SpellingSuggester.bestMatch("lea", vocabulary))
        assertNull(SpellingSuggester.bestMatch("invoice", vocabulary))
    }
}
