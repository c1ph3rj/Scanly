package `in`.c1ph3rj.scanly.di

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerDetector
import `in`.c1ph3rj.scanly.core.ml.LiteRtDocumentCornerDetector
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class MlModule {
    @Binds
    @Singleton
    abstract fun bindDocumentCornerDetector(
        detector: LiteRtDocumentCornerDetector,
    ): DocumentCornerDetector
}
