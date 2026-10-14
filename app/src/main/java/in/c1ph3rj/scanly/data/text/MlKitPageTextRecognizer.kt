package `in`.c1ph3rj.scanly.data.text

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import `in`.c1ph3rj.scanly.domain.model.PageTextBlock
import `in`.c1ph3rj.scanly.domain.model.PageTextLine
import `in`.c1ph3rj.scanly.domain.model.TextBox
import `in`.c1ph3rj.scanly.domain.text.TextReflow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class RecognizedPage(
    val text: String,
    val blocks: List<PageTextBlock>,
    val imageWidth: Int,
    val imageHeight: Int,
)

/**
 * ML Kit Text Recognition v2 with the bundled Latin model: no download, no network, works
 * the first time the app runs. One recognizer is reused and calls are serialized, which keeps
 * peak memory to a single decoded page.
 */
@Singleton
class MlKitPageTextRecognizer @Inject constructor() {
    private val mutex = Mutex()
    private var recognizer: TextRecognizer? = null

    suspend fun recognize(imagePath: String): RecognizedPage = mutex.withLock {
        val bitmap = decodeForRecognition(imagePath)
            ?: throw IllegalStateException("Page image could not be read.")
        try {
            val rotation = exifRotation(imagePath)
            val result = client().process(InputImage.fromBitmap(bitmap, rotation)).await()
            val (width, height) = if (rotation % 180 == 0) {
                bitmap.width to bitmap.height
            } else {
                bitmap.height to bitmap.width
            }
            result.toRecognizedPage(width, height)
        } finally {
            bitmap.recycle()
        }
    }

    /** Frees the model's native memory once a batch is done; the next call recreates it. */
    suspend fun release() = mutex.withLock {
        recognizer?.close()
        recognizer = null
    }

    private fun client(): TextRecognizer =
        recognizer ?: TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS).also { recognizer = it }

    private fun Text.toRecognizedPage(width: Int, height: Int): RecognizedPage {
        val blocks = textBlocks.mapNotNull { block ->
            val lines = block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                PageTextLine(line.text, box.normalized(width, height))
            }
            if (lines.isEmpty()) return@mapNotNull null
            val box = block.boundingBox?.normalized(width, height) ?: lines.union()
            PageTextBlock(TextReflow.paragraph(lines.map { it.text }), box, lines)
        }
        return RecognizedPage(
            text = TextReflow.page(blocks.map { it.text }),
            blocks = blocks,
            imageWidth = width,
            imageHeight = height,
        )
    }

    private fun Rect.normalized(width: Int, height: Int): TextBox = TextBox(
        left = (left.toFloat() / width).coerceIn(0f, 1f),
        top = (top.toFloat() / height).coerceIn(0f, 1f),
        right = (right.toFloat() / width).coerceIn(0f, 1f),
        bottom = (bottom.toFloat() / height).coerceIn(0f, 1f),
    )

    private fun List<PageTextLine>.union(): TextBox = TextBox(
        left = minOf { it.box.left },
        top = minOf { it.box.top },
        right = maxOf { it.box.right },
        bottom = maxOf { it.box.bottom },
    )

    /**
     * Downsamples so the long edge is at most [MAX_RECOGNITION_EDGE_PX]. Document text stays
     * well above ML Kit's ~16 px character-height guidance, at a fraction of the memory.
     */
    private fun decodeForRecognition(path: String): Bitmap? {
        if (!File(path).exists()) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val longEdge = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longEdge / sampleSize > MAX_RECOGNITION_EDGE_PX) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private fun exifRotation(path: String): Int = runCatching {
        ExifInterface(path).rotationDegrees
    }.getOrDefault(0)

    private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { continuation.resume(it) }
        addOnFailureListener { continuation.resumeWithException(it) }
        addOnCanceledListener { continuation.cancel() }
    }

    private companion object {
        const val MAX_RECOGNITION_EDGE_PX = 2_560
    }
}
