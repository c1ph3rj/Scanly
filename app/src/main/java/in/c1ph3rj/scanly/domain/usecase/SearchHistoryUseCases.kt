package `in`.c1ph3rj.scanly.domain.usecase

import `in`.c1ph3rj.scanly.domain.repository.SearchHistoryRepository
import javax.inject.Inject

class ObserveRecentSearchesUseCase @Inject constructor(private val repository: SearchHistoryRepository) {
    operator fun invoke() = repository.observeRecentSearches()
}

class AddRecentSearchUseCase @Inject constructor(private val repository: SearchHistoryRepository) {
    suspend operator fun invoke(query: String) = repository.addRecentSearch(query)
}

class RemoveRecentSearchUseCase @Inject constructor(private val repository: SearchHistoryRepository) {
    suspend operator fun invoke(query: String) = repository.removeRecentSearch(query)
}

class ClearRecentSearchesUseCase @Inject constructor(private val repository: SearchHistoryRepository) {
    suspend operator fun invoke() = repository.clearRecentSearches()
}
