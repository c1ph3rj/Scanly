package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.DocumentGateClass
import `in`.c1ph3rj.scanly.core.ml.DocumentGatePolicy
import `in`.c1ph3rj.scanly.core.ml.DocumentGateResult

/**
 * Debounces the live document gate with enter/exit hysteresis.
 *
 * - Entering needs [requiredConsecutiveFrames] consecutive frames accepted at
 *   [DocumentGatePolicy.LIVE_THRESHOLD].
 * - While accepted, frames whose top class is still a physical document with probability of at
 *   least [DocumentGatePolicy.LIVE_EXIT_THRESHOLD] keep it accepted.
 * - It leaves after [requiredConsecutiveRejects] consecutive frames below that, or at once on a
 *   single frame confidently classified as a screen or as no document.
 *
 * Not thread-safe; callers serialise access.
 */
class DocumentGateStabilityTracker(
    private val requiredConsecutiveFrames: Int = 2,
    private val requiredConsecutiveRejects: Int = 2,
) {
    private var accepted = false
    private var consecutiveAcceptedFrames = 0
    private var consecutiveRejectedFrames = 0

    fun evaluate(result: DocumentGateResult): Boolean {
        if (accepted) {
            when {
                result.isConfidentlyContrary() -> leaveAccepted()
                result.acceptsPhysicalDocument(DocumentGatePolicy.LIVE_EXIT_THRESHOLD) -> {
                    consecutiveRejectedFrames = 0
                }
                else -> {
                    consecutiveRejectedFrames += 1
                    if (consecutiveRejectedFrames >= requiredConsecutiveRejects) {
                        leaveAccepted()
                    }
                }
            }
            return accepted
        }

        if (result.acceptsPhysicalDocument(DocumentGatePolicy.LIVE_THRESHOLD)) {
            consecutiveAcceptedFrames += 1
        } else {
            consecutiveAcceptedFrames = 0
        }
        if (consecutiveAcceptedFrames >= requiredConsecutiveFrames) {
            accepted = true
            consecutiveAcceptedFrames = 0
            consecutiveRejectedFrames = 0
        }
        return accepted
    }

    fun reset() {
        leaveAccepted()
    }

    private fun leaveAccepted() {
        accepted = false
        consecutiveAcceptedFrames = 0
        consecutiveRejectedFrames = 0
    }

    private fun DocumentGateResult.isConfidentlyContrary(): Boolean =
        predictedClass != DocumentGateClass.PHYSICAL_DOCUMENT &&
            (probabilities[predictedClass] ?: 0f) >= DocumentGatePolicy.LIVE_THRESHOLD
}
