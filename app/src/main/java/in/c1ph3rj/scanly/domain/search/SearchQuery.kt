package `in`.c1ph3rj.scanly.domain.search

/**
 * A parsed search: free terms (matched as word prefixes, so typing "lea" already finds
 * "lease") plus "quoted phrases" that must appear in that exact order.
 */
data class SearchQuery(
    val raw: String,
    val terms: List<String>,
    val phrases: List<List<String>>,
) {
    val isBlank: Boolean get() = terms.isEmpty() && phrases.isEmpty()

    /** Every folded word the user typed, phrase words included — used for highlighting. */
    val allWords: List<String> get() = (phrases.flatten() + terms).distinct()

    /**
     * FTS4 MATCH expression, or null when nothing is worth sending to the index (a lone
     * letter would match most of the library). Tokens are folded `[letters/digits]` only,
     * so they can never inject FTS operators.
     */
    fun toFtsMatch(): String? {
        val parts = buildList {
            phrases.forEach { words -> add(words.joinToString(" ", prefix = "\"", postfix = "\"")) }
            terms.filter(::isIndexableTerm).forEach { term -> add("$term*") }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun isIndexableTerm(term: String): Boolean =
        term.length >= MIN_PREFIX_LENGTH || term.all(Char::isDigit)

    companion object {
        const val MIN_PREFIX_LENGTH = 2
        private val phrasePattern = Regex("\"([^\"]*)\"?")

        fun parse(raw: String): SearchQuery {
            val phrases = mutableListOf<List<String>>()
            val remainder = phrasePattern.replace(raw) { match ->
                val words = foldedWords(match.groupValues[1])
                when {
                    words.size > 1 -> phrases += words
                    // A single quoted word is just a term.
                    words.size == 1 -> return@replace " ${words.first()} "
                }
                " "
            }
            val terms = foldedWords(remainder).distinct()
            return SearchQuery(raw = raw, terms = terms, phrases = phrases.distinct())
        }

        private fun foldedWords(value: String): List<String> =
            TextFolding.words(TextFolding.fold(value)).map { it.value }.toList()
    }
}
