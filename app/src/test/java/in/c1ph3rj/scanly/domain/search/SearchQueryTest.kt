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
        val expression = SearchQuery.parse("INV-2041 or (NEAR*)").ftsGroups { null }!!
        assertEquals(listOf(listOf("\"inv 2041\"", "inv2041*"), listOf("or*"), listOf("near*")), expression.groups)
        assertTrue(expression.anyOf.none { it == '(' || it == ')' || it == '-' })
    }

    @Test
    fun numbersAndDatesAlsoSearchTheirDerivedForms() {
        val amount = SearchQuery.parse("1,250").ftsGroups { null }!!
        assertEquals(listOf(listOf("\"1 250\"", "1250")), amount.groups)
        val date = SearchQuery.parse("rent 02/11/2026").ftsGroups { null }!!
        assertEquals(listOf("\"02 11 2026\"", "d20261102", "d20260211"), date.groups.first())
        assertEquals(listOf("rent*"), date.groups[1])
        assertTrue("d20261102" in SearchQuery.parse("02/11/2026").allWords)
    }

    @Test
    fun derivedWordsStillCountAsTermsForNames() {
        assertEquals(listOf("inv", "2041"), SearchQuery.parse("INV-2041").terms)
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
        assertEquals(listOf(listOf("\"due date\""), listOf("rent*"), listOf("receipt", "recept")), groups!!.groups)
        assertEquals("\"due date\" OR rent* OR receipt OR recept", groups.anyOf)
        assertEquals(listOf(0, 0, 1, 2, 2), groups.termGroups.toList())
        assertEquals(null, groups.allOf)
    }

    @Test
    fun ftsGroupsGiveUpWhenATermHasNoAlternatives() {
        assertNull(SearchQuery.parse("rent zzzz").ftsGroups { term -> if (term == "zzzz") emptyList() else null })
    }

    @Test
    fun minusExcludesWordsAndPhrasesButNotHyphenatedWords() {
        val query = SearchQuery.parse("lease -draft -\"old copy\" INV-2041")
        assertEquals(listOf(listOf("draft"), listOf("old", "copy")), query.excluded)
        assertEquals(listOf("lease", "inv", "2041"), query.terms)
        assertTrue("draft" !in query.allWords)
        val expression = query.ftsGroups { null }!!
        assertEquals(listOf("draft*", "\"old copy\""), expression.excluded)
        assertNull(expression.allOf)
    }

    @Test
    fun exclusionsAloneSearchNothing() {
        assertTrue(SearchQuery.parse("-draft").isBlank)
        assertNull(SearchQuery.parse("-draft").ftsGroups { null })
    }

    @Test
    fun uppercaseOrMakesAnyOfGroups() {
        val query = SearchQuery.parse("rent OR lease OR \"x\" deposit")
        assertEquals(listOf(listOf(listOf("rent"), listOf("lease"), listOf("x"))), query.anyOf)
        assertEquals(listOf("deposit"), query.terms)
        assertEquals(listOf(listOf("rent*", "lease*", "x*"), listOf("deposit*")), query.ftsGroups { null }!!.groups)
    }

    @Test
    fun lowercaseOrAndDanglingOrAreJustWords() {
        assertEquals(listOf("rent", "or", "lease"), SearchQuery.parse("rent or lease").terms)
        val typing = SearchQuery.parse("rent OR")
        assertTrue(typing.anyOf.isEmpty())
        assertEquals(listOf("rent"), typing.terms)
    }

    @Test
    fun exactMatchIgnoresExclusions() {
        assertEquals("lease", SearchQuery.parse("lease -draft").toExactFtsMatch())
    }

    @Test
    fun blankQueryIsBlank() {
        assertTrue(SearchQuery.parse("  ,, ").isBlank)
    }
}
