package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.pip.DefaultPipRepository
import `in`.c1ph3rj.scanly.domain.repository.PipRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PipModule {
    @Binds
    @Singleton
    abstract fun bindPipRepository(
        repository: DefaultPipRepository,
    ): PipRepository
}
