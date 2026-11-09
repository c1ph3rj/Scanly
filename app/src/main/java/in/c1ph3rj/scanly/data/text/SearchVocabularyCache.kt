package `in`.c1ph3rj.scanly.data.text

import `in`.c1ph3rj.scanly.domain.search.VocabularyTerm

/**
 * Recent vocabulary lookups (words by first letter and length), so typing a query doesn't
 * rescan the same slice of the index on every keystroke. Cleared whenever page text is
 * written; entries also expire, since pages can disappear through deletes elsewhere.
 */
internal class SearchVocabularyCache(
    private val maxEntries: Int = 64,
    private val maxAgeMillis: Long = 30_000,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    data class Key(val first: Char, val minLength: Int, val maxLength: Int)

    private class Entry(val terms: List<VocabularyTerm>, val storedAtMillis: Long)

    private val entries = object : LinkedHashMap<Key, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>): Boolean = size > maxEntries
    }

    fun getOrPut(key: Key, load: () -> List<VocabularyTerm>): List<VocabularyTerm> {
        val now = clock()
        synchronized(entries) {
            entries[key]?.takeIf { now - it.storedAtMillis <= maxAgeMillis }?.let { return it.terms }
        }
        val terms = load()
        synchronized(entries) { entries[key] = Entry(terms, now) }
        return terms
    }

    fun clear() = synchronized(entries) { entries.clear() }
}
