package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchQueryTest {
    @Test
    fun foldsCaseAndAccentsIntoTerms() {
        val query = SearchQuery.parse("  Résumé  LEASE ")
        assertEquals(listOf("resume", "lease"), query.terms)
    }

    @Test
    fun quotedWordsBecomePhrasesAndLoneQuotedWordIsATerm() {
        val query = SearchQuery.parse("\"security deposit\" \"keys\" rent")
        assertEquals(listOf(listOf("security", "deposit")), query.phrases)
        assertEquals(listOf("keys", "rent"), query.terms)
    }

    @Test
    fun unclosedQuoteStillFormsPhraseWhileTyping() {
        assertEquals(listOf(listOf("lease", "begins")), SearchQuery.parse("\"lease begins").phrases)
    }

    @Test
    fun ftsMatchUsesPrefixTermsAndExactPhrases() {
        assertEquals("\"security deposit\" rent*", SearchQuery.parse("\"security deposit\" rent").toFtsMatch())
    }

    @Test
    fun singleLettersAreNotSentToTheIndexButDigitsAre() {
        assertNull(SearchQuery.parse("a").toFtsMatch())
        assertEquals("7*", SearchQuery.parse("7").toFtsMatch())
    }

    @Test
    fun operatorsAndPunctuationCannotReachFts() {
        val match = SearchQuery.parse("INV-2041 OR (NEAR*)").toFtsMatch()!!
        assertEquals("inv* 2041* or* near*", match)
        assertTrue(match.none { it == '(' || it == ')' || it == '-' })
    }

    @Test
    fun indicWordsStayWholeTermsMatchingTheIndex() {
        assertEquals(listOf("தமிழ்நாடு"), SearchQuery.parse("தமிழ்நாடு").terms)
        assertEquals("प्रधानमंत्री*", SearchQuery.parse("प्रधानमंत्री").toFtsMatch())
    }

    @Test
    fun exactMatchDropsPrefixStars() {
        assertEquals("\"security deposit\" rent", SearchQuery.parse("\"security deposit\" rent").toExactFtsMatch())
        assertNull(SearchQuery.parse("a").toExactFtsMatch())
    }

    @Test
    fun ftsGroupsSwapOnlyTheTermsGivenAlternatives() {
        val groups = SearchQuery.parse("\"due date\" rent reciept").ftsGroups { term ->
            if (term == "reciept") listOf("receipt", "recept") else null
        }
        assertEquals(listOf("\"due date\"", "rent*", "receipt OR recept"), groups)
    }

    @Test
    fun ftsGroupsGiveUpWhenATermHasNoAlternatives() {
        assertNull(SearchQuery.parse("rent zzzz").ftsGroups { term -> if (term == "zzzz") emptyList() else null })
    }

    @Test
    fun blankQueryIsBlank() {
        assertTrue(SearchQuery.parse("  ,, ").isBlank)
    }
}
