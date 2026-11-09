package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.search.DefaultSearchHistoryRepository
import `in`.c1ph3rj.scanly.data.text.DefaultOcrEngineStatusRepository
import `in`.c1ph3rj.scanly.data.text.DefaultPageTextRepository
import `in`.c1ph3rj.scanly.data.text.IndraCrashGuard
import `in`.c1ph3rj.scanly.data.text.MlKitPageTextRecognizer
import `in`.c1ph3rj.scanly.data.text.OcrEngineSource
import `in`.c1ph3rj.scanly.data.text.PageTextRecognizer
import `in`.c1ph3rj.scanly.data.text.SelectablePageTextRecognizer
import `in`.c1ph3rj.scanly.data.text.SettingsOcrEngineSource
import `in`.c1ph3rj.scanly.data.text.SharedPreferencesIndraCrashGuard
import `in`.c1ph3rj.scanly.data.text.indra.IndraPageTextRecognizer
import `in`.c1ph3rj.scanly.domain.repository.OcrEngineStatusRepository
import `in`.c1ph3rj.scanly.domain.repository.PageTextRepository
import `in`.c1ph3rj.scanly.domain.repository.SearchHistoryRepository
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PageTextModule {
    @Binds
    @Singleton
    abstract fun bindPageTextRepository(
        repository: DefaultPageTextRepository,
    ): PageTextRepository

    @Binds
    @Singleton
    abstract fun bindPageTextRecognizer(
        recognizer: SelectablePageTextRecognizer,
    ): PageTextRecognizer

    @Binds
    @Named(SelectablePageTextRecognizer.ML_KIT_QUALIFIER)
    abstract fun bindMlKitRecognizer(
        recognizer: MlKitPageTextRecognizer,
    ): PageTextRecognizer

    @Binds
    @Named(SelectablePageTextRecognizer.INDRA_QUALIFIER)
    abstract fun bindIndraRecognizer(
        recognizer: IndraPageTextRecognizer,
    ): PageTextRecognizer

    @Binds
    abstract fun bindOcrEngineSource(
        source: SettingsOcrEngineSource,
    ): OcrEngineSource

    @Binds
    abstract fun bindIndraCrashGuard(
        guard: SharedPreferencesIndraCrashGuard,
    ): IndraCrashGuard

    @Binds
    @Singleton
    abstract fun bindOcrEngineStatusRepository(
        repository: DefaultOcrEngineStatusRepository,
    ): OcrEngineStatusRepository

    @Binds
    @Singleton
    abstract fun bindSearchHistoryRepository(
        repository: DefaultSearchHistoryRepository,
    ): SearchHistoryRepository
}
