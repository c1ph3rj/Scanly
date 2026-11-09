package `in`.c1ph3rj.scanly.domain.search

/**
 * Where a page's text matches a query: words starting with a query word (case- and
 * accent-insensitive, like the index), plus text whose derived form matches ("1,250" for
 * 1250, "agree-\nment" for agreement, "2 Nov 2026" for 02/11/2026).
 */
object SearchHighlights {
    /** Merged source ranges in [text] matching any of the folded [words]. */
    fun ranges(text: String, words: List<String>): List<IntRange> {
        if (words.isEmpty() || text.isEmpty()) return emptyList()
        val folded = FoldedText.of(text)
        val wordRanges = TextFolding.words(folded.text).mapNotNull { word ->
            val prefix = words.filter { word.value.startsWith(it) }.maxOfOrNull { it.length } ?: return@mapNotNull null
            folded.toSourceRange(word.range.first until word.range.first + prefix)
        }.toList()
        val derivedRanges = SearchTextDeriver.derive(text)
            .filter { token ->
                words.any { word ->
                    if (token.kind == DerivedToken.Kind.JOINED_WORD) token.value.startsWith(word) else token.value == word
                }
            }
            .map { it.sourceRange }
        return NameMatcher.mergeRanges(wordRanges + derivedRanges)
    }
}
