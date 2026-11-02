package `in`.c1ph3rj.scanly.domain.search

/**
 * Typed words that also have a derived form ("1,250" → 1250, "02/11/2026" → d20261102): a page
 * matches with either the words in order or any of [values].
 */
data class DerivedGroup(
    val words: List<String>,
    val values: List<DerivedToken>,
)

/**
 * A parsed search: free terms (matched as word prefixes, so typing "lea" already finds
 * "lease") plus "quoted phrases" that must appear in that exact order.
 *
 * Two operators: `a OR b` (uppercase, so the word "or" is still searchable) matches either
 * side, and `-word` or `-"a phrase"` leaves out pages and names containing it.
 */
data class SearchQuery(
    val raw: String,
    val terms: List<String>,
    val phrases: List<List<String>>,
    /** Parts of the query that also match their derived forms; their words are in [terms] too. */
    val derived: List<DerivedGroup> = emptyList(),
    /** `a OR b OR c` groups: each alternative is one word, or several matched as a phrase. */
    val anyOf: List<List<List<String>>> = emptyList(),
    /** Words or phrases after `-`: matching pages and names are left out. */
    val excluded: List<List<String>> = emptyList(),
) {
    /** Exclusions alone search nothing: there is nothing to look for. */
    val isBlank: Boolean get() = terms.isEmpty() && phrases.isEmpty() && anyOf.isEmpty()

    /**
     * Every folded word the user typed, phrase words included, plus derived values (so a page
     * date written "2 Nov 2026" lights up for "02/11/2026") — used for highlighting. Excluded
     * words are left out: they are what the user doesn't want to see.
     */
    val allWords: List<String>
        get() = (
            phrases.flatten() + terms + anyOf.flatten().flatten() +
                derived.flatMap { group -> group.values.map { it.value } } +
                terms.mapNotNull(::transliterationOf)
            ).distinct()

    /**
     * FTS4 MATCH expression, or null when nothing is worth sending to the index (a lone
     * letter would match most of the library). Tokens are folded `[letters/digits]` only,
     * so they can never inject FTS operators.
     */
    fun toFtsMatch(): String? = ftsGroups(withTransliteration = false) { null }?.allOf

    /** Like [toFtsMatch], but terms must be whole words: "lease" no longer finds "leases". */
    fun toExactFtsMatch(): String? =
        ftsGroups(withTransliteration = false) { term -> listOf(term) }?.withoutExclusions()?.allOf

    /**
     * The index token an English-typed [term] would match in another script ("chennai" →
     * "trsn" for சென்னை), or null for short or non-Latin terms.
     */
    fun transliterationOf(term: String): String? {
        if (term.length < LooseRomanization.MIN_TYPED_LENGTH || !LooseRomanization.isLatin(term)) return null
        val key = LooseRomanization.key(term).takeIf { it.length >= 2 } ?: return null
        return LooseRomanization.TOKEN_PREFIX + key
    }

    /** Whether any term can also match by its English-spelling key. */
    val hasTransliterations: Boolean get() = terms.any { transliterationOf(it) != null }

    /**
     * The query as FTS groups, all of which a page must match. [alternatives] may swap a term
     * for other words (OR-ed together), or return null to keep the term as a prefix. Returns
     * null when there is nothing to search, or a term's alternatives are empty (that part
     * can't match, so neither can the query). [withTransliteration] adds each English-typed
     * term's key for text in other scripts ([transliterationOf]).
     */
    fun ftsGroups(
        withTransliteration: Boolean = false,
        alternatives: (term: String) -> List<String>?,
    ): FtsExpression? {
        val covered = derived.flatMapTo(HashSet()) { it.words }
        val groups = buildList {
            phrases.forEach { words -> add(listOf(phrase(words))) }
            derived.forEach { group ->
                val typed = if (group.words.size > 1) phrase(group.words) else "${group.words.single()}*"
                add(listOf(typed) + group.values.map(::derivedAlternative).distinct())
            }
            anyOf.forEach { alternatives ->
                add(alternatives.map { words -> if (words.size > 1) phrase(words) else "${words.single()}*" })
            }
            terms.filter { it !in covered && isIndexableTerm(it) }.forEach { term ->
                val swapped = alternatives(term)
                val key = transliterationOf(term).takeIf { withTransliteration }
                when {
                    swapped == null -> add(listOfNotNull("$term*", key))
                    swapped.isEmpty() && key == null -> return null
                    else -> add(swapped + listOfNotNull(key))
                }
            }
        }
        val exclusions = excluded.map { words -> if (words.size > 1) phrase(words) else "${words.single()}*" }
        return groups.takeIf { it.isNotEmpty() }?.let { FtsExpression(it, exclusions) }
    }

    private fun isIndexableTerm(term: String): Boolean =
        term.length >= MIN_PREFIX_LENGTH || term.all(Char::isDigit)

    private fun phrase(words: List<String>) = words.joinToString(" ", prefix = "\"", postfix = "\"")

    /** Joined words grow as the user types, so they match as a prefix; numbers and dates don't. */
    private fun derivedAlternative(token: DerivedToken): String =
        if (token.kind == DerivedToken.Kind.JOINED_WORD) "${token.value}*" else token.value

    companion object {
        const val MIN_PREFIX_LENGTH = 2
        private val phrasePattern = Regex("\"([^\"]*)\"?")

        /** `-word` or `-"a phrase"` at the start of a word; "INV-2041" is no exclusion. */
        private val exclusionPattern = Regex("(?<!\\S)-(?:\"([^\"]*)\"?|([\\p{L}\\p{M}\\p{N}][^\\s\"]*))")
        private val whitespace = Regex("\\s+")
        private const val OR = "OR"

        fun parse(raw: String): SearchQuery {
            val excluded = mutableListOf<List<String>>()
            val included = exclusionPattern.replace(raw) { match ->
                val words = foldedWords(match.groupValues[1].ifEmpty { match.groupValues[2] })
                if (words.isNotEmpty()) excluded += words
                " "
            }
            val phrases = mutableListOf<List<String>>()
            val unquoted = phrasePattern.replace(included) { match ->
                val words = foldedWords(match.groupValues[1])
                when {
                    words.size > 1 -> phrases += words
                    // A single quoted word is just a term.
                    words.size == 1 -> return@replace " ${words.first()} "
                }
                " "
            }
            val (anyOf, remainder) = splitAnyOf(unquoted)
            val terms = foldedWords(remainder).distinct()
            return SearchQuery(
                raw = raw,
                terms = terms,
                phrases = phrases.distinct(),
                derived = derivedGroups(remainder),
                anyOf = anyOf,
                excluded = excluded.distinct(),
            )
        }

        /**
         * Pulls `a OR b` runs out of [text]: each side is one whitespace-separated token. A
         * dangling OR ("rent OR") is just ignored so results don't flicker while typing.
         */
        private fun splitAnyOf(text: String): Pair<List<List<List<String>>>, String> {
            val tokens = text.split(whitespace).filter { it.isNotEmpty() }
            val groups = mutableListOf<List<List<String>>>()
            val rest = mutableListOf<String>()
            var index = 0
            while (index < tokens.size) {
                val run = mutableListOf(tokens[index])
                while (index + 2 < tokens.size && tokens[index + 1] == OR && tokens[index + 2] != OR) {
                    run += tokens[index + 2]
                    index += 2
                }
                index++
                val alternatives = run.map(::foldedWords).filter { it.isNotEmpty() }.distinct()
                when {
                    alternatives.size > 1 -> groups += alternatives
                    else -> rest += run
                }
            }
            return groups.distinct() to rest.filter { it != OR }.joinToString(" ")
        }

        /** Overlapping derived tokens ("02-11-2026" is a date and a digit run) form one group. */
        private fun derivedGroups(text: String): List<DerivedGroup> {
            val clusters = mutableListOf<Pair<IntRange, MutableList<DerivedToken>>>()
            // A typed Indic word is searched as written; its English-spelling key is for pages only.
            SearchTextDeriver.derive(text)
                .filter { it.kind != DerivedToken.Kind.TRANSLITERATED }
                .sortedBy { it.sourceRange.first }
                .forEach { token ->
                val last = clusters.lastOrNull()
                if (last != null && token.sourceRange.first <= last.first.last) {
                    clusters[clusters.lastIndex] =
                        (last.first.first..maxOf(last.first.last, token.sourceRange.last)) to last.second.apply { add(token) }
                } else {
                    clusters += token.sourceRange to mutableListOf(token)
                }
            }
            return clusters.mapNotNull { (range, tokens) ->
                val words = foldedWords(text.substring(range.first, range.last + 1))
                // A token that is just the typed word adds nothing ("2026" alone is no date).
                val values = tokens.filter { token -> words.singleOrNull() != token.value }
                if (words.isEmpty() || values.isEmpty()) null else DerivedGroup(words, values)
            }.distinct()
        }

        private fun foldedWords(value: String): List<String> =
            TextFolding.words(TextFolding.fold(value)).map { it.value }.toList()
    }
}
