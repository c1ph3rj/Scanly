package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.update.GitHubReleaseUpdateRepository
import `in`.c1ph3rj.scanly.domain.repository.AppUpdateRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        repository: GitHubReleaseUpdateRepository,
    ): AppUpdateRepository
}
