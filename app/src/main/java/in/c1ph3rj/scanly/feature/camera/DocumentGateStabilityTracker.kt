package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.DocumentGatePolicy
import `in`.c1ph3rj.scanly.core.ml.DocumentGateResult

class DocumentGateStabilityTracker(
    private val requiredConsecutiveFrames: Int = 2,
) {
    private var consecutiveAcceptedFrames = 0

    fun evaluate(result: DocumentGateResult): Boolean {
        if (result.acceptsPhysicalDocument(DocumentGatePolicy.LIVE_THRESHOLD)) {
            consecutiveAcceptedFrames += 1
        } else {
            consecutiveAcceptedFrames = 0
        }
        return consecutiveAcceptedFrames >= requiredConsecutiveFrames
    }

    fun reset() {
        consecutiveAcceptedFrames = 0
    }
}
