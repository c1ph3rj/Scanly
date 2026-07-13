package `in`.c1ph3rj.scanly.core.ml

import `in`.c1ph3rj.scanly.domain.model.DocumentCornerModel

object CornerCandidatePolicy {
    private const val AMBIGUOUS_QUALITY_THRESHOLD = 0.90f
    /** Offline stills only re-check when geometry is truly weak. */
    private const val STILL_AMBIGUOUS_QUALITY_THRESHOLD = 0.38f
    private const val REPLACEMENT_SCORE_MARGIN = 0.015f

    fun needsAccurateVerification(
        result: CornerDetectionResult,
        readiness: QuadReadiness = QuadReadiness.LIVE_CAPTURE,
    ): Boolean {
        if (result.model == DocumentCornerModel.ACCURATE) return false
        val quad = result.quad ?: return true
        // Still-process path: if the quad is already usable for import/warp, skip the
        // expensive Accurate pass that used to null-out good ID/RC-card detections.
        if (readiness == QuadReadiness.STILL_PROCESS &&
            DocumentQuadPolicy.isReady(quad, readiness)
        ) {
            return DocumentQuadPolicy.qualityScore(quad) < STILL_AMBIGUOUS_QUALITY_THRESHOLD
        }
        return !DocumentQuadPolicy.isCaptureReady(quad) ||
            DocumentQuadPolicy.qualityScore(quad) < AMBIGUOUS_QUALITY_THRESHOLD
    }

    fun choosePreferred(
        primary: CornerDetectionResult,
        accurate: CornerDetectionResult,
    ): CornerDetectionResult {
        val primaryReady = primary.quad?.let(DocumentQuadPolicy::isCaptureReady) == true
        val accurateReady = accurate.quad?.let(DocumentQuadPolicy::isCaptureReady) == true
        if (!primaryReady) return accurate.takeIf { accurateReady } ?: primary.copy(quad = null)
        if (!accurateReady) return primary
        return if (
            DocumentQuadPolicy.selectionScore(accurate) >=
            DocumentQuadPolicy.selectionScore(primary) + REPLACEMENT_SCORE_MARGIN
        ) accurate else primary
    }
}

operator fun CornerDetectionTiming.plus(other: CornerDetectionTiming): CornerDetectionTiming = CornerDetectionTiming(
    preprocessingNanos = preprocessingNanos + other.preprocessingNanos,
    inferenceNanos = inferenceNanos + other.inferenceNanos,
    postprocessingNanos = postprocessingNanos + other.postprocessingNanos,
    totalNanos = totalNanos + other.totalNanos,
)
