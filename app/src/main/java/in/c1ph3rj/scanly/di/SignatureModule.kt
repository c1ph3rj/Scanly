package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.sign.DefaultPageMarkupRepository
import `in`.c1ph3rj.scanly.data.sign.DefaultSignatureRepository
import `in`.c1ph3rj.scanly.domain.repository.PageMarkupRepository
import `in`.c1ph3rj.scanly.domain.repository.SignatureRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SignatureModule {
    @Binds
    @Singleton
    abstract fun bindSignatureRepository(
        repository: DefaultSignatureRepository,
    ): SignatureRepository

    @Binds
    @Singleton
    abstract fun bindPageMarkupRepository(
        repository: DefaultPageMarkupRepository,
    ): PageMarkupRepository
}
