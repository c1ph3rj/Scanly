package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.document.DefaultDocumentRepository
import `in`.c1ph3rj.scanly.data.group.DefaultGroupRepository
import `in`.c1ph3rj.scanly.data.page.DefaultPageRepository
import `in`.c1ph3rj.scanly.domain.repository.DocumentRepository
import `in`.c1ph3rj.scanly.domain.repository.GroupRepository
import `in`.c1ph3rj.scanly.domain.repository.PageRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DocumentDataModule {
    @Binds
    @Singleton
    abstract fun bindDocumentRepository(
        repository: DefaultDocumentRepository,
    ): DocumentRepository

    @Binds
    @Singleton
    abstract fun bindPageRepository(
        repository: DefaultPageRepository,
    ): PageRepository

    @Binds
    @Singleton
    abstract fun bindGroupRepository(
        repository: DefaultGroupRepository,
    ): GroupRepository
}
