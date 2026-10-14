package `in`.c1ph3rj.scanly.domain.repository

import kotlinx.coroutines.flow.Flow

/** Recent library searches, kept only on this device. */
interface SearchHistoryRepository {
    fun observeRecentSearches(): Flow<List<String>>

    suspend fun addRecentSearch(query: String)

    suspend fun removeRecentSearch(query: String)

    suspend fun clearRecentSearches()
}
