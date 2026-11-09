package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHighlightsTest {
    @Test
    fun highlightsWordPrefixesAndDerivedMatches() {
        val text = "Paid 1,250.00 on 2 Nov 2026 for the agree-\nment"
        val ranges = SearchHighlights.ranges(text, listOf("1250", "d20261102", "agreement"))
        assertEquals(listOf("1,250.00", "2 Nov 2026", "agree-\nment"), ranges.map { text.substring(it) })
    }

    @Test
    fun prefixHighlightCoversOnlyTheTypedPart() {
        val text = "Lease and leasing"
        assertEquals(listOf("Lea", "lea"), SearchHighlights.ranges(text, listOf("lea")).map { text.substring(it) })
    }
}
