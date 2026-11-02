package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

class DerivedSnippetTest {
    @Test
    fun windowsAroundTheDerivedMatchWithMarkers() {
        val text = (1..30).joinToString(" ") { "w$it" } + " paid 1,250.00 today " + (31..60).joinToString(" ") { "w$it" }
        val snippet = DerivedSnippet.build(text, listOf("1250"), maxWords = 6)
        assertEquals("…w29 w30 paid 1,250.00 today w31…", snippet.text)
        assertEquals(listOf("1,250.00"), snippet.highlightedParts)
    }

    @Test
    fun shortTextWithoutMatchIsShownWhole() {
        val snippet = DerivedSnippet.build("just a page", listOf("zzz"))
        assertEquals("just a page", snippet.text)
        assertEquals(emptyList<String>(), snippet.highlightedParts)
    }
}
