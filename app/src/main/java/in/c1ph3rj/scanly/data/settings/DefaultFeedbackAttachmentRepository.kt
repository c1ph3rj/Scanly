package `in`.c1ph3rj.scanly.data.settings

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ImageDecoder
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachment
import `in`.c1ph3rj.scanly.domain.model.FeedbackAttachmentLimits
import `in`.c1ph3rj.scanly.domain.repository.FeedbackAttachmentRepository
import java.io.File
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.roundToInt

/** Prepared copies are independent of temporary gallery URI grants. Originals are never edited. */
class DefaultFeedbackAttachmentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : FeedbackAttachmentRepository {
    private val directory get() = File(context.cacheDir, "feedback_images").apply {
        if (!isDirectory && !mkdirs()) throw IOException("Cannot create feedback cache")
    }

    override suspend fun prepare(uri: String): ScanlyResult<FeedbackAttachment> = withContext(Dispatchers.IO) {
        var source: File? = null
        var prepared: File? = null
        var decoded: Bitmap? = null
        var flattened: Bitmap? = null
        try {
            val folder = directory
            // Keep files available to email drafts; only abandoned copies older than a week expire.
            val expired = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            folder.listFiles()?.filter { it.lastModified() < expired }?.forEach { it.delete() }
            source = File.createTempFile("source_", ".tmp", folder)
            context.contentResolver.openInputStream(Uri.parse(uri))?.use { input ->
                source.outputStream().use { output ->
                    val buffer = ByteArray(32 * 1024)
                    var total = 0L
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > FeedbackAttachmentLimits.MAX_SOURCE_BYTES) throw IOException("Source too large")
                        output.write(buffer, 0, count)
                    }
                }
            } ?: throw IOException("Image unavailable")
            currentCoroutineContext().ensureActive()
            decoded = ImageDecoder.decodeBitmap(ImageDecoder.createSource(source)) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val scale = (FeedbackAttachmentLimits.MAX_EDGE.toDouble() /
                    maxOf(info.size.width, info.size.height)).coerceAtMost(1.0)
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
            val id = UUID.randomUUID().toString()
            // Prefer lossless PNG for screenshots; bounded output aborts oversized encodes early.
            prepared = File(folder, "$id.png")
            if (encode(decoded, Bitmap.CompressFormat.PNG, 100, prepared)) {
                return@withContext ScanlyResult.Success(attachment(prepared)!!)
            }
            prepared.delete()
            prepared = File(folder, "$id.jpg")
            flattened = Bitmap.createBitmap(decoded.width, decoded.height, Bitmap.Config.ARGB_8888)
            Canvas(flattened).apply { drawColor(Color.WHITE); drawBitmap(decoded, 0f, 0f, null) }
            decoded.recycle()
            decoded = null
            // Quality first, then dimensions; never shrink below roughly 960 px just to accept a file.
            while (true) {
                val current = requireNotNull(flattened)
                for (quality in intArrayOf(88, 76, 64)) {
                    currentCoroutineContext().ensureActive()
                    if (encode(current, Bitmap.CompressFormat.JPEG, quality, prepared)) {
                        return@withContext ScanlyResult.Success(attachment(prepared)!!)
                    }
                }
                val edge = maxOf(current.width, current.height)
                if (edge <= 960) throw IOException("Cannot prepare a readable attachment within the limit")
                val scale = maxOf(960.0 / edge, 0.8)
                val smaller = Bitmap.createScaledBitmap(current,
                    (current.width * scale).roundToInt().coerceAtLeast(1),
                    (current.height * scale).roundToInt().coerceAtLeast(1), true)
                current.recycle()
                flattened = smaller
            }
            @Suppress("UNREACHABLE_CODE")
            error("Unreachable")
        } catch (cancel: CancellationException) {
            prepared?.delete()
            throw cancel
        } catch (error: Exception) {
            prepared?.delete()
            ScanlyResult.Failure(ScanlyError(appString(R.string.feedback_image_failed), error))
        } catch (error: OutOfMemoryError) {
            prepared?.delete()
            ScanlyResult.Failure(ScanlyError(appString(R.string.feedback_image_failed), error))
        } finally {
            source?.delete()
            decoded?.recycle()
            flattened?.recycle()
        }
    }

    override suspend fun restore(paths: List<String>): List<FeedbackAttachment> = withContext(Dispatchers.IO) {
        paths.distinct().mapNotNull { path -> runCatching { attachment(File(path)) }.getOrNull() }
    }

    override suspend fun validate(images: List<FeedbackAttachment>): Boolean = withContext(Dispatchers.IO) {
        FeedbackAttachmentLimits.canSend(images) && images.all {
            runCatching { attachment(File(it.path)) == it }.getOrDefault(false)
        }
    }

    private fun attachment(file: File): FeedbackAttachment? {
        if (file.canonicalFile.parentFile != directory.canonicalFile || !file.isFile ||
            file.length() !in 1..FeedbackAttachmentLimits.MAX_IMAGE_BYTES || file.extension !in setOf("jpg", "png")) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth !in 1..FeedbackAttachmentLimits.MAX_EDGE ||
            bounds.outHeight !in 1..FeedbackAttachmentLimits.MAX_EDGE) return null
        file.setLastModified(System.currentTimeMillis())
        return FeedbackAttachment(file.path, file.length(), if (file.extension == "png") "image/png" else "image/jpeg")
    }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int, file: File): Boolean = try {
        file.outputStream().buffered().use { destination ->
            val bounded = object : OutputStream() {
                var count = 0L
                override fun write(value: Int) { checkSize(1); destination.write(value) }
                override fun write(bytes: ByteArray, offset: Int, length: Int) {
                    checkSize(length); destination.write(bytes, offset, length)
                }
                fun checkSize(length: Int) {
                    count += length
                    if (count > FeedbackAttachmentLimits.MAX_IMAGE_BYTES) throw IOException("Encoded image too large")
                }
            }
            bitmap.compress(format, quality, bounded) && bounded.count in 1..FeedbackAttachmentLimits.MAX_IMAGE_BYTES
        }
    } catch (_: IOException) { false }
}
