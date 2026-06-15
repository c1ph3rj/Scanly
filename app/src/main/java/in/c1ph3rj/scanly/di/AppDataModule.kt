package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.appdata.DefaultAppDataRepository
import `in`.c1ph3rj.scanly.domain.repository.AppDataRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppDataModule {
    @Binds
    @Singleton
    abstract fun bindAppDataRepository(
        repository: DefaultAppDataRepository,
    ): AppDataRepository
}
