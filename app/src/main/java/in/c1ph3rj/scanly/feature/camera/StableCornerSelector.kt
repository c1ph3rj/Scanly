package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.core.ml.CornerDetectionResult
import `in`.c1ph3rj.scanly.core.ml.DocumentCornerQuad
import `in`.c1ph3rj.scanly.core.ml.DocumentQuadPolicy
import `in`.c1ph3rj.scanly.core.ml.NormalizedPoint

/**
 * Result of one [StableCornerSelector.selectTracked] call.
 *
 * @property isFresh true when [result]'s quad was confirmed by this frame's detection; false when it
 *   is the previously tracked quad held through a short detection gap (or there is no quad). A held
 *   quad may be drawn but must never trigger auto-capture on its own.
 */
data class TrackedCornerSelection(
    val result: CornerDetectionResult,
    val isFresh: Boolean,
)

class StableCornerSelector(
    /**
     * Consecutive invalid/missing frames after which the tracked quad is dropped. Below it the last
     * quad keeps being returned so one missed detection does not blink the outline (~375 ms at 8 fps).
     */
    private val missingFramesBeforeClear: Int = 3,
    private val initialConfirmationFrames: Int = 2,
    private val normalSwitchFrames: Int = 3,
    private val superiorSwitchFrames: Int = 2,
    private val trackingDistance: Float = 0.075f,
    private val pendingDistance: Float = 0.060f,
    private val superiorScoreMargin: Float = 0.025f,
    private val smoothingFactor: Float = 0.34f,
) {
    private var current: CornerDetectionResult? = null
    private var pending: CornerDetectionResult? = null
    private var pendingFrames: Int = 0
    private var missingFrames: Int = 0

    fun select(candidate: CornerDetectionResult): CornerDetectionResult = selectTracked(candidate).result

    fun selectTracked(candidate: CornerDetectionResult): TrackedCornerSelection {
        val validCandidate = candidate.takeIf { result ->
            result.quad?.let { quad -> DocumentQuadPolicy.isCaptureReady(quad, result.frameAspectRatio) } == true
        }
        if (validCandidate == null) {
            missingFrames += 1
            resetPending()
            if (missingFrames >= missingFramesBeforeClear) current = null
            // Grace: keep showing the tracked quad (stale, so never fresh) for a short gap.
            val held = current ?: return TrackedCornerSelection(candidate.copy(quad = null), isFresh = false)
            return TrackedCornerSelection(
                held.copy(
                    inferenceTimeMillis = candidate.inferenceTimeMillis,
                    timing = candidate.timing,
                ),
                isFresh = false,
            )
        }
        missingFrames = 0

        val active = current
        if (active == null) {
            updatePending(validCandidate)
            if (pendingFrames >= initialConfirmationFrames) promotePending()
            val confirmed = current?.quad
            return TrackedCornerSelection(validCandidate.copy(quad = confirmed), isFresh = confirmed != null)
        }

        val activeQuad = checkNotNull(active.quad)
        val candidateQuad = checkNotNull(validCandidate.quad)
        if (candidateQuad.maxCornerDistance(activeQuad) <= trackingDistance) {
            current = validCandidate.copy(quad = activeQuad.interpolate(candidateQuad, smoothingFactor))
            resetPending()
            return TrackedCornerSelection(checkNotNull(current), isFresh = true)
        }

        updatePending(validCandidate)
        val pendingResult = checkNotNull(pending)
        val isSuperior = DocumentQuadPolicy.selectionScore(pendingResult) >=
            DocumentQuadPolicy.selectionScore(active) + superiorScoreMargin
        val requiredFrames = if (isSuperior) superiorSwitchFrames else normalSwitchFrames
        if (pendingFrames >= requiredFrames) {
            promotePending()
            return TrackedCornerSelection(checkNotNull(current), isFresh = true)
        }

        // Never feed an unconfirmed location into the overlay or auto-capture.
        // The current candidate remains cached and can resume on the next close frame.
        return TrackedCornerSelection(validCandidate.copy(quad = null), isFresh = false)
    }

    fun reset() {
        current = null
        missingFrames = 0
        resetPending()
    }

    private fun updatePending(candidate: CornerDetectionResult) {
        val previous = pending
        val sameCluster = previous?.quad?.maxCornerDistance(checkNotNull(candidate.quad))
            ?.let { it <= pendingDistance } == true
        if (sameCluster) {
            pendingFrames += 1
            if (DocumentQuadPolicy.selectionScore(candidate) > DocumentQuadPolicy.selectionScore(checkNotNull(previous))) {
                pending = candidate
            }
        } else {
            pending = candidate
            pendingFrames = 1
        }
    }

    private fun promotePending() {
        current = pending
        resetPending()
    }

    private fun resetPending() {
        pending = null
        pendingFrames = 0
    }

    private fun DocumentCornerQuad.interpolate(other: DocumentCornerQuad, fraction: Float): DocumentCornerQuad =
        DocumentCornerQuad(
            topLeft = topLeft.interpolate(other.topLeft, fraction),
            topRight = topRight.interpolate(other.topRight, fraction),
            bottomRight = bottomRight.interpolate(other.bottomRight, fraction),
            bottomLeft = bottomLeft.interpolate(other.bottomLeft, fraction),
        )

    private fun NormalizedPoint.interpolate(other: NormalizedPoint, fraction: Float): NormalizedPoint =
        NormalizedPoint(
            x = x + ((other.x - x) * fraction),
            y = y + ((other.y - y) * fraction),
        )
}
