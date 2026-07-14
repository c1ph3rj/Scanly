package `in`.c1ph3rj.scanly.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import `in`.c1ph3rj.scanly.data.pdftools.DefaultPdfToolkitRepository
import `in`.c1ph3rj.scanly.data.qr.DefaultQrCodeRepository
import `in`.c1ph3rj.scanly.domain.repository.PdfToolkitRepository
import `in`.c1ph3rj.scanly.domain.repository.QrCodeRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class PdfToolkitModule {
    @Binds
    @Singleton
    abstract fun bindPdfToolkitRepository(
        repository: DefaultPdfToolkitRepository,
    ): PdfToolkitRepository

    @Binds
    @Singleton
    abstract fun bindQrCodeRepository(
        repository: DefaultQrCodeRepository,
    ): QrCodeRepository
}
