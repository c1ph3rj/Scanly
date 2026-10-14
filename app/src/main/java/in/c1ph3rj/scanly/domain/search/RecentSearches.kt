package `in`.c1ph3rj.scanly.domain.search

/** Most-recent-first list of past queries, de-duplicated ignoring case and spacing. */
object RecentSearches {
    const val MAX_ENTRIES = 8

    fun add(current: List<String>, query: String): List<String> {
        val cleaned = query.trim().replace(Regex("\\s+"), " ")
        if (cleaned.isEmpty()) return current
        return (listOf(cleaned) + current.filterNot { it.equals(cleaned, ignoreCase = true) })
            .take(MAX_ENTRIES)
    }
}
