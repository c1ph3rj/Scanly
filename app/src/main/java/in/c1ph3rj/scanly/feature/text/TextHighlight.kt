package `in`.c1ph3rj.scanly.feature.text

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import `in`.c1ph3rj.scanly.domain.model.HighlightedText
import `in`.c1ph3rj.scanly.domain.search.SearchHighlights
import java.text.BreakIterator
import java.util.Locale

/**
 * The "marker pen" used for search matches: warm yellow on light surfaces, a softer amber wash
 * on dark ones so it stays legible without glowing.
 */
@Composable
@ReadOnlyComposable
fun matchHighlightStyle(): SpanStyle {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return SpanStyle(
        background = if (dark) Color(0x66E0B53A) else Color(0xFFFFE58A),
        color = if (dark) Color(0xFFFFF4D6) else Color(0xFF1F1A05),
        fontWeight = FontWeight.Medium,
    )
}

fun HighlightedText.toAnnotatedString(style: SpanStyle): AnnotatedString = buildAnnotatedString {
    append(text)
    characterHighlightRanges().forEach { range -> addStyle(style, range.first, range.last + 1) }
}

/**
 * Search offsets refer to code units, not displayed characters. In Tamil, for example,
 * a prefix can end between ம and its vowel sign ி, or between a consonant and its pulli (்).
 * Keep the entire character in one style run, including for index-provided snippets.
 */
private fun HighlightedText.characterHighlightRanges(): List<IntRange> {
    if (text.isEmpty() || highlights.isEmpty()) return emptyList()
    val characters = BreakIterator.getCharacterInstance(Locale.ROOT)
    characters.setText(text)
    val expanded = highlights.mapNotNull { range ->
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (end <= start) return@mapNotNull null
        val characterStart = if (characters.isBoundary(start)) start else characters.preceding(start)
        val characterEnd = if (characters.isBoundary(end)) end else characters.following(end)
        characterStart until characterEnd
    }.sortedBy { it.first }
    // Expanding neighboring matches can make them touch; use one continuous style run.
    val merged = mutableListOf<IntRange>()
    expanded.forEach { range ->
        val previous = merged.lastOrNull()
        if (previous != null && range.first <= previous.last + 1) {
            merged[merged.lastIndex] = previous.first..maxOf(previous.last, range.last)
        } else {
            merged += range
        }
    }
    return merged
}

/**
 * Highlights every word in [text] that starts with one of [words] (already folded), matching
 * how the search index treats typed words as prefixes, plus text whose derived form matches
 * ("1,250" for 1250). Case- and accent-insensitive.
 */
fun highlightWordPrefixes(text: String, words: List<String>, style: SpanStyle): AnnotatedString {
    if (words.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    return HighlightedText(text, SearchHighlights.ranges(text, words)).toAnnotatedString(style)
}
