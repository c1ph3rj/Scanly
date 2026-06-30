package `in`.c1ph3rj.scanly.data.recognition

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import `in`.c1ph3rj.scanly.core.common.ScanlyDispatchers
import `in`.c1ph3rj.scanly.core.common.ScanlyError
import `in`.c1ph3rj.scanly.core.common.ScanlyResult
import `in`.c1ph3rj.scanly.domain.model.NormalizedTextPoint
import `in`.c1ph3rj.scanly.domain.model.RecognizedPageText
import `in`.c1ph3rj.scanly.domain.model.RecognizedTextToken
import `in`.c1ph3rj.scanly.domain.repository.PageTextRecognizer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class MlKitPageTextRecognizer @Inject constructor(
    private val dispatchers: ScanlyDispatchers,
) : PageTextRecognizer {
    private val recognizer: TextRecognizer by lazy {
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    }

    override suspend fun recognize(imagePath: String): ScanlyResult<RecognizedPageText> =
        try {
            require(File(imagePath).isFile) { "Page image is not available." }
            val recognizedText = withContext(dispatchers.default) {
                val bitmap = decodeForRecognition(imagePath)
                    ?: error("Could not decode the page image.")
                try {
                    val result = recognizer.processAwait(InputImage.fromBitmap(bitmap, 0))
                    result.toRecognizedPageText(
                        imageWidth = bitmap.width,
                        imageHeight = bitmap.height,
                    )
                } finally {
                    bitmap.recycle()
                }
            }
            ScanlyResult.Success(recognizedText)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (error: Throwable) {
            ScanlyResult.Failure(
                ScanlyError(
                    message = error.message ?: "Text recognition failed.",
                    cause = error,
                ),
            )
        }

    private fun decodeForRecognition(path: String): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        return BitmapFactory.decodeFile(
            path,
            BitmapFactory.Options().apply {
                inSampleSize = calculateSampleSize(
                    width = bounds.outWidth,
                    height = bounds.outHeight,
                    maxDimension = MAX_RECOGNITION_DIMENSION,
                )
                inPreferredConfig = Bitmap.Config.ARGB_8888
            },
        )
    }

    private suspend fun TextRecognizer.processAwait(inputImage: InputImage): Text =
        suspendCoroutine { continuation ->
            process(inputImage)
                .addOnSuccessListener(continuation::resume)
                .addOnFailureListener(continuation::resumeWithException)
                .addOnCanceledListener {
                    continuation.resumeWithException(CancellationException("Text recognition cancelled."))
                }
        }

    private fun Text.toRecognizedPageText(
        imageWidth: Int,
        imageHeight: Int,
    ): RecognizedPageText {
        var tokenIndex = 0
        var lineIndex = 0
        val tokens = buildList {
            textBlocks.forEachIndexed { blockIndex, block ->
                block.lines.forEach { line ->
                    line.elements.forEach { element ->
                        val points = element.cornerPoints
                            ?.takeIf { it.size >= MINIMUM_POLYGON_POINTS }
                            ?: element.boundingBox?.toCornerPoints()
                            ?: emptyArray()
                        val tokenText = element.text.trim()
                        if (tokenText.isNotEmpty() && points.size >= MINIMUM_POLYGON_POINTS) {
                            add(
                                RecognizedTextToken(
                                    index = tokenIndex++,
                                    text = tokenText,
                                    blockIndex = blockIndex,
                                    lineIndex = lineIndex,
                                    cornerPoints = points.map { point ->
                                        NormalizedTextPoint(
                                            x = (point.x / imageWidth.toFloat()).coerceIn(0f, 1f),
                                            y = (point.y / imageHeight.toFloat()).coerceIn(0f, 1f),
                                        )
                                    },
                                ),
                            )
                        }
                    }
                    lineIndex += 1
                }
            }
        }
        return RecognizedPageText(tokens)
    }

    private fun Rect.toCornerPoints(): Array<Point> = arrayOf(
        Point(left, top),
        Point(right, top),
        Point(right, bottom),
        Point(left, bottom),
    )

    private fun calculateSampleSize(
        width: Int,
        height: Int,
        maxDimension: Int,
    ): Int {
        var sampleSize = 1
        var currentWidth = width
        var currentHeight = height
        while (currentWidth > maxDimension || currentHeight > maxDimension) {
            currentWidth /= 2
            currentHeight /= 2
            sampleSize *= 2
        }
        return sampleSize.coerceAtLeast(1)
    }

    private companion object {
        const val MAX_RECOGNITION_DIMENSION = 2_400
        const val MINIMUM_POLYGON_POINTS = 3
    }
}
