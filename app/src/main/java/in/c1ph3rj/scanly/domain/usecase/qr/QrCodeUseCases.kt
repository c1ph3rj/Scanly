package `in`.c1ph3rj.scanly.domain.usecase.qr

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.repository.QrCodeRepository
import javax.inject.Inject

class GenerateQrBitmapUseCase @Inject constructor(
    private val repository: QrCodeRepository,
) {
    suspend operator fun invoke(content: String, sizePx: Int = 768): ScanlyResult<Bitmap> =
        repository.generateQrBitmap(content, sizePx)
}

class SaveQrPngUseCase @Inject constructor(
    private val repository: QrCodeRepository,
) {
    suspend operator fun invoke(content: String, sizePx: Int = 768): ScanlyResult<ExportArtifact> =
        repository.saveQrPng(content, sizePx)
}
