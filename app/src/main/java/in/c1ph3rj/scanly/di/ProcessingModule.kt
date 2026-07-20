package `in`.c1ph3rj.scanly.di

import `in`.c1ph3rj.scanly.data.processing.DefaultPageImageProcessor
import `in`.c1ph3rj.scanly.domain.processing.LiveDocumentAnalysisSession
import `in`.c1ph3rj.scanly.domain.processing.PageImageProcessor
import `in`.c1ph3rj.scanly.feature.camera.DefaultLiveDocumentAnalysisSession
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

    @Binds
    @Singleton
    abstract fun bindLiveDocumentAnalysisSession(
        session: DefaultLiveDocumentAnalysisSession,
    ): LiveDocumentAnalysisSession
}
