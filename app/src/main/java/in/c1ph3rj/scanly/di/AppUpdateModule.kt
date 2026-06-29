package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.update.DefaultAppUpdatePromptRepository
import `in`.c1ph3rj.scanly.data.update.DefaultPlayInAppUpdateCoordinator
import `in`.c1ph3rj.scanly.data.update.GitHubReleaseUpdateRepository
import `in`.c1ph3rj.scanly.data.update.PlayStoreAppUpdateRepository
import `in`.c1ph3rj.scanly.domain.repository.AppReleaseNotesRepository
import `in`.c1ph3rj.scanly.domain.repository.AppUpdatePromptRepository
import `in`.c1ph3rj.scanly.domain.repository.AppUpdateRepository
import `in`.c1ph3rj.scanly.domain.repository.PlayInAppUpdateCoordinator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AppUpdateModule {
    @Binds
    @Singleton
    abstract fun bindAppUpdateRepository(
        repository: PlayStoreAppUpdateRepository,
    ): AppUpdateRepository

    @Binds
    @Singleton
    abstract fun bindAppReleaseNotesRepository(
        repository: GitHubReleaseUpdateRepository,
    ): AppReleaseNotesRepository

    @Binds
    @Singleton
    abstract fun bindPlayInAppUpdateCoordinator(
        coordinator: DefaultPlayInAppUpdateCoordinator,
    ): PlayInAppUpdateCoordinator

    @Binds
    @Singleton
    abstract fun bindAppUpdatePromptRepository(
        repository: DefaultAppUpdatePromptRepository,
    ): AppUpdatePromptRepository
}