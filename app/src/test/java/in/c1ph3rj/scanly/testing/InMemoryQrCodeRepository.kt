package `in`.c1ph3rj.scanly.testing

import android.graphics.Bitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.ExportArtifact
import `in`.c1ph3rj.scanly.domain.repository.QrCodeRepository
import java.io.File

class InMemoryQrCodeRepository(
    private val outputDirectory: File,
) : QrCodeRepository {
    var lastGeneratedContent: String? = null
        private set
    var lastGeneratedWidth: Int = 0
        private set

    override suspend fun generateQrBitmap(
        content: String,
        sizePx: Int,
    ): ScanlyResult<Bitmap> = runLibraryResult("Could not generate QR code.") {
        val matrix = encode(content, sizePx)
        lastGeneratedContent = content.trim()
        lastGeneratedWidth = matrix.width
        runCatching {
            Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        }.getOrElse {
            error("QR matrix encoded (${matrix.width}x${matrix.height}) but Bitmap is unavailable.")
        }
    }

    override suspend fun saveQrPng(
        content: String,
        sizePx: Int,
    ): ScanlyResult<ExportArtifact> = runLibraryResult("Could not save QR code.") {
        val matrix = encode(content, sizePx)
        lastGeneratedContent = content.trim()
        lastGeneratedWidth = matrix.width
        outputDirectory.mkdirs()
        val file = File(outputDirectory, "qr_${content.trim().hashCode()}.png")
        val payload = buildString {
            appendLine("QR ${matrix.width}x${matrix.height}")
            appendLine(content.trim())
            for (y in 0 until matrix.height) {
                for (x in 0 until matrix.width) {
                    append(if (matrix[x, y]) '1' else '0')
                }
                appendLine()
            }
        }
        file.writeText(payload)
        check(file.length() > 0L) { "QR artifact is empty." }
        ExportArtifact(
            filePath = file.absolutePath,
            fileName = file.name,
            mimeType = "image/png",
        )
    }

    private fun encode(content: String, sizePx: Int) = run {
        require(content.isNotBlank()) { "Enter text or a URL to encode." }
        val size = sizePx.coerceIn(256, 2048)
        QRCodeWriter().encode(
            content.trim(),
            BarcodeFormat.QR_CODE,
            size,
            size,
            mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8",
            ),
        )
    }
}
