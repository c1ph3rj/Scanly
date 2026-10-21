package `in`.c1ph3rj.scanly.data.qr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
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
        inkArgb: Long,
    ): ScanlyResult<Bitmap> = withContext(dispatchers.default) {
        runCatching {
            require(content.isNotBlank()) { "Enter something to encode." }
            encodeQr(content.trim(), sizePx.coerceIn(256, 2048), inkArgb.toInt())
        }.toScanlyResult("Could not generate QR code.")
    }

    override suspend fun saveQrPng(
        content: String,
        sizePx: Int,
        inkArgb: Long,
        caption: String?,
    ): ScanlyResult<ExportArtifact> = withContext(dispatchers.io) {
        runCatching {
            require(content.isNotBlank()) { "Enter something to encode." }
            val code = encodeQr(content.trim(), sizePx.coerceIn(256, 2048), inkArgb.toInt())
            val label = caption?.trim()?.takeIf { it.isNotEmpty() }
            val image = if (label == null) code else withCaption(code, label, inkArgb.toInt())
            val dir = File(context.cacheDir, "qr-tools").apply { mkdirs() }
            // Older exports were already shared or saved; sweep them so the folder doesn't grow forever.
            val staleBefore = System.currentTimeMillis() - STALE_EXPORT_MILLIS
            dir.listFiles().orEmpty()
                .filter { entry -> entry.lastModified() < staleBefore }
                .forEach { entry -> entry.delete() }
            // The file name is shown in Downloads and the snackbar, so it must never carry the
            // encoded content (Wi-Fi passwords, contact details, payment links).
            val slug = (label ?: contentTypeLabel(content.trim())).lowercase()
                .replace(Regex("^[a-z]+://"), "")
                .replace(Regex("[^a-z0-9]+"), "-")
                .trim('-')
                .take(32)
                .ifBlank { "code" }
            val file = File(dir, "qr-$slug-${System.currentTimeMillis()}.png")
            try {
                FileOutputStream(file).use { out ->
                    if (!image.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        error("Could not write QR image.")
                    }
                }
            } finally {
                if (image !== code) image.recycle()
                code.recycle()
            }
            ExportArtifact(
                filePath = file.absolutePath,
                fileName = file.name,
                mimeType = "image/png",
            )
        }.toScanlyResult("Could not save QR code.")
    }

    private fun encodeQr(content: String, sizePx: Int, ink: Int): Bitmap {
        val hints = mapOf(
            EncodeHintType.MARGIN to 1,
            EncodeHintType.CHARACTER_SET to "UTF-8",
            // M survives printing and creases without bloating the module count.
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
        )
        val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
        val width = matrix.width
        val height = matrix.height
        val pixels = IntArray(width * height)
        for (y in 0 until height) {
            val offset = y * width
            for (x in 0 until width) {
                pixels[offset + x] = if (matrix[x, y]) ink else Color.WHITE
            }
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(pixels, 0, width, 0, 0, width, height)
        }
    }

    /** Adds a white band under the code with [caption] centred in the ink colour. */
    private fun withCaption(code: Bitmap, caption: String, ink: Int): Bitmap {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textSize = code.width * 0.055f
        }
        val maxWidth = code.width * 0.9f
        while (paint.measureText(caption) > maxWidth && paint.textSize > code.width * 0.03f) {
            paint.textSize *= 0.92f
        }
        val text = if (paint.measureText(caption) > maxWidth) {
            val count = paint.breakText(caption, true, maxWidth - paint.measureText("\u2026"), null)
            caption.take(count).trimEnd() + "\u2026"
        } else {
            caption
        }
        val band = (paint.textSize * 2.2f).toInt()
        val output = Bitmap.createBitmap(code.width, code.height + band, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.WHITE)
            drawBitmap(code, 0f, 0f, null)
            val centerY = code.height + band * 0.42f
            val baseline = centerY - (paint.descent() + paint.ascent()) / 2f
            drawText(text, code.width / 2f, baseline, paint)
        }
        return output
    }

    /** Generic file-name label for the content type; never includes the content itself. */
    private fun contentTypeLabel(content: String): String = when {
        content.startsWith("WIFI:", ignoreCase = true) -> "wifi"
        content.startsWith("MECARD:", ignoreCase = true) ||
            content.startsWith("BEGIN:VCARD", ignoreCase = true) -> "contact"
        content.startsWith("upi://", ignoreCase = true) -> "upi"
        content.startsWith("http://", ignoreCase = true) ||
            content.startsWith("https://", ignoreCase = true) -> "link"
        else -> "code"
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

    private companion object {
        const val STALE_EXPORT_MILLIS = 60 * 60 * 1000L
    }
}
