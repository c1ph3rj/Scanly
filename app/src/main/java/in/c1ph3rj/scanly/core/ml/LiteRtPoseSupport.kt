package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

internal data class PreparedImage(
    val inputBuffer: ByteBuffer,
    val scale: Float,
    val padX: Float,
    val padY: Float,
)

internal data class DecodedPosePrediction(
    val confidence: Float,
    val quad: DocumentCornerQuad?,
)

internal data class LiteRtModelContract(
    val inputWidth: Int,
    val inputHeight: Int,
    val predictionCount: Int,
    val modelName: String,
)

internal class LiteRtImagePreprocessor(
    private val inputWidth: Int,
    private val inputHeight: Int,
) {
    private val canvasBitmap = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
    private val canvas = Canvas(canvasBitmap)
    private val pixels = IntArray(inputWidth * inputHeight)
    private val inputBuffer = ByteBuffer.allocateDirect(
        inputWidth * inputHeight * LiteRtPoseConstants.INPUT_CHANNELS * LiteRtPoseConstants.FLOAT_BYTES,
    ).order(ByteOrder.nativeOrder())

    fun prepare(bitmap: Bitmap): PreparedImage {
        val scale = minOf(inputWidth / bitmap.width.toFloat(), inputHeight / bitmap.height.toFloat())
        val scaledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val scaledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val padX = (inputWidth - scaledWidth) / 2f
        val padY = (inputHeight - scaledHeight) / 2f
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
        canvas.drawColor(
            Color.rgb(
                LiteRtPoseConstants.LETTERBOX_COLOR,
                LiteRtPoseConstants.LETTERBOX_COLOR,
                LiteRtPoseConstants.LETTERBOX_COLOR,
            ),
        )
        canvas.drawBitmap(scaledBitmap, padX, padY, null)

        canvasBitmap.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)
        inputBuffer.clear()
        for (pixel in pixels) {
            inputBuffer.putFloat(Color.red(pixel) / 255f)
            inputBuffer.putFloat(Color.green(pixel) / 255f)
            inputBuffer.putFloat(Color.blue(pixel) / 255f)
        }
        inputBuffer.rewind()

        if (scaledBitmap !== bitmap) {
            scaledBitmap.recycle()
        }

        return PreparedImage(
            inputBuffer = inputBuffer,
            scale = scale,
            padX = padX,
            padY = padY,
        )
    }
}

internal fun decodeBestPrediction(
    outputBuffer: ByteBuffer,
    predictionCount: Int,
    preparedImage: PreparedImage,
    inputWidth: Int,
    inputHeight: Int,
    originalWidth: Int,
    originalHeight: Int,
    minConfidenceThreshold: Float = LiteRtPoseConstants.DETECTION_CONFIDENCE_THRESHOLD,
    values: FloatArray? = null,
): DecodedPosePrediction {
    outputBuffer.rewind()
    val floatBuffer = outputBuffer.asFloatBuffer()
    val decodedValues = values?.takeIf { it.size == floatBuffer.remaining() }
        ?: FloatArray(floatBuffer.remaining())
    floatBuffer.get(decodedValues)

    var bestIndex = 0
    var bestConfidence = Float.NEGATIVE_INFINITY
    for (anchorIndex in 0 until predictionCount) {
        val confidence = decodedValues[(LiteRtPoseConstants.CLASS_CHANNEL * predictionCount) + anchorIndex]
        if (confidence > bestConfidence) {
            bestConfidence = confidence
            bestIndex = anchorIndex
        }
    }

    if (bestConfidence < minConfidenceThreshold) {
        return DecodedPosePrediction(
            confidence = bestConfidence,
            quad = null,
        )
    }

    val quad = DocumentCornerQuad(
        topLeft = decodeKeypoint(decodedValues, bestIndex, LiteRtPoseConstants.KEYPOINT_START_CHANNEL + 0, predictionCount, preparedImage, inputWidth, inputHeight, originalWidth, originalHeight),
        topRight = decodeKeypoint(decodedValues, bestIndex, LiteRtPoseConstants.KEYPOINT_START_CHANNEL + 3, predictionCount, preparedImage, inputWidth, inputHeight, originalWidth, originalHeight),
        bottomRight = decodeKeypoint(decodedValues, bestIndex, LiteRtPoseConstants.KEYPOINT_START_CHANNEL + 6, predictionCount, preparedImage, inputWidth, inputHeight, originalWidth, originalHeight),
        bottomLeft = decodeKeypoint(decodedValues, bestIndex, LiteRtPoseConstants.KEYPOINT_START_CHANNEL + 9, predictionCount, preparedImage, inputWidth, inputHeight, originalWidth, originalHeight),
    )

    return DecodedPosePrediction(
        confidence = bestConfidence,
        quad = quad.takeIf(DocumentCornerQuad::isValid),
    )
}

private fun decodeKeypoint(
    values: FloatArray,
    anchorIndex: Int,
    startChannel: Int,
    predictionCount: Int,
    preparedImage: PreparedImage,
    inputWidth: Int,
    inputHeight: Int,
    originalWidth: Int,
    originalHeight: Int,
): NormalizedPoint {
    val normalizedX = values[(startChannel * predictionCount) + anchorIndex]
    val normalizedY = values[((startChannel + 1) * predictionCount) + anchorIndex]
    val letterboxedX = normalizedX * inputWidth
    val letterboxedY = normalizedY * inputHeight
    val unpaddedX = ((letterboxedX - preparedImage.padX) / preparedImage.scale)
        .coerceIn(0f, originalWidth.toFloat())
    val unpaddedY = ((letterboxedY - preparedImage.padY) / preparedImage.scale)
        .coerceIn(0f, originalHeight.toFloat())
    return NormalizedPoint(
        x = (unpaddedX / originalWidth).coerceIn(0f, 1f),
        y = (unpaddedY / originalHeight).coerceIn(0f, 1f),
    )
}

internal object LiteRtPoseConstants {
    const val DEFAULT_THREAD_COUNT = 4
    const val INPUT_CHANNELS = 3
    const val FLOAT_BYTES = 4
    const val LETTERBOX_COLOR = 114
    const val CLASS_CHANNEL = 4
    const val KEYPOINT_START_CHANNEL = 5
    const val DETECTION_CONFIDENCE_THRESHOLD = 0.25f
}
