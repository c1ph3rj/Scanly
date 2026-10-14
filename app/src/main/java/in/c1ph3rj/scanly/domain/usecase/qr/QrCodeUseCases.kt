package `in`.c1ph3rj.scanly.domain.usecase.qr

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.repository.DefaultQrInkArgb
import `in`.c1ph3rj.scanly.domain.repository.QrCodeRepository
import javax.inject.Inject

class GenerateQrBitmapUseCase @Inject constructor(
    private val repository: QrCodeRepository,
) {
    suspend operator fun invoke(
        content: String,
        sizePx: Int = 768,
        inkArgb: Long = DefaultQrInkArgb,
    ): ScanlyResult<Bitmap> = repository.generateQrBitmap(content, sizePx, inkArgb)
}

class SaveQrPngUseCase @Inject constructor(
    private val repository: QrCodeRepository,
) {
    suspend operator fun invoke(
        content: String,
        sizePx: Int = 1024,
        inkArgb: Long = DefaultQrInkArgb,
        caption: String? = null,
    ): ScanlyResult<ExportArtifact> = repository.saveQrPng(content, sizePx, inkArgb, caption)
}
