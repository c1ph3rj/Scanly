package `in`.c1ph3rj.scanly.di

import `in`.c1ph3rj.scanly.core.common.DefaultScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class CoroutineModule {
    @Binds
    @Singleton
    abstract fun bindScanlyDispatchers(
        dispatchers: DefaultScanlyDispatchers,
    ): ScanlyDispatchers
}
