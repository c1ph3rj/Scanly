package `in`.c1ph3rj.scanly.feature.text

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import `in`.c1ph3rj.scanly.domain.model.HighlightedText
import `in`.c1ph3rj.scanly.domain.search.NameMatcher
import `in`.c1ph3rj.scanly.domain.search.SearchQuery
import `in`.c1ph3rj.scanly.domain.search.SnippetParser
import org.junit.Assert.assertEquals
import org.junit.Test

class TextHighlightTest {
    private val style = SpanStyle(background = Color.Yellow)

    @Test
    fun tamilQueryKeepsVowelSignsAndPulliWithTheirLetters() {
        val text = "தமிழ்நாடு அரசு"
        val result = highlight(text, "தமிழ்")

        assertEquals(text, result.text)
        assertEquals(listOf(0 until "தமிழ்".length), result.ranges())
    }

    @Test
    fun partialTamilQueryHighlightsWholeMi() {
        assertEquals(listOf(0 until "தமி".length), highlight("தமிழ்நாடு", "தம").ranges())
    }

    @Test
    fun repeatedTamilWordsKeepSeparateHighlights() {
        assertEquals(listOf(0..4, 11..15), highlight("தமிழ் அரசு தமிழ்", "தமிழ்").ranges())
    }

    @Test
    fun searchTitlesAndSnippetsAlsoKeepWholeTamilCharacters() {
        val title = NameMatcher.match("தமிழ்நாடு", SearchQuery.parse("தமிழ்"))!!.title
        val snippet = SnippetParser.parse("\u0002தம\u0003ி\u0002ழ\u0003்நாடு")

        assertEquals(listOf(0..4), title.toAnnotatedString(style).ranges())
        assertEquals(listOf(0..4), snippet.toAnnotatedString(style).ranges())
    }

    @Test
    fun rangeStartingOnAVowelSignIncludesItsBaseLetter() {
        assertEquals(listOf(0..1), HighlightedText("மி", listOf(1..1)).toAnnotatedString(style).ranges())
    }

    @Test
    fun decomposedLatinAccentStaysInsideHighlight() {
        assertEquals(listOf(0..4), highlight("cafe\u0301 receipt", "cafe").ranges())
    }

    @Test
    fun englishPrefixesAndAccentInsensitiveSearchStillWork() {
        assertEquals(listOf(0..2, 6..11), highlight("Lease résumé", "lea resume").ranges())
    }

    @Test
    fun emptyQueriesAndInvalidRangesLeaveTextUnstyled() {
        assertEquals(emptyList<IntRange>(), highlight("தமிழ்", "").ranges())
        assertEquals(emptyList<IntRange>(), highlight("", "தமிழ்").ranges())
        assertEquals(emptyList<IntRange>(), HighlightedText("abc", listOf(9..12)).toAnnotatedString(style).ranges())
    }

    private fun highlight(text: String, query: String): AnnotatedString =
        highlightWordPrefixes(text, SearchQuery.parse(query).allWords, style)

    private fun AnnotatedString.ranges(): List<IntRange> = spanStyles.map { it.start until it.end }
}
