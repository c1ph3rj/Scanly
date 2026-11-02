package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchCompletionTest {
    @Test
    fun findsTheWordBeingTyped() {
        assertEquals(SearchCompletion.TypedWord(5, "agr", excluded = false), SearchCompletion.typedWord("rent Agr"))
        assertEquals(SearchCompletion.TypedWord(6, "dra", excluded = true), SearchCompletion.typedWord("lease -dra"))
        assertEquals("தமி", SearchCompletion.typedWord("தமி")?.prefix)
    }

    @Test
    fun offersNothingWhenTheWordIsDoneShortOrQuoted() {
        assertNull(SearchCompletion.typedWord("rent "))
        assertNull(SearchCompletion.typedWord("rent a"))
        assertNull(SearchCompletion.typedWord("\"security dep"))
        assertNull(SearchCompletion.typedWord("INV-20"))
        assertNull(SearchCompletion.typedWord("rent OR"))
        assertNull(SearchCompletion.typedWord(""))
    }

    @Test
    fun pageWordsComeFirstByDocumentsThenNameWords() {
        val completions = SearchCompletion.merge(
            prefix = "agr",
            vocabulary = listOf(VocabularyTerm("agra", 1), VocabularyTerm("agreement", 9), VocabularyTerm("agr", 4)),
            nameWords = listOf("agriculture", "agreement"),
            limit = 5,
        )
        assertEquals(
            listOf(Completion("agreement", 9), Completion("agra", 1), Completion("agriculture")),
            completions,
        )
    }

    @Test
    fun applyingReplacesTheTypedWordAndAddsASpace() {
        val typed = SearchCompletion.typedWord("lease -dra")!!
        assertEquals("lease -draft ", SearchCompletion.apply("lease -dra", typed, "draft"))
        val plain = SearchCompletion.typedWord("rent agr")!!
        assertEquals("rent agreement ", SearchCompletion.apply("rent agr", plain, "agreement"))
        assertTrue(SearchCompletion.typedWord(SearchCompletion.apply("rent agr", plain, "agreement")) == null)
    }
}
