package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.update.GitHubAppUpdateRepository
import `in`.c1ph3rj.scanly.data.update.NoOpPlayInAppUpdateCoordinator
import `in`.c1ph3rj.scanly.domain.repository.AppUpdateRepository
import `in`.c1ph3rj.scanly.domain.repository.PlayInAppUpdateCoordinator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DistributionAppUpdateModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        repository: GitHubAppUpdateRepository,
    ): AppUpdateRepository

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateCoordinator(
        coordinator: NoOpPlayInAppUpdateCoordinator,
    ): PlayInAppUpdateCoordinator
}
