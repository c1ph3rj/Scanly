package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SnippetParserTest {
    @Test
    fun stripsMarkersAndRecordsRanges() {
        val parsed = SnippetParser.parse("…The \u0002lease\u0003 begins\non 1 Nov…")
        assertEquals("…The lease begins on 1 Nov…", parsed.text)
        assertEquals("lease", parsed.text.substring(parsed.highlights.first()))
    }

    @Test
    fun countsFourIntegersPerMatch() {
        assertEquals(2, SnippetParser.matchCount("0 0 5 5 0 0 40 5"))
        assertEquals(1, SnippetParser.matchCount(""))
    }
}
