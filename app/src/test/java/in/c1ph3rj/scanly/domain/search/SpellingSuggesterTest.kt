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
    fun closeMatchesListNearWordsClosestFirstUpToTheLimit() {
        assertEquals(listOf("lease", "least"), SpellingSuggester.closeMatches("leasr", vocabulary, limit = 4))
        assertEquals(listOf("lease"), SpellingSuggester.closeMatches("leasr", vocabulary, limit = 1))
    }

    @Test
    fun closeMatchesIgnoreShortWords() {
        assertEquals(emptyList<String>(), SpellingSuggester.closeMatches("lea", vocabulary, limit = 4))
    }

    @Test
    fun ocrVariantsFindMisreadsButNotTypos() {
        val pages = listOf(
            VocabularyTerm("invoice", 8),
            VocabularyTerm("invoices", 2),
            VocabularyTerm("lnvoice", 3),
            VocabularyTerm("invoace", 1),
            VocabularyTerm("rnodel", 1),
        )
        assertEquals(listOf("lnvoice"), SpellingSuggester.ocrVariants("invoice", pages, limit = 3))
        assertEquals(listOf("rnodel"), SpellingSuggester.ocrVariants("model", pages, limit = 3))
        assertEquals(emptyList<String>(), SpellingSuggester.ocrVariants("1250", pages, limit = 3))
    }

    @Test
    fun closeMatchesPreferOcrLookAlikesOverTypos() {
        val pages = listOf(VocabularyTerm("recelpt", 1), VocabularyTerm("receipz", 9))
        assertEquals(listOf("recelpt", "receipz"), SpellingSuggester.closeMatches("receipt", pages, limit = 2))
    }

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
