package `in`.c1ph3rj.scanly.data.search

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.domain.repository.SearchHistoryRepository
import `in`.c1ph3rj.scanly.domain.search.RecentSearches
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.searchHistoryDataStore by preferencesDataStore(name = "search_history")

@Singleton
class DefaultSearchHistoryRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) : SearchHistoryRepository {

    override fun observeRecentSearches(): Flow<List<String>> =
        context.searchHistoryDataStore.data.map { preferences -> decode(preferences[RECENT_KEY]) }

    override suspend fun addRecentSearch(query: String) {
        context.searchHistoryDataStore.edit { preferences ->
            preferences[RECENT_KEY] = encode(RecentSearches.add(decode(preferences[RECENT_KEY]), query))
        }
    }

    override suspend fun removeRecentSearch(query: String) {
        context.searchHistoryDataStore.edit { preferences ->
            preferences[RECENT_KEY] = encode(decode(preferences[RECENT_KEY]).filterNot { it == query })
        }
    }

    override suspend fun clearRecentSearches() {
        context.searchHistoryDataStore.edit { preferences -> preferences.remove(RECENT_KEY) }
    }

    // Unit separator: can't be typed into the search field, so it never splits a query.
    private fun encode(queries: List<String>): String = queries.joinToString(SEPARATOR)

    private fun decode(value: String?): List<String> =
        value?.split(SEPARATOR)?.filter(String::isNotBlank).orEmpty()

    private companion object {
        val RECENT_KEY = stringPreferencesKey("recent_queries")
        const val SEPARATOR = "\u001F"
    }
}
