package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NameMatcherTest {
    private fun match(title: String, query: String) = NameMatcher.match(title, SearchQuery.parse(query))

    @Test
    fun wordPrefixHighlightsTypedPart() {
        val result = match("Lease agreement", "lea")!!
        assertEquals(listOf(0..2), result.title.highlights)
    }

    @Test
    fun everyTermMustMatch() {
        assertNotNull(match("Lease agreement", "agree lease"))
        assertNull(match("Lease agreement", "lease invoice"))
    }

    @Test
    fun accentsMatchAndHighlightOriginalCharacters() {
        val result = match("Mon résumé", "resume")!!
        assertEquals(listOf(4..9), result.title.highlights)
    }

    @Test
    fun oneTypoIsToleratedForLongerWords() {
        assertNotNull(match("Grocery receipt", "reciept"))
        assertNull(match("Tax", "tex"))
    }

    @Test
    fun exactAndLeadingMatchesOutrankInfixAndFuzzy() {
        val exact = match("Invoice", "invoice")!!.score
        val leading = match("Invoice March", "invoice")!!.score
        val later = match("March invoice", "invoice")!!.score
        val infix = match("Reinvoiced", "invoice")!!.score
        val fuzzy = match("Invoise", "invoice")!!.score
        assertTrue(exact > leading && leading > later && later > infix && infix > fuzzy)
    }

    @Test
    fun editDistanceCountsTranspositionAsOne() {
        assertEquals(1, NameMatcher.editDistance("receipt", "reciept"))
        assertEquals(0, NameMatcher.editDistance("same", "same"))
        assertEquals(3, NameMatcher.editDistance("", "abc"))
    }

    @Test
    fun overlappingHighlightsMerge() {
        assertEquals(listOf(0..5, 8..9), NameMatcher.mergeRanges(listOf(3..5, 0..3, 8..9)))
    }
}
