package `in`.c1ph3rj.scanly.data.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.repository.QrCodeRepository
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultQrCodeRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dispatchers: ScanlyDispatchers,
) : QrCodeRepository {

    override suspend fun generateQrBitmap(
        content: String,
        sizePx: Int,
    ): ScanlyResult<Bitmap> = withContext(dispatchers.default) {
        runCatching {
            require(content.isNotBlank()) { "Enter text or a URL to encode." }
            encodeQr(content.trim(), sizePx.coerceIn(256, 2048))
        }.toScanlyResult("Could not generate QR code.")
    }

    override suspend fun saveQrPng(
        content: String,
        sizePx: Int,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            require(content.isNotBlank()) { "Enter text or a URL to encode." }
            val bitmap = encodeQr(content.trim(), sizePx.coerceIn(256, 2048))
            val dir = File(context.cacheDir, "qr-tools").apply { mkdirs() }
            val file = File(dir, "qr_${System.currentTimeMillis()}.png")
            FileOutputStream(file).use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                    error("Could not write QR image.")
                }
            }
            bitmap.recycle()
            ExportArtifact(
                filePath = file.absolutePath,
                fileName = file.name,
                mimeType = "image/png",
            )
        }.toScanlyResult("Could not save QR code.")
    }

    private fun encodeQr(content: String, sizePx: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val pixels = IntArray(sizePx * sizePx)
        for (y in 0 until sizePx) {
            val offset = y * sizePx
            for (x in 0 until sizePx) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        return Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, sizePx, 0, 0, sizePx, sizePx)
        }
    }

    private fun <T> Result<T>.toScanlyResult(fallbackMessage: String): ScanlyResult<T> =
        fold(
            onSuccess = { ScanlyResult.Success(it) },
            onFailure = {
                ScanlyResult.Failure(
                    ScanlyError(message = it.message ?: fallbackMessage, cause = it),
                )
            },
        )
}
