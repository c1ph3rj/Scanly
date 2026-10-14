package `in`.c1ph3rj.scanly.domain.search

import `in`.c1ph3rj.scanly.domain.model.HighlightedText

/** Decodes FTS4 `snippet()` / `offsets()` output produced with \u0002 / \u0003 match markers. */
object SnippetParser {
    const val MATCH_START = '\u0002'
    const val MATCH_END = '\u0003'
    private val whitespace = Regex("\\s+")

    fun parse(snippet: String): HighlightedText {
        // Collapse line breaks first: snippets are shown inline, and markers survive this.
        val flattened = whitespace.replace(snippet, " ").trim()
        val text = StringBuilder(flattened.length)
        val highlights = mutableListOf<IntRange>()
        var start = -1
        flattened.forEach { char ->
            when (char) {
                MATCH_START -> start = text.length
                MATCH_END -> {
                    if (start in 0 until text.length) highlights += start until text.length
                    start = -1
                }
                else -> text.append(char)
            }
        }
        return HighlightedText(text.toString(), highlights)
    }

    /** FTS4 `offsets()` is four integers per matched token occurrence. */
    fun matchCount(offsets: String): Int {
        val numbers = offsets.trim().split(' ').count { it.isNotEmpty() }
        return (numbers / 4).coerceAtLeast(1)
    }
}
