package `in`.c1ph3rj.scanly.di

import `in`.c1ph3rj.scanly.data.processing.DefaultPageImageProcessor
import `in`.c1ph3rj.scanly.domain.processing.PageImageProcessor
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProcessingModule {
    @Binds
    @Singleton
    abstract fun bindPageImageProcessor(
        processor: DefaultPageImageProcessor,
    ): PageImageProcessor
}
