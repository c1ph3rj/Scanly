package `in`.c1ph3rj.scanly.domain.search

import `in`.c1ph3rj.scanly.domain.model.HighlightedText
import kotlin.math.min

data class NameMatch(
    /** Higher is better; roughly 0.3 (typo-tolerant) to 1.5 (exact name). */
    val score: Double,
    val title: HighlightedText,
)

/**
 * Matches a document or folder name against a query. Every word the user typed must match
 * somewhere in the name, trying (best first): the start of a word, anywhere in the name,
 * then one-typo tolerance for longer words ("reciept" still finds "Receipt").
 */
object NameMatcher {
    private const val EXACT_SCORE = 1.5
    private const val LEADING_PREFIX_SCORE = 1.1
    private const val PREFIX_SCORE = 1.0
    private const val INFIX_SCORE = 0.6
    private const val FUZZY_SCORE = 0.35
    private const val FUZZY_MIN_LENGTH = 4
    private const val FUZZY_TWO_EDITS_MIN_LENGTH = 8

    fun match(title: String, query: SearchQuery): NameMatch? {
        if (query.isBlank) return null
        val folded = FoldedText.of(title)
        val words = TextFolding.words(folded.text).toList()
        val highlights = mutableListOf<IntRange>()
        var total = 0.0
        var parts = 0

        query.phrases.forEach { phrase ->
            val needle = phrase.joinToString(" ")
            val start = folded.text.indexOf(needle).takeIf { it >= 0 } ?: return null
            highlights += folded.toSourceRange(start until start + needle.length)
            total += if (words.any { it.range.first == start }) PREFIX_SCORE else INFIX_SCORE
            parts++
        }
        query.terms.forEach { term ->
            val termMatch = matchTerm(term, folded.text, words) ?: return null
            highlights += folded.toSourceRange(termMatch.range)
            total += termMatch.score
            parts++
        }
        val normalizedTitle = words.joinToString(" ") { it.value }
        val normalizedQuery = (query.phrases.flatten() + query.terms).joinToString(" ")
        val score = if (normalizedTitle == normalizedQuery) EXACT_SCORE else total / parts
        return NameMatch(score, HighlightedText(title, mergeRanges(highlights)))
    }

    private data class TermMatch(val range: IntRange, val score: Double)

    private fun matchTerm(term: String, text: String, words: List<MatchResult>): TermMatch? {
        words.forEachIndexed { index, word ->
            if (word.value.startsWith(term)) {
                return TermMatch(
                    word.range.first until word.range.first + term.length,
                    if (index == 0) LEADING_PREFIX_SCORE else PREFIX_SCORE,
                )
            }
        }
        val infix = text.indexOf(term)
        if (infix >= 0) return TermMatch(infix until infix + term.length, INFIX_SCORE)
        if (term.length < FUZZY_MIN_LENGTH) return null
        val allowedEdits = if (term.length >= FUZZY_TWO_EDITS_MIN_LENGTH) 2 else 1
        return words.firstOrNull { word ->
            // Compare against the word's same-length prefix too, so partially typed words still fuzz.
            val candidate = word.value
            editDistance(term, candidate) <= allowedEdits ||
                (candidate.length > term.length &&
                    editDistance(term, candidate.take(term.length)) <= allowedEdits)
        }?.let { TermMatch(it.range, FUZZY_SCORE) }
    }

    /** Optimal string alignment distance (Levenshtein + adjacent transpositions). */
    internal fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val rows = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) rows[i][0] = i
        for (j in 0..b.length) rows[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                var best = min(min(rows[i - 1][j] + 1, rows[i][j - 1] + 1), rows[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    best = min(best, rows[i - 2][j - 2] + 1)
                }
                rows[i][j] = best
            }
        }
        return rows[a.length][b.length]
    }

    internal fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.size < 2) return ranges
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf(sorted.first())
        sorted.drop(1).forEach { range ->
            val last = merged.last()
            if (range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }
}
