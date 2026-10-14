package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.search.DefaultSearchHistoryRepository
import `in`.c1ph3rj.scanly.data.text.DefaultPageTextRepository
import `in`.c1ph3rj.scanly.domain.repository.PageTextRepository
import `in`.c1ph3rj.scanly.domain.repository.SearchHistoryRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PageTextModule {
    @Binds
    @Singleton
    abstract fun bindPageTextRepository(
        repository: DefaultPageTextRepository,
    ): PageTextRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(
        repository: DefaultSearchHistoryRepository,
    ): SearchHistoryRepository
}
