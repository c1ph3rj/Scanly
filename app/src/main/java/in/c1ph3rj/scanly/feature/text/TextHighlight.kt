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
import `in`.c1ph3rj.scanly.domain.search.FoldedText

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
    highlights.forEach { range ->
        val start = range.first.coerceIn(0, text.length)
        val end = (range.last + 1).coerceIn(start, text.length)
        if (end > start) addStyle(style, start, end)
    }
}

/**
 * Highlights every word in [text] that starts with one of [words] (already folded), matching
 * how the search index treats typed words as prefixes. Case- and accent-insensitive.
 */
fun highlightWordPrefixes(text: String, words: List<String>, style: SpanStyle): AnnotatedString {
    if (words.isEmpty() || text.isEmpty()) return AnnotatedString(text)
    val folded = FoldedText.of(text)
    return buildAnnotatedString {
        append(text)
        wordPattern.findAll(folded.text).forEach { word ->
            val prefix = words.filter { word.value.startsWith(it) }.maxOfOrNull { it.length } ?: return@forEach
            val source = folded.toSourceRange(word.range.first until word.range.first + prefix)
            addStyle(style, source.first, source.last + 1)
        }
    }
}

private val wordPattern = Regex("[\\p{L}\\p{N}]+")
