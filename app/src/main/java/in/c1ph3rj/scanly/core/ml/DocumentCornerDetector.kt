package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap

interface DocumentCornerDetector {
    suspend fun detect(frame: DetectionFrame): CornerDetectionResult

    suspend fun detect(bitmap: Bitmap): CornerDetectionResult
}

data class DetectionFrame(
    val width: Int,
    val height: Int,
    val rotationDegrees: Int,
    val bytes: ByteArray,
    val cropLeft: Int = 0,
    val cropTop: Int = 0,
    val cropRight: Int = width,
    val cropBottom: Int = height,
)

data class CornerDetectionResult(
    val quad: DocumentCornerQuad?,
    val confidence: Float,
    val inferenceTimeMillis: Long,
    val modelName: String,
)
