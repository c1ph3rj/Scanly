package `in`.c1ph3rj.scanly.domain.search

/** A word offered to finish what is being typed, and how many documents contain it (if known). */
data class Completion(
    val word: String,
    val documentCount: Int? = null,
)

/**
 * Word autocomplete for the search box: finds the word being typed and finishes it with words
 * that really occur in the library, page text first, then document and folder names.
 */
object SearchCompletion {
    const val MIN_PREFIX_LENGTH = 2
    private val wordOnly = Regex("[\\p{L}\\p{M}\\p{N}]+")

    /** The word being typed: where it starts in the raw query, folded, and whether it is `-excluded`. */
    data class TypedWord(val start: Int, val prefix: String, val excluded: Boolean)

    /**
     * The last word of [query] while it is still being typed, or null: after a space (the word is
     * done), inside an open quote (a phrase), or when it is too short or more than one word.
     */
    fun typedWord(query: String): TypedWord? {
        if (query.isEmpty() || query.last().isWhitespace()) return null
        if (query.count { it == '"' } % 2 == 1) return null
        val start = query.indexOfLast { it.isWhitespace() } + 1
        var token = query.substring(start)
        val excluded = token.startsWith('-')
        if (excluded) token = token.drop(1)
        val folded = TextFolding.fold(token)
        if (folded.length < MIN_PREFIX_LENGTH || !wordOnly.matches(folded) || token == "OR") return null
        return TypedWord(start, folded, excluded)
    }

    /**
     * Up to [limit] completions for [prefix]: library words by how many documents have them,
     * then words from names. The typed word itself is left out.
     */
    fun merge(prefix: String, vocabulary: List<VocabularyTerm>, nameWords: List<String>, limit: Int): List<Completion> {
        val fromPages = vocabulary
            .filter { it.term.startsWith(prefix) && it.term != prefix }
            .sortedWith(compareByDescending<VocabularyTerm> { it.pageCount }.thenBy { it.term })
            .map { Completion(it.term, it.pageCount) }
        val fromNames = nameWords
            .filter { it.startsWith(prefix) && it != prefix }
            .distinct()
            .sorted()
            .map { Completion(it) }
        return (fromPages + fromNames).distinctBy { it.word }.take(limit)
    }

    /** [query] with the typed word replaced by [word] and a space, ready for the next word. */
    fun apply(query: String, typed: TypedWord, word: String): String =
        query.substring(0, typed.start) + (if (typed.excluded) "-" else "") + word + " "
}
