package `in`.c1ph3rj.scanly.di

import `in`.c1ph3rj.scanly.data.diagnostics.DefaultSprintZeroDiagnosticsRepository
import `in`.c1ph3rj.scanly.domain.repository.SprintZeroDiagnosticsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiagnosticsModule {
    @Binds
    @Singleton
    abstract fun bindSprintZeroDiagnosticsRepository(
        repository: DefaultSprintZeroDiagnosticsRepository,
    ): SprintZeroDiagnosticsRepository
}
