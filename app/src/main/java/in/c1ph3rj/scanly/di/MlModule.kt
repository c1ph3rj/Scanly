package `in`.c1ph3rj.scanly.di

import `in`.c1ph3rj.scanly.core.ml.DocumentCornerDetector
import `in`.c1ph3rj.scanly.core.ml.DocumentGateDetector
import `in`.c1ph3rj.scanly.core.ml.IdCardFaceDetector
import `in`.c1ph3rj.scanly.core.ml.LiteRtDocumentCornerDetector
import `in`.c1ph3rj.scanly.core.ml.LiteRtDocumentGateDetector
import `in`.c1ph3rj.scanly.core.ml.MlKitIdCardFaceDetector
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

    @Binds
    @Singleton
    abstract fun bindDocumentGateDetector(
        detector: LiteRtDocumentGateDetector,
    ): DocumentGateDetector

    @Binds
    @Singleton
    abstract fun bindIdCardFaceDetector(
        detector: MlKitIdCardFaceDetector,
    ): IdCardFaceDetector
}
