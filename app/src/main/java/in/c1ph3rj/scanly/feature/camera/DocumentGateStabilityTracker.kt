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
    private val policy: LiveGatePolicy = LiveGatePolicy.Default,
) {
    private var accepted = false
    private var consecutiveAcceptedFrames = 0
    private var consecutiveRejectedFrames = 0

    fun evaluate(result: DocumentGateResult): Boolean {
        if (accepted) {
            when {
                result.isConfidentlyContrary() -> leaveAccepted()
                result.accepts(policy.exitThreshold) -> {
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

        if (result.accepts(policy.entryThreshold)) {
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

    /** A physical document at [threshold], or, when only screens block, anything but a confident screen. */
    private fun DocumentGateResult.accepts(threshold: Float): Boolean =
        if (policy.onlyScreensBlock) {
            !isConfidentScreen()
        } else {
            acceptsPhysicalDocument(threshold)
        }

    private fun DocumentGateResult.isConfidentlyContrary(): Boolean = when (predictedClass) {
        DocumentGateClass.PHYSICAL_DOCUMENT -> false
        DocumentGateClass.DIGITAL_SCREEN -> isConfidentScreen()
        DocumentGateClass.NEITHER -> policy.instantExitOnNeither && !policy.onlyScreensBlock &&
            (probabilities[predictedClass] ?: 0f) >= DocumentGatePolicy.LIVE_THRESHOLD
    }

    private fun DocumentGateResult.isConfidentScreen(): Boolean =
        predictedClass == DocumentGateClass.DIGITAL_SCREEN &&
            (probabilities[predictedClass] ?: 0f) >= DocumentGatePolicy.LIVE_THRESHOLD
}

/**
 * How a scan mode reads the live document gate.
 *
 * @property onlyScreensBlock accept everything except a confident screen. For subjects the gate
 *   was not trained to accept with confidence, such as open books (covers are trained as "neither").
 * @property instantExitOnNeither leave at once on one confident "no document" frame; when false
 *   such frames only count towards [DocumentGateStabilityTracker]'s reject streak.
 */
data class LiveGatePolicy(
    val entryThreshold: Float = DocumentGatePolicy.LIVE_THRESHOLD,
    val exitThreshold: Float = DocumentGatePolicy.LIVE_EXIT_THRESHOLD,
    val onlyScreensBlock: Boolean = false,
    val instantExitOnNeither: Boolean = true,
) {
    companion object {
        val Default = LiveGatePolicy()
    }
}
