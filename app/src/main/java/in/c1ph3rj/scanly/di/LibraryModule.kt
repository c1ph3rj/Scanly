package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.library.DefaultLibraryAccessRepository
import `in`.c1ph3rj.scanly.domain.repository.LibraryAccessRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class LibraryModule {
    @Binds
    @Singleton
    abstract fun bindLibraryAccessRepository(repository: DefaultLibraryAccessRepository): LibraryAccessRepository
}

