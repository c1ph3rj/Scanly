package `in`.c1ph3rj.scanly.domain.repository

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact

interface QrCodeRepository {
    suspend fun generateQrBitmap(
        content: String,
        sizePx: Int = 768,
    ): ScanlyResult<Bitmap>

    suspend fun saveQrPng(
        content: String,
        sizePx: Int = 768,
    ): ScanlyResult<ExportArtifact>
}
