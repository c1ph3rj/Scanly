package `in`.c1ph3rj.scanly.domain.search

import com.ibm.icu.text.Transliterator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Index and query sides of English-spelling search meet on the same token. */
class TransliterationSearchTest {
    private val transliterator = Transliterator.getInstance("Any-Latin; Latin-ASCII; Lower")

    @Before
    fun installRomanizer() {
        SearchTextDeriver.romanizer = Romanizer { word ->
            if (word.any { it.code > 0x024F && it.isLetter() }) transliterator.transliterate(word) else null
        }
    }

    @After
    fun removeRomanizer() {
        SearchTextDeriver.romanizer = null
    }

    @Test
    fun indicWordsGetTheKeyTheEnglishSpellingSearches() {
        val text = "சென்னை மாநகராட்சி"
        val token = SearchTextDeriver.derive(text).first { it.kind == DerivedToken.Kind.TRANSLITERATED }
        assertEquals("சென்னை", text.substring(token.sourceRange))
        assertEquals(token.value, SearchQuery.parse("Chennai").transliterationOf("chennai"))
    }

    @Test
    fun latinWordsGetNoKey() {
        assertTrue(SearchTextDeriver.derive("Chennai office").none { it.kind == DerivedToken.Kind.TRANSLITERATED })
    }

    @Test
    fun typedIndicWordsAreSearchedAsWritten() {
        val query = SearchQuery.parse("தமிழ்நாடு")
        assertTrue(query.derived.isEmpty())
        assertEquals("தமிழ்நாடு*", query.toFtsMatch())
    }

    @Test
    fun queriesAddTheKeyOnlyWhenAsked() {
        val query = SearchQuery.parse("chennai")
        assertEquals(listOf(listOf("chennai*")), query.ftsGroups { null }!!.groups)
        assertEquals(listOf(listOf("chennai*", "trsn")), query.ftsGroups(withTransliteration = true) { null }!!.groups)
        assertEquals("chennai*", query.toFtsMatch())
        assertNull(SearchQuery.parse("ab").transliterationOf("ab"))
        assertNull(SearchQuery.parse("தமிழ்").transliterationOf("தமிழ்"))
    }

    @Test
    fun highlightsTheIndicWordForTheEnglishSpelling() {
        val text = "சென்னை மாநகராட்சி"
        val ranges = SearchHighlights.ranges(text, SearchQuery.parse("chennai").allWords)
        assertEquals(listOf("சென்னை"), ranges.map { text.substring(it) })
    }
}
