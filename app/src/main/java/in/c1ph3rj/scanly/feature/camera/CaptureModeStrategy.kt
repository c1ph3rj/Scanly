package `in`.c1ph3rj.scanly.feature.camera

import `in`.c1ph3rj.scanly.R
import `in`.c1ph3rj.scanly.core.common.appString
import `in`.c1ph3rj.scanly.core.ml.BookAnalysis
import `in`.c1ph3rj.scanly.core.ml.LiveQuadRules
import `in`.c1ph3rj.scanly.domain.model.ScanMode

/**
 * Everything a [ScanMode] changes about the live camera: which subjects count, how the gate and
 * the book-gutter analysis behave, how long a hold must be, and what follows a capture.
 * [forMode] for [ScanMode.DOCUMENT] is exactly the original flow.
 */
data class CaptureModeStrategy(
    val mode: ScanMode,
    val gate: LiveGatePolicy,
    val bookAnalysis: BookAnalysis,
    val quadRules: LiveQuadRules,
    val stability: StabilityTuning,
    /** Warn about (and hold auto-capture for) a light hot spot on the subject. */
    val detectGlare: Boolean,
) {
    fun newStabilityTracker(): CaptureStabilityTracker = CaptureStabilityTracker(
        minStableDurationMillis = stability.minStableDurationMillis,
        cooldownMillis = stability.cooldownMillis,
        rearmDistanceThreshold = stability.rearmDistanceThreshold,
        quadRules = quadRules,
        nextSubjectMessage = stability.nextSubjectMessage,
    )

    fun newCornerSelector(): StableCornerSelector = StableCornerSelector(quadRules = quadRules)

    fun newGateTracker(): DocumentGateStabilityTracker = DocumentGateStabilityTracker(policy = gate)

    companion object {
        fun forMode(mode: ScanMode): CaptureModeStrategy = when (mode) {
            ScanMode.DOCUMENT -> Document
            ScanMode.ID_CARD -> IdCard
            ScanMode.BOOK -> Book
        }

        private val Document = CaptureModeStrategy(
            mode = ScanMode.DOCUMENT,
            gate = LiveGatePolicy.Default,
            bookAnalysis = BookAnalysis.LIVE_ONLY,
            quadRules = LiveQuadRules.Default,
            stability = StabilityTuning(),
            detectGlare = false,
        )

        /**
         * A card fills less of the frame than a page, is held close, centred in the guide, and
         * is glossy. No gutter analysis: a card's photo edge reads as a book seam.
         */
        private val IdCard = CaptureModeStrategy(
            mode = ScanMode.ID_CARD,
            // Cards are trained as documents, but glossy plastic scores lower and a hand around
            // it can read as "neither" for a frame: enter a little lower, never drop on one frame.
            gate = LiveGatePolicy(
                entryThreshold = 0.80f,
                exitThreshold = 0.60f,
                instantExitOnNeither = false,
            ),
            bookAnalysis = BookAnalysis.OFF,
            quadRules = LiveQuadRules(
                minArea = 0.07f,
                minQuality = 0.52f,
                longShortRatio = 1.30f..1.90f,
                maxCentreOffset = 0.18f,
            ),
            // The cooldown message names the side to scan next; the view model supplies it.
            stability = StabilityTuning(minStableDurationMillis = 1_200L),
            detectGlare = true,
        )

        /**
         * One page per capture. The gutter trim also runs on the saved photo so the page stays
         * trimmed, and curved page edges are tolerated. Open books are not what the gate was
         * trained to accept (covers are "neither"), so only screens block.
         */
        private val Book = CaptureModeStrategy(
            mode = ScanMode.BOOK,
            gate = LiveGatePolicy(onlyScreensBlock = true),
            bookAnalysis = BookAnalysis.ALWAYS,
            quadRules = LiveQuadRules(minQuality = 0.50f),
            stability = StabilityTuning(
                minStableDurationMillis = 1_500L,
                cooldownMillis = 1_500L,
                nextSubjectMessage = { appString(R.string.scan_mode_book_turn_the_page) },
            ),
            detectGlare = false,
        )
    }
}

/** [CaptureStabilityTracker] timings a mode overrides; the defaults are the document flow's. */
data class StabilityTuning(
    val minStableDurationMillis: Long = 1_800L,
    val cooldownMillis: Long = 2_500L,
    val rearmDistanceThreshold: Float = 0.08f,
    val nextSubjectMessage: () -> String = {
        appString(R.string.capture_stability_tracker_move_to_the_next_page)
    },
)
