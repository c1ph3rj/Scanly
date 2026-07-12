package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap
import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel

interface DocumentCornerDetector {
    suspend fun detect(
        frame: DetectionFrame,
        model: DocumentCornerModel = DocumentCornerModel.LEGACY,
    ): CornerDetectionResult

    suspend fun detect(
        bitmap: Bitmap,
        model: DocumentCornerModel = DocumentCornerModel.LEGACY,
    ): CornerDetectionResult
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
    val model: DocumentCornerModel = DocumentCornerModel.LEGACY,
    val timing: CornerDetectionTiming = CornerDetectionTiming(
        inferenceNanos = inferenceTimeMillis * 1_000_000,
        totalNanos = inferenceTimeMillis * 1_000_000,
    ),
)

data class CornerDetectionTiming(
    val preprocessingNanos: Long = 0,
    val inferenceNanos: Long = 0,
    val postprocessingNanos: Long = 0,
    val totalNanos: Long = preprocessingNanos + inferenceNanos + postprocessingNanos,
) {
    val preprocessingMillis: Double get() = preprocessingNanos / 1_000_000.0
    val inferenceMillis: Double get() = inferenceNanos / 1_000_000.0
    val postprocessingMillis: Double get() = postprocessingNanos / 1_000_000.0
    val totalMillis: Double get() = totalNanos / 1_000_000.0
}
