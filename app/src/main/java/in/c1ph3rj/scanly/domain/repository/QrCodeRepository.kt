package `in`.c1ph3rj.scanly.domain.repository

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact

/** Near-black ink used when no colour is chosen (design token "Ink" #101418). */
const val DefaultQrInkArgb: Long = 0xFF101418

interface QrCodeRepository {
    /** Square code on white with a one-module quiet zone, drawn in [inkArgb]. */
    suspend fun generateQrBitmap(
        content: String,
        sizePx: Int = 768,
        inkArgb: Long = DefaultQrInkArgb,
    ): ScanlyResult<Bitmap>

    /**
     * Writes a PNG of the code. When [caption] is set it is printed under the code, so the
     * saved image explains itself when printed or forwarded.
     */
    suspend fun saveQrPng(
        content: String,
        sizePx: Int = 1024,
        inkArgb: Long = DefaultQrInkArgb,
        caption: String? = null,
    ): ScanlyResult<ExportArtifact>
}
