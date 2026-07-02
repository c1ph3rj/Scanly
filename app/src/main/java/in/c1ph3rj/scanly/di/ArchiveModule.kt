package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.archive.DefaultLibraryArchiveRepository
import `in`.c1ph3rj.scanly.domain.repository.LibraryArchiveRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ArchiveModule {
    @Binds
    @Singleton
    abstract fun bindLibraryArchiveRepository(
        repository: DefaultLibraryArchiveRepository,
    ): LibraryArchiveRepository
}
