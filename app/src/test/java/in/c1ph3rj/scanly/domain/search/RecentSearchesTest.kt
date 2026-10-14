package `in`.c1ph3rj.scanly.domain.search

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {
    @Test
    fun newestFirstWithoutDuplicates() {
        assertEquals(listOf("Lease", "invoice"), RecentSearches.add(listOf("invoice", "lease"), "  Lease "))
    }

    @Test
    fun blankQueriesAreIgnored() {
        assertEquals(listOf("a"), RecentSearches.add(listOf("a"), "   "))
    }

    @Test
    fun keepsAtMostEight() {
        val full = (1..8).map { "q$it" }
        val updated = RecentSearches.add(full, "new")
        assertEquals(RecentSearches.MAX_ENTRIES, updated.size)
        assertEquals("new", updated.first())
        assertEquals("q7", updated.last())
    }
}
