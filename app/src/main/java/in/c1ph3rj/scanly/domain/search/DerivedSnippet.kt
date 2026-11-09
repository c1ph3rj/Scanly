package `in`.c1ph3rj.scanly.domain.search

import `in`.c1ph3rj.scanly.domain.model.HighlightedText

/**
 * A snippet built in Kotlin for pages the index matched only through derived forms: FTS
 * snippets come from the text column, so a page found via "1250" for "1,250" would otherwise
 * show no highlight at all.
 */
object DerivedSnippet {
    private val token = Regex("\\S+")
    private const val WORDS_BEFORE_MATCH = 3

    fun build(text: String, words: List<String>, maxWords: Int = 14): HighlightedText {
        val tokens = token.findAll(text).map { it.range }.toList()
        if (tokens.isEmpty()) return HighlightedText("")
        val highlights = SearchHighlights.ranges(text, words)
        val firstMatch = highlights.firstOrNull()?.first ?: 0
        val matchToken = tokens.indexOfFirst { it.last >= firstMatch }.coerceAtLeast(0)
        val start = (matchToken - WORDS_BEFORE_MATCH).coerceAtLeast(0)
        val end = (start + maxWords).coerceAtMost(tokens.size)
        val windowStart = tokens[start].first
        val windowEnd = tokens[end - 1].last + 1
        // Same markers the index uses, so SnippetParser does the rest.
        val marked = buildString {
            if (start > 0) append("…")
            var cursor = windowStart
            highlights.forEach { range ->
                val from = range.first.coerceIn(windowStart, windowEnd)
                val to = (range.last + 1).coerceIn(windowStart, windowEnd)
                if (to <= from || from < cursor) return@forEach
                append(text, cursor, from)
                append(SnippetParser.MATCH_START).append(text, from, to).append(SnippetParser.MATCH_END)
                cursor = to
            }
            append(text, cursor, windowEnd)
            if (end < tokens.size) append("…")
        }
        return SnippetParser.parse(marked)
    }
}
