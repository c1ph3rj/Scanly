package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.export.DefaultDocumentExportRepository
import `in`.c1ph3rj.scanly.domain.repository.DocumentExportRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    @Binds
    @Singleton
    abstract fun bindDocumentExportRepository(
        repository: DefaultDocumentExportRepository,
    ): DocumentExportRepository
}
