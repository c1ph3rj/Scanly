package `in`.c1ph3rj.scanly.domain.search

/** A word from the library's own text, with how many pages contain it. */
data class VocabularyTerm(
    val term: String,
    val pageCount: Int,
)

/**
 * "Did you mean …?" built from words that really occur in the user's pages, so suggestions
 * are always searchable and never come from a generic dictionary.
 */
object SpellingSuggester {
    const val MIN_TERM_LENGTH = 4
    private const val TWO_EDITS_MIN_LENGTH = 8

    fun maxEdits(term: String): Int = if (term.length >= TWO_EDITS_MIN_LENGTH) 2 else 1

    /**
     * Picks the closest candidate within the allowed edit distance; ties go to the word found
     * on more pages, then alphabetical order so results are stable.
     */
    fun bestMatch(term: String, candidates: List<VocabularyTerm>): String? {
        if (term.length < MIN_TERM_LENGTH) return null
        val limit = maxEdits(term)
        return candidates.asSequence()
            .filter { it.term != term }
            .map { it to NameMatcher.editDistance(term, it.term) }
            .filter { (_, distance) -> distance <= limit }
            .sortedWith(
                compareBy<Pair<VocabularyTerm, Int>> { it.second }
                    .thenByDescending { it.first.pageCount }
                    .thenBy { it.first.term },
            )
            .firstOrNull()
            ?.first
            ?.term
    }
}
