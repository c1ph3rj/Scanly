package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.rating.DefaultRatingPromptRepository
import `in`.c1ph3rj.scanly.domain.repository.RatingPromptRepository
import `in`.c1ph3rj.scanly.domain.usecase.MarkRatingPromptShownUseCase
import `in`.c1ph3rj.scanly.domain.usecase.ObserveShouldShowRatingPromptUseCase
import `in`.c1ph3rj.scanly.domain.usecase.RecordFirstSeenUseCase
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RatingModule {
    @Binds
    @Singleton
    abstract fun bindRatingPromptRepository(
        repository: DefaultRatingPromptRepository,
    ): RatingPromptRepository

    companion object {
        @Provides
        fun provideObserveShouldShowRatingPromptUseCase(
            repository: RatingPromptRepository,
        ): ObserveShouldShowRatingPromptUseCase = ObserveShouldShowRatingPromptUseCase(repository)

        @Provides
        fun provideMarkRatingPromptShownUseCase(
            repository: RatingPromptRepository,
        ): MarkRatingPromptShownUseCase = MarkRatingPromptShownUseCase(repository)

        @Provides
        fun provideRecordFirstSeenUseCase(
            repository: RatingPromptRepository,
        ): RecordFirstSeenUseCase = RecordFirstSeenUseCase(repository)
    }
}
