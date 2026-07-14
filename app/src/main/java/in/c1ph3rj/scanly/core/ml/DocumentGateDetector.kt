package `in`.c1ph3rj.scanly.core.ml

import android.graphics.Bitmap

enum class DocumentGateClass(val displayName: String) {
    PHYSICAL_DOCUMENT("Physical document"),
    DIGITAL_SCREEN("Digital screen"),
    NEITHER("Neither"),
}

data class DocumentGateResult(
    val probabilities: Map<DocumentGateClass, Float>,
    val timing: DocumentGateTiming,
) {
    val predictedClass: DocumentGateClass = probabilities.maxByOrNull { it.value }?.key
        ?: DocumentGateClass.NEITHER
    val physicalDocumentProbability: Float = probabilities[DocumentGateClass.PHYSICAL_DOCUMENT] ?: 0f

    fun acceptsPhysicalDocument(threshold: Float): Boolean =
        predictedClass == DocumentGateClass.PHYSICAL_DOCUMENT && physicalDocumentProbability >= threshold
}

data class DocumentGateTiming(
    val preprocessingNanos: Long,
    val inferenceNanos: Long,
    val postprocessingNanos: Long,
    val totalNanos: Long,
) {
    val preprocessingMillis: Double get() = preprocessingNanos / 1_000_000.0
    val inferenceMillis: Double get() = inferenceNanos / 1_000_000.0
    val postprocessingMillis: Double get() = postprocessingNanos / 1_000_000.0
    val totalMillis: Double get() = totalNanos / 1_000_000.0
}

interface DocumentGateDetector {
    suspend fun classify(bitmap: Bitmap): DocumentGateResult
}

object DocumentGatePolicy {
    const val LIVE_THRESHOLD = 0.90f
    const val POST_PROCESSING_THRESHOLD = 0.95f
}
