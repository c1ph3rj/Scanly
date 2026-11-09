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
    fun bestMatch(term: String, candidates: List<VocabularyTerm>): String? =
        closeMatches(term, candidates, limit = 1).firstOrNull()

    /**
     * Up to [limit] candidates within the allowed edit distance, closest first, the same order
     * [bestMatch] uses. These are the words searched in place of a typed word nothing matched.
     * OCR look-alikes count as less than a full edit (see [OcrEditDistance]).
     */
    fun closeMatches(term: String, candidates: List<VocabularyTerm>, limit: Int): List<String> {
        if (term.length < MIN_TERM_LENGTH) return emptyList()
        return closest(term, candidates, maxEdits(term).toDouble(), limit) { it.term != term }
    }

    /**
     * Words in the pages that look like [term] misread by OCR ("lnvoice" for "invoice"):
     * reachable only through look-alike characters, never a plain typo. Words that start with
     * [term] are left out, since the typed search already finds them.
     */
    fun ocrVariants(term: String, candidates: List<VocabularyTerm>, limit: Int): List<String> {
        if (term.length < MIN_TERM_LENGTH || term.all(Char::isDigit)) return emptyList()
        return closest(term, candidates, MAX_OCR_VARIANT_COST, limit) { !it.term.startsWith(term) }
    }

    /** Most OCR look-alike cost a variant may have; any plain edit costs a full 1. */
    private const val MAX_OCR_VARIANT_COST = 0.8

    private fun closest(
        term: String,
        candidates: List<VocabularyTerm>,
        maxCost: Double,
        limit: Int,
        keep: (VocabularyTerm) -> Boolean,
    ): List<String> = candidates.asSequence()
        .filter(keep)
        .map { it to OcrEditDistance.distance(term, it.term, maxCost) }
        .filter { (_, distance) -> distance <= maxCost }
        .sortedWith(
            compareBy<Pair<VocabularyTerm, Double>> { it.second }
                .thenByDescending { it.first.pageCount }
                .thenBy { it.first.term },
        )
        .take(limit)
        .map { it.first.term }
        .toList()
}
